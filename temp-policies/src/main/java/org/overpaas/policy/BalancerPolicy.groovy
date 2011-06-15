package org.overpaas.policy

import java.util.Collection
import java.util.Map
import java.util.logging.Logger

import org.overpaas.activity.EntityRankers
import org.overpaas.activity.Event
import org.overpaas.activity.EventDictionary
import org.overpaas.activity.EventFilters
import org.overpaas.activity.EventListener
import org.overpaas.activity.impl.EventImpl

import com.google.common.base.Predicate

public class BalancerPolicy<T extends Comparable<T>> implements Policy, EventListener {
    
    /* TODO ENHANCEMENTS, copied from AbstractCommonElasticityPolicy
     * TODO Get the good stuff from AbstractCommonElasticityPolicy
     *
     * if a node has a large backlog, then balance so that it isn't getting new inputs
     * (even if we balance to make it empty, it will then pull when it is ready)
     * [would this help, doubtful at Ms, maybe at TP/MRs ? ].
     * NB - backlog means a large discrepancy between node total and sum of items
     *
     * limit amount of balancing done at an M node (due to time it takes to migrate)
     *
     * permit balancing even when we are growing/shrinking, though maybe at revised levels
     *
     * headroom
     *
     * pull to lower nodes
     *
     * include backlog in many estimates
     *
     *
     * consider sum of items as compared with estimated total?  and/or backlog
     *
     * finer-grained restrictions on when policies can run;
     * currently there is a single mutex and ALL transitions also operate on this mutex
     *
     * improve balancing limits, currently they are forgotten when a policy finishes
     * (which is okay if transitions don't impose overhead of longer than policy frequency
     * but would be a problem otherwise)
     *
     * for scale out (and/or balance hot) use a hot prediction;
     * i.e. predictive model doesn't return a single value, but a range (worst,mean,best) ?
     *
     * more configurable headroom
     */

    private static final Logger LOG = Logger.getLogger(BalancerPolicy.class.getName())
    
    private BalanceableEntity entity
    
    private String[] moveableEntityWorkrateMetricName
    private String[] balanceableSubEntityWorkrateMetricName
    
    private T minAbsoluteDifference
    private double minFractionDifference
    private T minWatermark
    private T maxWatermark

    public BalancerPolicy() {
    }
    
    public void setEntity(BalanceableEntity entity) {
        this.entity = entity
    }

    public void setBalanceableSubEntityWorkrateMetricName(String[] val) {
        this.balanceableSubEntityWorkrateMetricName = val
    }
    
    public void setMoveableEntityWorkrateMetricName(String[] val) {
        this.moveableEntityWorkrateMetricName = val
    }

    /**
     * If the workrates (hottest-coldest < val), then don't bothering moving anything.
     */
    public void setMinAbsoluteDifference(T val) {
        this.minAbsoluteDifference = val
    }
    
    /**
     * If the workrates (hottest-coldest < hottest*val), then don't bothering moving anything.
     */
    public void setMinFractionDifference(double val) {
        this.minFractionDifference = val
    }
    
    /**
     * If the workrate (hottest < val), then don't bother moving anything.
     * If average would still be > val with one less entity, then emit too_cold event.
     */
    public void setMinWatermark(T val) {
        this.minWatermark = val    
    }
    
    /**
     * If coldest > val, then emit too_hot event.
     */
    public void setMaxWatermark(T val) {
        this.maxWatermark = val
    }
    
    public void postConstruct() {
        // FIXME If I've been given a closure for evaluating the mediator's workrate, then how do I know what my subscription should be?
        // FIXME subscribe to what? The mediators can come and go...
        
        entity.subscribe(EventFilters.all(), this)
        entity.subscribeToChildren(
                new Predicate<Entity>() {
                    boolean apply(Entity val) {
                        return entity.getBalanceableSubContainers().contains(val)
                    }
                },
                EventFilters.newMetricFilter(balanceableSubEntityWorkrateMetricName),
                this);
        entity.subscribeToChildren(
                new Predicate<Entity>() {
                    boolean apply(Entity val) {
                        return entity.getMovableItems().contains(val)
                    }
                },
                EventFilters.newMetricFilter(moveableEntityWorkrateMetricName),
                this);
    }
    
