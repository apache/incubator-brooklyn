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
package brooklyn.location.jclouds.pool;

import static brooklyn.location.jclouds.pool.MachinePoolPredicates.compose;
import static brooklyn.location.jclouds.pool.MachinePoolPredicates.matching;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jclouds.compute.ComputeService;
import org.jclouds.compute.RunNodesException;
import org.jclouds.compute.domain.ComputeMetadata;
import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.compute.domain.Template;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Predicate;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

/**
 * Contains details of machines detected at a given cloud (ComputeService),
 * and records claims made against those machines via this pool.
 * <p>
 * Machine instances themselves are persisted and rescanned as new instances of this class are created.
 * Claims however are specific to this instance of the class, i.e. <b>not</b> persisted.
 * <p>
 * This class is believed to be thread-safe.
 * Refreshes to the remote detected machines are synchronized on the pool instance.
 * Details of detected and claimed machines are also synchronized on the pool instance.
 * (If it is necessary to claim machines whilst the pool is being rescanned,
 * we can investigate a more sophisticated threading model.
 * Access to some fields is clearly independent and uses a tighter synchonization
 * strategy, e.g. templates.  
 * Synchronization of fields within a synch block on the class instance
 * is permitted, but not the other way round,
 * and synching on multiple fields is also not permitted.)
 * <p>
 * Callers wishing to guarantee results of e.g. ensureUnclaimed remaining available
 * can synchronize on this class for the duration that they wish to have that guarantee
 * (at the cost, of course, of any other threads being able to access this pool).
 * <p>
 * If underlying provisioning/destroying operations fail, the pool
 * currently may be in an unknown state, currently.
 * If more robustness is needed this can be added.
 * 
 * @deprecated since 0.6.0; never used in production setting, and thus of dubious value; best avoided as unlikely to be supported in future versions
 */
@Deprecated
public class MachinePool {
    
    private static final Logger log = LoggerFactory.getLogger(MachinePool.class);
    
    protected final ComputeService computeService;
    final AtomicBoolean refreshNeeded = new AtomicBoolean(true);
    final List<ReusableMachineTemplate> templates = new ArrayList<ReusableMachineTemplate>();
    String poolName = null;
    
    /** all machines detected, less those in the black list */
    volatile MachineSet detectedMachines = new MachineSet();
    volatile MachineSet matchedMachines = new MachineSet();
    volatile MachineSet claimedMachines = new MachineSet();
    volatile MachineSet blacklistedMachines = new MachineSet();
    
    public MachinePool(ComputeService computeService) {
        this.computeService = computeService;
    }
    
    protected synchronized void init() {
        if (!refreshNeeded.get()) return;
        refresh();
    }
    
    public void setPoolName(String poolName) {
        if (poolName!=null)
            log.warn("Changing pool name of "+this+" (from "+this.poolName+" to "+poolName+") is discouraged.");
        this.poolName = poolName;
    }
    /** pool name is used as a group/label by jclouds, for convenience only; 
     * it has no special properties for detecting matching instances
     * (use explicit tags on the templates, for that). 
     * defaults to name of pool class and user name.
     * callers should set pool name before getting, if using a custom name. */
    public synchronized String getPoolName() {
        if (poolName==null)
            poolName = getClass().getSimpleName()+"-"+System.getProperty("user.name");
        return poolName;
    }
    
    /** refreshes the pool of machines from the server (finding all instances matching the registered templates) */
    public synchronized void refresh() {
        refreshNeeded.set(false);
        Set<? extends ComputeMetadata> computes = computeService.listNodes();
        Set<NodeMetadata> nodes = new LinkedHashSet<NodeMetadata>();
        for (ComputeMetadata c: computes) {
            if (c instanceof NodeMetadata) {
                nodes.add((NodeMetadata)c);
            } else {
                // TODO should we try to fetch more info?
                log.warn("MachinePool "+this+" ignoring non-Node record for remote machine: "+c);
            }
        }

        MachineSet allNewDetectedMachines = new MachineSet(nodes);
        MachineSet newDetectedMachines = filterForAllowedMachines(allNewDetectedMachines);
        MachineSet oldDetectedMachines = detectedMachines;
        MachineSet newMatchedMachines = new MachineSet();
        detectedMachines = newDetectedMachines;

        MachineSet appearedMachinesIncludingBlacklist = allNewDetectedMachines.removed(oldDetectedMachines);
        MachineSet appearedMachines = filterForAllowedMachines(appearedMachinesIncludingBlacklist);
        if (appearedMachinesIncludingBlacklist.size()>appearedMachines.size())
            if (log.isDebugEnabled()) log.debug("Pool "+this+", ignoring "+(appearedMachinesIncludingBlacklist.size()-appearedMachines.size())+" disallowed");
        int matchedAppeared = 0;
        for (NodeMetadata m: appearedMachines) {
            if (m.getStatus() != NodeMetadata.Status.RUNNING) {
                if (log.isDebugEnabled()) 
                    log.debug("Pool "+this+", newly detected machine "+m+", not running ("+m.getStatus()+")");
            } else {
                Set<ReusableMachineTemplate> ts = getTemplatesMatchingInstance(m);
                if (!ts.isEmpty()) {
                    matchedAppeared++;
                    newMatchedMachines = newMatchedMachines.added(new MachineSet(m));
                    if (log.isDebugEnabled()) 
                        log.debug("Pool "+this+", newly detected machine "+m+", matches pool templates "+ts);
                } else {
                    if (log.isDebugEnabled()) 
                        log.debug("Pool "+this+", newly detected machine "+m+", does not match any pool templates");
                }
            }
        }
        if (matchedAppeared>0) {
            log.info("Pool "+this+" discovered "+matchedAppeared+" matching machines (of "+appearedMachines.size()+" total new; "+newDetectedMachines.size()+" total including claimed and unmatched)");
        } else {
            if (log.isDebugEnabled()) 
                log.debug("Pool "+this+" discovered "+matchedAppeared+" matching machines (of "+appearedMachines.size()+" total new; "+newDetectedMachines.size()+" total including claimed and unmatched)");
        }
        matchedMachines = newMatchedMachines;
    }

