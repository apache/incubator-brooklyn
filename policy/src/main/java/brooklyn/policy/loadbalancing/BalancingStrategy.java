/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package brooklyn.policy.loadbalancing;

import java.text.MessageFormat;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.location.Location;

/**
 * Represents an abstract algorithm for optimally balancing worker "items" among several "containers" based on the workloads
 * of the items, and corresponding high- and low-thresholds on the containers.
 * 
 * TODO: extract interface, provide default implementation
 * TODO: remove legacy code comments
 */
public class BalancingStrategy<NodeType extends Entity, ItemType extends Movable> {

    // FIXME Bad idea to use MessageFormat.format in this way; if toString of entity contains
    // special characters interpreted by MessageFormat, then it will all break!
    
    // This is a modified version of the watermark elasticity policy from Monterey v3.
    
    private static final Logger LOG = LoggerFactory.getLogger(BalancingStrategy.class);
    
    private static final int MAX_MIGRATIONS_PER_BALANCING_NODE = 20; // arbitrary (Splodge)
    private static final boolean BALANCE_COLD_PULLS_IN_SAME_RUN_AS_HOT_PUSHES = false;
    
    private final String name;
    private final BalanceablePoolModel<NodeType, ItemType> model;
    private final PolicyUtilForPool<NodeType, ItemType> helper;
//    private boolean loggedColdestTooHigh = false;
//    private boolean loggedHottestTooLow = false;
    
    
    public BalancingStrategy(String name, BalanceablePoolModel<NodeType, ItemType> model) {
        this.name = name;
        this.model = model;
        this.helper = new PolicyUtilForPool<NodeType, ItemType>(model);
    }
    
    public String getName() {
        return name;
    }
    
    public void rebalance() {
        checkAndApplyOn(model.getPoolContents());
    }
    
    public int getMaxMigrationsPerBalancingNode() {
        return MAX_MIGRATIONS_PER_BALANCING_NODE;
    }
    
    public BalanceablePoolModel<NodeType, ItemType> getDataProvider() {
        return model;
    }
    
    // This was the entry point for the legacy policy.
    private void checkAndApplyOn(final Collection<NodeType> dirtyNodesSupplied) {
        Collection<NodeType> dirtyNodes = dirtyNodesSupplied;
        
//        if (startTime + FORCE_ALL_NODES_IF_DELAYED_FOR_MILLIS < System.currentTimeMillis()) {
//            Set<NodeType> allNodes = new LinkedHashSet<NodeType>();
//            allNodes.addAll(dirtyNodes);
//            dirtyNodes = allNodes;
//            allNodes.addAll(getDataProvider().getPoolContents());
//            if (LOG.isDebugEnabled())
//                LOG.debug("policy "+getDataProvider().getAbbr()+" running after delay ("+
//                        TimeUtils.makeTimeString(System.currentTimeMillis() - startTime)+", analysing all nodes: "+
//                        dirtyNodes);
//        }
        
//        nodeFinder.optionalCachedNodesWithBacklogDetected.clear();
//        boolean gonnaGrow = growPool(dirtyNodes);
//        getDataProvider().waitForAllTransitionsComplete();
        boolean gonnaGrow = false;
        
        Set<NodeType> nonFrozenDirtyNodes = new LinkedHashSet<NodeType>(dirtyNodes);
//        boolean gonnaShrink = false;
//        if (!gonnaGrow && !DONT_SHRINK_UNLESS_BALANCED) {
//            gonnaShrink = shrinkPool(nonFrozenDirtyNodes);
//            getDataProvider().waitForAllTransitionsComplete();
//        }
        
        if (getDataProvider().getPoolSize() >= 2) {
            boolean didBalancing = false;
            for (NodeType a : nonFrozenDirtyNodes) {
                didBalancing |= balanceItemsOnNodesInQuestion(a, gonnaGrow);
//                getMutator().waitForAllTransitionsComplete();
            }
            if (didBalancing) {
                return;
            }
        }
        
//        if (!gonnaGrow && DONT_SHRINK_UNLESS_BALANCED) {
//            gonnaShrink = shrinkPool(nonFrozenDirtyNodes);
//            getDataProvider().waitForAllTransitionsComplete();
//        }
        
//        if (gonnaGrow || gonnaShrink)
//        //don't log 'doing nothing' message
//        return;
        
//        if (LOG.isDebugEnabled()) {
//            double poolTotal = getDataProvider().getPoolPredictedWorkrateTotal();
//            int poolSize = getDataProvider().getPoolPredictedSize();
//            LOG.debug(MessageFormat.format("policy "+getDataProvider().getAbbr()+" did nothing; pool workrate is {0,number,#.##} x {1} nodes",
//                    1.0*poolTotal/poolSize, poolSize));
//        }
    }
    