    public void onEvent(Event event) {
        Collection<Entity> containers = entity.getBalanceableSubContainers();
        if (containers.isEmpty()) return
        
        List<Entity> busiestEntities = new ArrayList<Entity>(containers)
        Collections.sort(busiestEntities, EntityRankers.newMetricRanker(balanceableSubEntityWorkrateMetricName))
        Collections.reverse(busiestEntities)
        
        def coldestEntity = busiestEntities.get(busiestEntities.size()-1)
        
        // Keep trying to find something to balance; e.g. could be that first entity has only one extremely busy item
        for (int i = 0; i < busiestEntities.size()-1; i++) {
            int numMoved = balanceItemsBetweenHottestAndColdest(busiestEntities.get(i), coldestEntity)
            if (numMoved > 0) break
        }

        // Check if pool is too cold
        if (minWatermark != null && getTemperature(busiestEntities.get(0)) < minWatermark) {
            def total = getTotalTemperature()
            def numContainers = containers.size()
            if (numContainers >= 2 && (total / (numContainers-1) < minWatermark)) {
                def hotestTemperature = getTemperature(busiestEntities.get(0))
                def coldestTemperature = getTemperature(coldestEntity)
                Map metrics = ["average":total/numContainers, "size":numContainers, 
                        "coldest":coldestTemperature, "hottest":hotestTemperature]
                
                entity.raiseEvent(new EventImpl(EventDictionary.TOO_COLD_EVENT_NAME, metrics));
            }
        }
        
        // Check if pool is too hot
        if (maxWatermark != null && getTemperature(coldestEntity) > maxWatermark) {
            // TODO Also check if there are items that could usefully be balanced if we had another container
            def total = getTotalTemperature()
            def numContainers = containers.size()
            def hotestTemperature = getTemperature(busiestEntities.get(0))
            def coldestTemperature = getTemperature(coldestEntity)
            Map metrics = ["average":total/numContainers, "size":numContainers, 
                    "coldest":coldestTemperature, "hottest":hotestTemperature]
            
            entity.raiseEvent(new EventImpl(EventDictionary.TOO_HOT_EVENT_NAME, metrics));
        }
    }
    
    protected int balanceItemsBetweenHottestAndColdest(Entity hottest, Entity coldest) {
        def hottestTemperature = getTemperature(hottest)
        def coldestTemperature = getTemperature(coldest)
        def differenceInTemperature = hottestTemperature - coldestTemperature
        
        if (differenceInTemperature < minAbsoluteDifference) {
            return 0 // absolute difference is too small to bother moving
        }
        if (differenceInTemperature < hottestTemperature*minFractionDifference) {
            return 0 // percentage difference is too small to bother moving
        }
        if (minWatermark != null && hottestTemperature < minWatermark) {
            return 0 // entity is not busy enough to bother with
        }
        
        // Ideal to move is 1/2 of the difference; that would make the two identically loaded
        // It's fiddly maths for how much to move. E.g. have containers with workrates a=200, b=100; 
        // It's definitely not worth "rebalancing" if the change doesn't reduce the difference (e.g. a=[100+100],b=[100]);
        // TODO It's arguably/configurably not worth bothering to rebalance if the best you can do is a=101, b=199;
        //      Can we use minAbsoluteDifference/minFractionDifference to guide decision of maxToMove?
        
        def idealToMove = 0.5d*differenceInTemperature
        def maxToMove = differenceInTemperature
        
        List<MoveableEntity> moveables = entity.getMovableItemsAt(hottest)
        Map moveableTemperatures = getTemperaturesOfMoveablesAt(hottest)
        Collection<MoveableEntity> itemsToMove = chooseItemsToMove(moveableTemperatures, idealToMove, maxToMove)
        for (itemToMove in itemsToMove) {
            entity.move(itemToMove, coldest)
        }
        return itemsToMove.size()
    }
    