    protected MachineSet filterForAllowedMachines(MachineSet input) {
        return input.removed(blacklistedMachines);
    }

    // TODO template registry and claiming from a template could be a separate responsibility
    
    protected ReusableMachineTemplate registerTemplate(ReusableMachineTemplate template) {
        registerTemplates(template);
        return template;
    }
    protected void registerTemplates(ReusableMachineTemplate ...templatesToReg) {
        synchronized (templates) { 
            for (ReusableMachineTemplate template: templatesToReg)
                templates.add(template); 
        }
    }
    
    protected ReusableMachineTemplate newTemplate(String name) {
        return registerTemplate(new ReusableMachineTemplate(name));
    }

    
    public List<ReusableMachineTemplate> getTemplates() {
        List<ReusableMachineTemplate> result;
        synchronized (templates) { result = ImmutableList.copyOf(templates); }
        return result;
    }
    
    /** all machines matching any templates */
    public MachineSet all() {
        init();
        return matchedMachines;
    }

    /** machines matching any templates which have not been claimed */
    public MachineSet unclaimed() {
        init();
        synchronized (this) {
            return matchedMachines.removed(claimedMachines);
        }
    }
    
    /** returns all machines matching the given criteria (may be claimed) */
    @SuppressWarnings("unchecked")
    public MachineSet all(Predicate<NodeMetadata> criterion) {
        // To avoid generics complaints in callers caused by varargs, overload here
        return all(new Predicate[] {criterion});
    }
    
    /** returns all machines matching the given criteria (may be claimed) */
    public MachineSet all(Predicate<NodeMetadata> ...ops) {
        return new MachineSet(Iterables.filter(all(), compose(ops)));
    }

    /** returns unclaimed machines matching the given criteria */
    @SuppressWarnings("unchecked")
    public MachineSet unclaimed(Predicate<NodeMetadata> criterion) {
        // To avoid generics complaints in callers caused by varargs, overload here
        return unclaimed(new Predicate[] {criterion});
    }
    
    /** returns unclaimed machines matching the given criteria */
    public MachineSet unclaimed(Predicate<NodeMetadata> ...criteria) {
        return new MachineSet(Iterables.filter(unclaimed(), compose(criteria)));
    }

    /** creates machines if necessary so that this spec exists (may already be claimed however) 
     * returns a set of all matching machines, guaranteed non-empty 
     * (but possibly some are already claimed) */
    public MachineSet ensureExists(ReusableMachineTemplate template) {
        return ensureExists(1, template);
    }

    public synchronized void addToBlacklist(MachineSet newToBlacklist) {
        setBlacklist(blacklistedMachines.added(newToBlacklist));
    }
    
    /** replaces the blacklist set; callers should generally perform a refresh()
     * afterwards, to trigger re-detection of blacklisted machines
     */
    public synchronized void setBlacklist(MachineSet newBlacklist) {
        blacklistedMachines = newBlacklist;
        detectedMachines = detectedMachines.removed(blacklistedMachines);
        matchedMachines = matchedMachines.removed(blacklistedMachines);
    }
    
    /** creates machines if necessary so that this spec exists (may already be claimed however);
     * returns a set of all matching machines, of size at least count (but possibly some are already claimed).
     * (the pool can change at any point, so this set is a best-effort but may be out of date.
     * see javadoc comments on this class.) */
    public MachineSet ensureExists(int count, ReusableMachineTemplate template) {
        MachineSet current;
        current = all(matching(template));
        if (current.size() >= count)
            return current;
        //have to create more
        MachineSet moreNeeded = create(count-current.size(), template);
        return current.added(moreNeeded);
    }
    