    protected boolean balanceItemsOnNodesInQuestion(NodeType questionedNode, boolean gonnaGrow) {
        double questionedNodeTotalWorkrate = getDataProvider().getTotalWorkrate(questionedNode);
        
        boolean balanced = balanceItemsOnHotNode(questionedNode, questionedNodeTotalWorkrate, gonnaGrow);
//        getMutator().waitForAllTransitionsComplete();
        
        if (!balanced || BALANCE_COLD_PULLS_IN_SAME_RUN_AS_HOT_PUSHES) {
            balanced |= balanceItemsOnColdNode(questionedNode, questionedNodeTotalWorkrate, gonnaGrow);
//            getMutator().waitForAllTransitionsComplete();
        }
        if (balanced)
            return true;
        
        if (LOG.isDebugEnabled()) {
            LOG.debug( MessageFormat.format(
                    "policy "+getDataProvider().getName()+" not balancing "+questionedNode+"; " +
                    "its workrate {0,number,#.##} is acceptable (or cannot be balanced)", questionedNodeTotalWorkrate) );
        }
        return false;
    }
    
    protected boolean balanceItemsOnHotNode(NodeType node, double nodeWorkrate, boolean gonnaGrow) {
        double originalNodeWorkrate = nodeWorkrate;
        int migrationCount = 0;
        int iterationCount = 0;
        
        Set<ItemType> itemsMoved = new LinkedHashSet<ItemType>();
        Set<NodeType> nodesChecked = new LinkedHashSet<NodeType>();
        
//        if (nodeFinder.config.COUNT_BACKLOG_AS_EXTRA_WORKRATE) {
//            int backlog = nodeFinder.getBacklogQueueLength(questionedNode);
//            if (backlog>0) {
//                Level l = backlog>1000 ? Level.INFO : backlog>10 ? Level.FINE : Level.FINER;
//                if (LOG.isLoggable(l)) {
//                    LOG.log( l, MessageFormat.format(
//                            "policy "+getDataProvider().getAbbr()+" detected queue at node "+questionedNode+", " +
//                            "inflating workrate {0,number,#.##} + "+backlog, questionedNodeTotalWorkrate) );
//                }
//                questionedNodeTotalWorkrate += backlog;
//            }
//        }
        
        Double highThreshold = model.getHighThreshold(node);
        if (highThreshold == -1) {
            // node presumably has been removed; TODO log
            return false;
        }
        
        while (nodeWorkrate > highThreshold && migrationCount < getMaxMigrationsPerBalancingNode()) {
            iterationCount++;
            
            if (LOG.isDebugEnabled()) {
                LOG.debug(MessageFormat.format(
                        "policy "+getDataProvider().getName()+" considering balancing hot node "+node+" " +
                        "(workrate {0,number,#.##}); iteration "+iterationCount, nodeWorkrate) );
            }
            
            // move from hot node, to coldest
            
            NodeType coldNode = helper.findColdestContainer(nodesChecked);
            
            if (coldNode == null) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug( MessageFormat.format(
                            "policy "+getDataProvider().getName()+" not balancing hot node "+node+" " +
                            "(workrate {0,number,#.##}); no coldest node available", nodeWorkrate) );
                }
                break;
            }
            