    /**
     * Optimises for moving as few things as possible; but therefore sacrifices optimal balance.
     */
    private Collection<MoveableEntity> chooseItemsToMove(Map moveableTemperatures, idealToMove, maxToMove) {
        def idealRemainingToMove = idealToMove
        def maxRemainingToMove = maxToMove
        Collection<MoveableEntity> result = new ArrayList<MoveableEntity>()
        Map busiestMoveableTemperatures = sortMapByReverseValues(moveableTemperatures)
        
        // First pass, we add the biggest things possible without going over the ideal
        for (e in busiestMoveableTemperatures) {
            if (e.getValue() == null) continue
            if (e.getValue() > idealRemainingToMove) break
            
            result.add(e.getKey())
            idealRemainingToMove -= e.getValue()
            maxRemainingToMove -= e.getValue()
        }
        
        // Second pass, we add the smallest things to get as close to ideal as possible (positive or negative)
        for (e in reverse(busiestMoveableTemperatures)) {
            if (e.getValue() == null) continue
            if (idealRemainingToMove <= 0)  break
            if (e.getValue() >= maxRemainingToMove) break
            if (e.getValue() >= (idealRemainingToMove*2)) break // moving would make it less balanced than just leaving it here
            
            result.add(e.getKey())
            idealRemainingToMove -= e.getValue()
            maxRemainingToMove -= e.getValue()
        }
        
        return result
    }
    
    private T getTotalTemperature() {
        def result = 0
        for (container in entity.getBalanceableSubContainers()) {
            result += getTemperature(container)
        }
        return result
    }
    
    private T getTemperature(Entity container) {
        /*
        workrate {
            segments { 
                IBM: {
                    reqsIn: 34
                    reqsOut: 44
                }
                ...
        
        
        moveableItemsActivityMap = { workrate.segments }
        temperatureOfMoveableItem = { reqsIn+reqsOut }
        
        in code:
        rawItemsMap = invokeOn(moveableItemsActivityMap, mNode.activity)
        temperaturePerItemMap = [:]
        rawItemsMap.each { temperaturePerItemMap.put it.key(), invokeOn(temperatureOfMoveableItem, it.value()) }

        results in:
        temperaturePerItemMap == [ IBM:78, ... ]


        
        
        workrate {
            reqsIn: 34
            reqsOut: 44
        }
        mediatorWorkrateCalculator = { workrate.reqsIn + workrate.reqsOut }
        temperature = invokeOn(mediatorWorkrateCalculator, entity.getMetrics())
        
        workrateCalculator.call(metrics)
        Closure {
            metrics
            
        }
        */
        return container.getMetrics().getRaw(balanceableSubEntityWorkrateMetricName)
    }
    
    private Map getTemperaturesOfMoveablesAt(Entity container) {
        Map result = [:]
        Collection<MoveableEntity> moveables = entity.getMovableItemsAt(container)
        for (m in moveables) {
            result.put(m, m.getMetrics().getRaw(moveableEntityWorkrateMetricName))
        }
        return result
    }
    
    // TODO What utility function for sortMapByReverseValues?
    private Map sortMapByReverseValues(Map map) {
        def valuesOrdered = new ArrayList(map.values())
        Collections.sort(valuesOrdered)
        def keysOrdered = new ArrayList(map.size())
        for (i in 1..map.size()) {
            keysOrdered.add(null)
        }
        
        for (key in map.keySet()) {
            def value = map.get(key)
            int index = valuesOrdered.indexOf(value)
            def offset = 0
            while (keysOrdered.get(index+offset) != null) {
                offset++
            }
            keysOrdered.set(index+offset, key)
        }
        
        def result = [:]
        for (key in keysOrdered) {
            result.put(key, map.get(key))
        }
        
        return reverse(result)
    }
    
    private Map reverse(Map map) {
        def result = [:]
        def reversedKeys = []
        for (key in map.keySet()) {
            reversedKeys.add(0, key)
        }
        for (key in reversedKeys) {
            result.put(key, map.get(key))
        }
        return result
    }
}