    /** creates machines if necessary so that this spec can subsequently be claimed;
     * returns all such unclaimed machines, guaranteed to be non-empty.
    * (the pool can change at any point, so this set is a best-effort but may be out of date.
    * see javadoc comments on this class.) */
    public MachineSet ensureUnclaimed(ReusableMachineTemplate template) {
        return ensureUnclaimed(1, template);
    }

    /** creates machines if necessary so that this spec can subsequently be claimed;
     * returns a set of at least count unclaimed machines */
    public MachineSet ensureUnclaimed(int count, ReusableMachineTemplate template) {
        MachineSet current;
        current = unclaimed(matching(template));
        if (current.size() >= count)
            return current;
        //have to create more
        MachineSet moreNeeded = create(count-current.size(), template);
        return current.added(moreNeeded);
    }

    public Set<ReusableMachineTemplate> getTemplatesMatchingInstance(NodeMetadata nm) {
        Set<ReusableMachineTemplate> result = new LinkedHashSet<ReusableMachineTemplate>(); 
        for (ReusableMachineTemplate t: getTemplates()) {
            if (matching(t).apply(nm)) {
               result.add(t); 
            }
        }        
        return result;
    }
    
    /** creates the given number of machines of the indicated template */
    public MachineSet create(int count, ReusableMachineTemplate template) {
        Set<? extends NodeMetadata> nodes;
        try {
            Template t = template.newJcloudsTemplate(computeService);
            if (log.isDebugEnabled()) log.debug("Creating "+count+" new instances of "+t);
            nodes = computeService.createNodesInGroup(getPoolName(), count, t);
        } catch (RunNodesException e) {
            throw Throwables.propagate(e);
        }
        MachineSet result = new MachineSet(nodes);
        registerNewNodes(result, template);
        return result;
    }
    protected void registerNewNodes(MachineSet result, ReusableMachineTemplate template) {
        for (NodeMetadata m: result) {
            Set<ReusableMachineTemplate> ts = getTemplatesMatchingInstance(m);
            if (ts.isEmpty()) {
                log.error("Pool "+this+", created machine "+m+" from template "+template+", but no pool templates match!");
            } else {
                if (log.isDebugEnabled())
                    log.debug("Pool "+this+", created machine "+m+" from template "+template+", matching templates "+ts);
            }
        }
        synchronized (this) {
            detectedMachines = detectedMachines.added(result);
            matchedMachines = matchedMachines.added(result);
        }
    }

    /** claims the indicated number of machines with the indicated spec, creating if necessary */
    public MachineSet claim(int count, ReusableMachineTemplate t) {
        init();
        Set<NodeMetadata> claiming = new LinkedHashSet<NodeMetadata>();
        while (claiming.size() < count) {
            MachineSet mm = ensureUnclaimed(count - claiming.size(), t);
            for (NodeMetadata m : mm) {
                synchronized (this) {
                    if (claiming.size() < count && !claimedMachines.contains(m)) {
                        claiming.add(m);
                        claimedMachines = claimedMachines.added(new MachineSet(m));
                    }
                }
            }
        }
        MachineSet result = new MachineSet(claiming);
        return result;
    }


    /** claims the indicated set of machines;
     * throws exception if cannot all be claimed;
     * returns the set passed in if successful */
    public MachineSet claim(MachineSet set) {
        init();
        synchronized (this) {
            MachineSet originalClaimed = claimedMachines;
            claimedMachines = claimedMachines.added(set);
            MachineSet newlyClaimed = claimedMachines.removed(originalClaimed);
            if (newlyClaimed.size() != set.size()) {
                //did not claim all; unclaim and fail
                claimedMachines = originalClaimed;
                MachineSet unavailable = set.removed(newlyClaimed); 
                throw new IllegalArgumentException("Could not claim all requested machines; failed to claim "+unavailable);
            }
            return newlyClaimed;
        }
    }
    
    public int unclaim(MachineSet set) {
        init();
        synchronized (this) {
            MachineSet originalClaimed = claimedMachines;
            claimedMachines = claimedMachines.removed(set);
            return originalClaimed.size() - claimedMachines.size();
        }
    }

    
    public int destroy(final MachineSet set) {
        init();
        synchronized (this) {
            detectedMachines = detectedMachines.removed(set);
            matchedMachines = matchedMachines.removed(set);
            claimedMachines = claimedMachines.removed(set);
        }
        Set<? extends NodeMetadata> destroyed = computeService.destroyNodesMatching(new Predicate<NodeMetadata>() {
            @Override
            public boolean apply(NodeMetadata input) {
                return set.contains(input);
            }
        });
        synchronized (this) {
            //in case a rescan happened while we were destroying
            detectedMachines = detectedMachines.removed(set);
            matchedMachines = matchedMachines.removed(set);
            claimedMachines = claimedMachines.removed(set);
        }
        return destroyed.size();        
    }
        
    
}