            if (coldNode.equals(node)) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug( MessageFormat.format(
                            "policy "+getDataProvider().getName()+" not balancing hot node "+node+" " +
                            "(workrate {0,number,#.##}); it is also the coldest modifiable node", nodeWorkrate) );
                }
                break;
            }
            
            double coldNodeWorkrate = getDataProvider().getTotalWorkrate(coldNode);
            boolean emergencyLoadBalancing = coldNodeWorkrate < nodeWorkrate*2/3;
            double coldNodeHighThreshold = model.getHighThreshold(coldNode);
            if (coldNodeWorkrate >= coldNodeHighThreshold && !emergencyLoadBalancing) {
                //don't balance if all nodes are approx equally hot (and very hot)
                
                //for now, stop warning if it is a recurring theme!
//                Level level = loggedColdestTooHigh ? Level.FINER : Level.INFO;
//                LOG.log(level, MessageFormat.format(
//                        "policy "+getDataProvider().getAbbr()+" not balancing hot node "+questionedNode+" " +
//                        "(workrate {0,number,#.##}); coldest node "+coldNode+" has workrate {1,number,#.##} also too high"+
//                        (loggedColdestTooHigh ? "" : " (future cases will be logged at finer)"),
//                        questionedNodeTotalWorkrate, coldNodeWorkrate) );
//                loggedColdestTooHigh = true;
                break;
            }
            double poolLowWatermark = Double.MAX_VALUE; // TODO
            if (gonnaGrow && (coldNodeWorkrate >= poolLowWatermark && !emergencyLoadBalancing)) {
                //if we're growing the pool, refuse to balance unless the cold node is indeed very cold, or hot node very hot
                
                //for now, stop warning if it is a recurring theme!
//                Level level = loggedColdestTooHigh ? Level.FINER : Level.INFO;
//                LOG.log(level, MessageFormat.format(
//                        "policy "+getDataProvider().getAbbr()+" not balancing hot node "+questionedNode+" " +
//                        "(workrate {0,number,#.##}); coldest node "+coldNode+" has workrate {1,number,#.##} also too high to accept while pool is growing" +
//                        (loggedColdestTooHigh ? "" : " (future cases will be logged at finer)"),
//                        questionedNodeTotalWorkrate, coldNodeWorkrate) );
//                loggedColdestTooHigh = true;
                break;
            }
            
            String questionedNodeName = getDataProvider().getName(node);
            String coldNodeName = getDataProvider().getName(coldNode);
            Location coldNodeLocation = getDataProvider().getLocation(coldNode);
            
            if (LOG.isDebugEnabled()) {
                LOG.debug( MessageFormat.format(
                        "policy "+getDataProvider().getName()+" balancing hot node "+questionedNodeName+" " +
                        "("+node+", workrate {0,number,#.##}), " +
                        "considering target "+coldNodeName+" ("+coldNode+", workrate {1,number,#.##})",
                        nodeWorkrate, coldNodeWorkrate) );
            }
            
            double idealSizeToMove = (nodeWorkrate - coldNodeWorkrate) / 2;
            //if the 'ideal' amount to move would cause cold to be too hot, then reduce ideal amount
            
            if (idealSizeToMove + coldNodeWorkrate > coldNodeHighThreshold)
                idealSizeToMove = coldNodeHighThreshold - coldNodeWorkrate;
            
            
            double maxSizeToMoveIdeally = Math.min(
                    nodeWorkrate/2 + 0.00001,
                    //permit it to exceed node high if there is no alternative (this is 'max' not 'ideal'),
                    //so long as it still gives a significant benefit
                    //                      getConfiguration().nodeHighWaterMark - coldNodeWorkrate,
                    (nodeWorkrate - coldNodeWorkrate)*0.9);
            double maxSizeToMoveIfNoSmallButLarger = nodeWorkrate*3/4;
            
            Map<ItemType, Double> questionedNodeItems = getDataProvider().getItemWorkrates(node);
            if (questionedNodeItems == null) {
                if (LOG.isDebugEnabled())
                    LOG.debug(MessageFormat.format(
                            "policy "+getDataProvider().getName()+" balancing hot node "+questionedNodeName+" " +
                            "("+node+", workrate {0,number,#.##}), abandoned; " +
                            "item report for " + questionedNodeName + " unavailable",
                            nodeWorkrate));
                break;
            }
            ItemType itemToMove = findBestItemToMove(questionedNodeItems, idealSizeToMove, maxSizeToMoveIdeally,
                    maxSizeToMoveIfNoSmallButLarger, itemsMoved, coldNodeLocation);
            
            if (itemToMove == null) {
                if (LOG.isDebugEnabled())
                    LOG.debug(MessageFormat.format(
                            "policy "+getDataProvider().getName()+" balancing hot node "+questionedNodeName+" " +
                            "("+node+", workrate {0,number,#.##}), ending; " +
                            "no suitable segment found " +
                            "(ideal transition item size {1,number,#.##}, max {2,number,#.##}, " +
                            "moving to coldest node "+coldNodeName+" ("+coldNode+", workrate {3,number,#.##}); available items: {4}",
                            nodeWorkrate, idealSizeToMove, maxSizeToMoveIdeally, coldNodeWorkrate, questionedNodeItems) );
                break;
            }
            
            itemsMoved.add(itemToMove);
            double itemWorkrate = questionedNodeItems.get(itemToMove);
            
//            if (LOG.isLoggable(Level.FINE))
//                LOG.fine( MessageFormat.format(
//                        "policy "+getDataProvider().getAbbr()+" balancing hot node "+questionedNodeName+" " +
//                        "(workrate {0,number,#.##}, too high), transitioning " + itemToMove +
//                        " to "+coldNodeName+" (workrate {1,number,#.##}, now += {2,number,#.##})",
//                        questionedNodeTotalWorkrate, coldNodeWorkrate, segmentRate) );
            
            nodeWorkrate -= itemWorkrate;
            coldNodeWorkrate += itemWorkrate;
            
            moveItem(itemToMove, node, coldNode);
            ++migrationCount;
        }
        
        if (LOG.isDebugEnabled()) {
            if (iterationCount == 0) {
                if (LOG.isTraceEnabled())
                    LOG.trace( MessageFormat.format(
                            "policy "+getDataProvider().getName()+" balancing if hot finished at node "+node+"; " +
                            "workrate {0,number,#.##} not hot",
                            originalNodeWorkrate) );
            }
            else if (itemsMoved.isEmpty()) {
                if (LOG.isTraceEnabled())
                    LOG.trace( MessageFormat.format(
                            "policy "+getDataProvider().getName()+" balancing finished at hot node "+node+" " +
                            "(workrate {0,number,#.##}); no way to improve it",
                            originalNodeWorkrate) );
            } else {
                LOG.debug( MessageFormat.format(
                        "policy "+getDataProvider().getName()+" balancing finished at hot node "+node+"; " +
                        "workrate from {0,number,#.##} to {1,number,#.##} (report now says {2,number,#.##}) " +
                        "by moving off {3}",
                        originalNodeWorkrate,
                        nodeWorkrate,
                        getDataProvider().getTotalWorkrate(node),
                        itemsMoved
                        ) );
            }
        }
        return !itemsMoved.isEmpty();
    }
    
    protected boolean balanceItemsOnColdNode(NodeType questionedNode, double questionedNodeTotalWorkrate, boolean gonnaGrow) {
        // Abort if the node has pending adjustments.
        Map<ItemType, Double> items = getDataProvider().getItemWorkrates(questionedNode);
        if (items == null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug( MessageFormat.format(
                        "policy "+getDataProvider().getName()+" not balancing cold node "+questionedNode+" " +
                        "(workrate {0,number,#.##}); workrate breakdown unavailable (probably reverting)",
                        questionedNodeTotalWorkrate) );
            }
            return false;
        }
        for (ItemType item : items.keySet()) {
            if (!model.isItemMoveable(item)) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug( MessageFormat.format(
                            "policy "+getDataProvider().getName()+" not balancing cold node "+questionedNode+" " +
                            "(workrate {0,number,#.##}); at least one item ("+item+") is in flux",
                            questionedNodeTotalWorkrate) );
                }
                return false;
            }
        }
        
        double originalQuestionedNodeTotalWorkrate = questionedNodeTotalWorkrate;
        int numMigrations = 0;
        
        Set<ItemType> itemsMoved = new LinkedHashSet<ItemType>();
        Set<NodeType> nodesChecked = new LinkedHashSet<NodeType>();
        
        int iters = 0;
        Location questionedLocation = getDataProvider().getLocation(questionedNode);
        
        double lowThreshold = model.getLowThreshold(questionedNode);
        while (questionedNodeTotalWorkrate < lowThreshold) {
            iters++;
            
            if (LOG.isDebugEnabled()) {
                LOG.debug( MessageFormat.format(
                        "policy "+getDataProvider().getName()+" considering balancing cold node "+questionedNode+" " +
                        "(workrate {0,number,#.##}); iteration "+iters, questionedNodeTotalWorkrate));
            }
            
            // move from cold node, to hottest
            
            NodeType hotNode = helper.findHottestContainer(nodesChecked);
            
            if (hotNode == null) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug( MessageFormat.format(
                            "policy "+getDataProvider().getName()+" not balancing cold node "+questionedNode+" " +
                            "(workrate {0,number,#.##}); no hottest node available", questionedNodeTotalWorkrate) );
                }
                
                break;
            }
            if (hotNode.equals(questionedNode)) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug( MessageFormat.format(
                            "policy "+getDataProvider().getName()+" not balancing cold node "+questionedNode+" " +
                            "(workrate {0,number,#.##}); it is also the hottest modfiable node", questionedNodeTotalWorkrate) );
                }
                break;
            }
            
            
            double hotNodeWorkrate = getDataProvider().getTotalWorkrate(hotNode);
            double hotNodeLowThreshold = model.getLowThreshold(hotNode);
            double hotNodeHighThreshold = model.getHighThreshold(hotNode);
            boolean emergencyLoadBalancing = false;  //doesn't apply to cold
            if (hotNodeWorkrate == -1 || hotNodeLowThreshold == -1 || hotNodeHighThreshold == -1) {
                // hotNode presumably has been removed; TODO log
                break;
            }
            if (hotNodeWorkrate <= hotNodeLowThreshold && !emergencyLoadBalancing) {
                //don't balance if all nodes are too low
                
                //for now, stop warning if it is a recurring theme!
//                Level level = loggedHottestTooLow ? Level.FINER : Level.INFO;
//                LOG.log(level, MessageFormat.format(
//                        "policy "+getDataProvider().getAbbr()+" not balancing cold node "+questionedNode+" " +
//                        "(workrate {0,number,#.##}); hottest node "+hotNode+" has workrate {1,number,#.##} also too low" +
//                        (loggedHottestTooLow ? "" : " (future cases will be logged at finer)"),
//                        questionedNodeTotalWorkrate, hotNodeWorkrate) );
//                loggedHottestTooLow = true;
                break;
            }
            if (gonnaGrow && (hotNodeWorkrate <= hotNodeHighThreshold && !emergencyLoadBalancing)) {
                //if we're growing the pool, refuse to balance unless the hot node is quite hot
                
                //again, stop warning if it is a recurring theme!
//                Level level = loggedHottestTooLow ? Level.FINER : Level.INFO;
//                LOG.log(level, MessageFormat.format(
//                        "policy "+getDataProvider().getAbbr()+" not balancing cold node "+questionedNode+" " +
//                        "(workrate {0,number,#.##}); hottest node "+hotNode+" has workrate {1,number,#.##} also too low to accept while pool is growing"+
//                        (loggedHottestTooLow ? "" : " (future cases will be logged at finer)"),
//                        questionedNodeTotalWorkrate, hotNodeWorkrate) );
//                loggedHottestTooLow = true;
                break;
            }
            
            String questionedNodeName = getDataProvider().getName(questionedNode);
            String hotNodeName = getDataProvider().getName(hotNode);
            
            if (LOG.isDebugEnabled()) {
                LOG.debug( MessageFormat.format(
                        "policy "+getDataProvider().getName()+" balancing cold node "+questionedNodeName+" " +
                        "("+questionedNode+", workrate {0,number,#.##}), " +
                        "considering source "+hotNodeName+" ("+hotNode+", workrate {1,number,#.##})",
                        questionedNodeTotalWorkrate, hotNodeWorkrate) );
            }
            
            double idealSizeToMove = (hotNodeWorkrate - questionedNodeTotalWorkrate) / 2;
            //if the 'ideal' amount to move would cause cold to be too hot, then reduce ideal amount
            double targetNodeHighThreshold = model.getHighThreshold(questionedNode);
            if (idealSizeToMove + questionedNodeTotalWorkrate > targetNodeHighThreshold)
                idealSizeToMove = targetNodeHighThreshold - questionedNodeTotalWorkrate;
            double maxSizeToMoveIdeally = Math.min(
                    hotNodeWorkrate/2,
                    //allow to swap order, but not very much (0.9 was allowed when balancing high)
                    (hotNodeWorkrate - questionedNodeTotalWorkrate)*0.6);
            double maxSizeToMoveIfNoSmallButLarger = questionedNodeTotalWorkrate*3/4;
            
            Map<ItemType, Double> hotNodeItems = getDataProvider().getItemWorkrates(hotNode);
            if (hotNodeItems == null) {
                if (LOG.isDebugEnabled())
                    LOG.debug(MessageFormat.format(
                            "policy "+getDataProvider().getName()+" balancing cold node "+questionedNodeName+" " +
                            "("+questionedNode+", workrate {0,number,#.##}), " +
                            "excluding hot node "+hotNodeName+" because its item report unavailable",
                            questionedNodeTotalWorkrate));
                nodesChecked.add(hotNode);
                continue;
            }
            
            ItemType itemToMove = findBestItemToMove(hotNodeItems, idealSizeToMove, maxSizeToMoveIdeally,
                    maxSizeToMoveIfNoSmallButLarger, itemsMoved, questionedLocation);
            if (itemToMove == null) {
                if (LOG.isDebugEnabled())
                    LOG.debug(MessageFormat.format(
                            "policy "+getDataProvider().getName()+" balancing cold node "+questionedNodeName+" " +
                            "("+questionedNode+", workrate {0,number,#.##}), " +
                            "excluding hot node "+hotNodeName+" because it has no appilcable items " +
                            "(ideal transition item size {1,number,#.##}, max {2,number,#.##}, " +
                            "moving from hot node "+hotNodeName+" ("+hotNode+", workrate {3,number,#.##}); available items: {4}",
                            questionedNodeTotalWorkrate, idealSizeToMove, maxSizeToMoveIdeally, hotNodeWorkrate, hotNodeItems) );
                
                nodesChecked.add(hotNode);
                continue;
            }
            
            itemsMoved.add(itemToMove);
            double segmentRate = hotNodeItems.get(itemToMove);
            
//            if (LOG.isLoggable(Level.FINE))
//                LOG.fine( MessageFormat.format(
//                        "policy "+getDataProvider().getAbbr()+" balancing cold node "+questionedNodeName+" " +
//                        "(workrate {0,number,#.##}, too low), transitioning " + itemToMove +
//                        " from "+hotNodeName+" (workrate {1,number,#.##}, now -= {2,number,#.##})",
//                        questionedNodeTotalWorkrate, hotNodeWorkrate, segmentRate) );
            
            questionedNodeTotalWorkrate += segmentRate;
            hotNodeWorkrate -= segmentRate;
            
            moveItem(itemToMove, hotNode, questionedNode);
            
            if (++numMigrations >= getMaxMigrationsPerBalancingNode()) {
                break;
            }
        }
        
        if (LOG.isDebugEnabled()) {
            if (iters == 0) {
                if (LOG.isTraceEnabled())
                    LOG.trace( MessageFormat.format(
                            "policy "+getDataProvider().getName()+" balancing if cold finished at node "+questionedNode+"; " +
                            "workrate {0,number,#.##} not cold",
                            originalQuestionedNodeTotalWorkrate) );
            }
            else if (itemsMoved.isEmpty()) {
                if (LOG.isTraceEnabled())
                    LOG.trace( MessageFormat.format(
                            "policy "+getDataProvider().getName()+" balancing finished at cold node "+questionedNode+" " +
                            "(workrate {0,number,#.##}); no way to improve it",
                            originalQuestionedNodeTotalWorkrate) );
            } else {
                LOG.debug( MessageFormat.format(
                        "policy "+getDataProvider().getName()+" balancing finished at cold node "+questionedNode+"; " +
                        "workrate from {0,number,#.##} to {1,number,#.##} (report now says {2,number,#.##}) " +
                        "by moving in {3}",
                        originalQuestionedNodeTotalWorkrate,
                        questionedNodeTotalWorkrate,
                        getDataProvider().getTotalWorkrate(questionedNode),
                        itemsMoved) );
            }
        }
        return !itemsMoved.isEmpty();
    }
    
    protected void moveItem(ItemType item, NodeType oldNode, NodeType newNode) {
        item.move(newNode);
        model.onItemMoved(item, newNode);
    }
    
    /**
     * "Best" is defined as nearest to the targetCost, without exceeding maxCost, unless maxCostIfNothingSmallerButLarger > 0
     * which does just that (useful if the ideal and target are estimates and aren't quite right, typically it will take
     * something larger than maxRate but less than half the total rate, which is only possible when the estimates don't agree)
     */
    protected ItemType findBestItemToMove(Map<ItemType, Double> costsPerItem, double targetCost, double maxCost,
            double maxCostIfNothingSmallerButLarger, Set<ItemType> excludedItems, Location locationIfKnown) {
        
        ItemType closestMatch = null;
        ItemType smallestMoveable = null, largest = null;
        double minDiff = Double.MAX_VALUE, smallestC = Double.MAX_VALUE, largestC = Double.MIN_VALUE;
        boolean exclusions = false;
        
        for (Entry<ItemType, Double> entry : costsPerItem.entrySet()) {
            ItemType item = entry.getKey();
            Double cost = entry.getValue();
            
            if (cost == null) {
                if (LOG.isDebugEnabled()) LOG.debug(MessageFormat.format("Item ''{0}'' has null workrate: skipping", item));
                continue;
            }
            
            if (!model.isItemMoveable(item)) {
                if (LOG.isDebugEnabled()) LOG.debug(MessageFormat.format("Item ''{0}'' cannot be moved: skipping", item));
                continue;
            }
            if (cost < 0) {
                if (LOG.isDebugEnabled()) LOG.debug(MessageFormat.format("Item ''{0}'' subject to recent adjustment: skipping", item));
                continue;
            }
            if (excludedItems.contains(item)) {
                exclusions = true;
                continue;
            }
            if (cost < 0) { // FIXME: already tested above
                exclusions = true;
                continue;
            }
            if (cost <= 0) { // FIXME: overlaps previous condition
                continue;
            }
            if (largest == null || cost > largestC) {
                largest = item;
                largestC = cost;
            }
            if (!model.isItemMoveable(item)) { // FIXME: already tested above
                continue;
            }
            if (locationIfKnown != null && !model.isItemAllowedIn(item, locationIfKnown)) {
                continue;
            }
            if (smallestMoveable == null || cost < smallestC) {
                smallestMoveable = item;
                smallestC = cost;
            }
            if (cost > maxCost) {
                continue;
            }
            double diff = Math.abs(targetCost - cost);
            if (closestMatch == null || diff < minDiff) {
                closestMatch = item;
                minDiff = diff;
            }
        }
        
        if (closestMatch != null)
            return closestMatch;
        
        if (smallestC < maxCostIfNothingSmallerButLarger && smallestC < largestC && !exclusions)
            return smallestMoveable;
        
        return null;
    }
    
}
