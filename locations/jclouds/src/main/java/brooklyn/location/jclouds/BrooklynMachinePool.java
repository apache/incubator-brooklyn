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
package brooklyn.location.jclouds;

import static brooklyn.location.jclouds.pool.MachinePoolPredicates.matching;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.jclouds.compute.domain.NodeMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.trait.Startable;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.location.jclouds.pool.MachinePool;
import brooklyn.location.jclouds.pool.MachineSet;
import brooklyn.location.jclouds.pool.ReusableMachineTemplate;
import brooklyn.management.Task;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.task.BasicExecutionContext;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;

/**
 * @deprecated since 0.6.0; never used in production setting, and thus of dubious value; best avoided as unlikely to be supported in future versions
 */
@Deprecated
public class BrooklynMachinePool extends MachinePool {

    private static final Logger log = LoggerFactory.getLogger(BrooklynMachinePool.class);
    
    protected final JcloudsLocation location;
    final List<Task<?>> activeTasks = new ArrayList<Task<?>>();
    final String providerLocationId;
    
    public BrooklynMachinePool(JcloudsLocation l) {
        super(l.getComputeService());
        providerLocationId = l.getRegion();
        this.location = l;
    }
    
    /** claims a machine with the indicated spec, creating if necessary */
    public SshMachineLocation obtain(ReusableMachineTemplate t) {
        MachineSet previous = unclaimed(matching(t));
        
        while (true) {
            NodeMetadata m = claim(1, t).iterator().next();
            // TODO ideally shouldn't have to rebind
            SshMachineLocation result = null;
            try {
                result = toSshMachineLocation( m );
            } catch (Exception e) {
                if (previous.contains(m)) {
                    log.debug("attempt to bind to previous existing machine "+m+" failed (will blacklist and retry another): "+e);
                } else {
                    log.warn("attempt to bind to machine "+m+" failed: "+e);
                    throw Throwables.propagate(e);
                }
            }
            if (result!=null) return result;
            if (previous.contains(m)) {
                log.debug("could not bind to previous existing machine "+m+"; blacklisting and trying a new one");
                addToBlacklist(new MachineSet(m));
            } else {
                throw new IllegalStateException("cannot bind/connect to newly created machine; error in configuration");
            }
        }
    }
    
    protected MachineSet filterForAllowedMachines(MachineSet input) {
        MachineSet result = super.filterForAllowedMachines(input);
        if (providerLocationId!=null) {
            result = result.filtered(matching( new ReusableMachineTemplate().locationId(providerLocationId).strict(false) ));
        }
        return result;
    }

    /** returns an SshMachineLocation, if one can be created and accessed; returns null if it cannot be created */
    protected SshMachineLocation toSshMachineLocation(NodeMetadata m) {
        try {
            JcloudsSshMachineLocation sshM = location.rebindMachine(m);
            if (sshM.execCommands("check-reachable", Arrays.asList("whoami")) != 0) {
                log.warn("cannot bind to machine "+m);
                return null;
            }
            return sshM;
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
    }
    
    @Override
    public MachineSet create(int count, ReusableMachineTemplate template) {
        List<NodeMetadata> nodes = new ArrayList<NodeMetadata>();
        for (int i=0; i<count; i++) {
            // TODO this in parallel
            JcloudsSshMachineLocation m;
            try {
                m = location.obtain(MutableMap.of("callerContext", ""+this+"("+template+")"), template);
            } catch (Exception e) {
                throw Throwables.propagate(e);
            }
            nodes.add(m.getNode());
        }
        MachineSet result = new MachineSet(nodes);
        registerNewNodes(result, template);
        return result;
    }

    public boolean unclaim(SshMachineLocation location) {
        init();
        if (location instanceof JcloudsSshMachineLocation)
            return unclaim(new MachineSet( ((JcloudsSshMachineLocation)location).getNode()) ) > 0;
        return false;
    }
    public boolean destroy(SshMachineLocation location) {
        init();
        if (location instanceof JcloudsSshMachineLocation)
            return destroy(new MachineSet( ((JcloudsSshMachineLocation)location).getNode()) ) > 0;
        return false;
    }

    // TODO we need to remove stale tasks somewhere
    protected <T> Task<T> addTask(Task<T> t) {
        synchronized (activeTasks) { activeTasks.add(t); }
        return t;
    }
    
    public List<Task<?>> getActiveTasks() {
        List<Task<?>> result;
        synchronized (activeTasks) { result = ImmutableList.<Task<?>>copyOf(activeTasks); }
        return result;
    }

    public void blockUntilTasksEnded() {
        while (true) {
            boolean allDone = true;
            List<Task<?>> tt = getActiveTasks();
            for (Task<?> t: tt) {
                if (!t.isDone()) {
                    allDone = false;
                    if (log.isDebugEnabled()) log.debug("Pool "+this+", blocking for completion of: "+t);
                    t.blockUntilEnded();
                }
            }
            synchronized (activeTasks) {
                List<Task> newTT = new ArrayList<Task>(getActiveTasks());
                newTT.removeAll(tt);
                if (allDone && tt.isEmpty()) {
                    //task list has stabilized, and there are no active tasks; clear and exit
                    if (log.isDebugEnabled()) log.debug("Pool "+this+", all known tasks have completed, clearing list");
                    activeTasks.clear();
                    break;
                }
                if (log.isDebugEnabled()) log.debug("Pool "+this+", all previously known tasks have completed, but there are new tasks ("+newTT+") checking them");
            }
        }
    }

    /** starts the given template; for use only within a task (e.g. application's start effector).
     * returns a child task of the current task.
     * <p>
     * throws exception if not in a task. (you will have to claim, then invoke the effectors manually.) */
    public Task<?> start(final ReusableMachineTemplate template, final List<? extends Startable> entities) {
        BasicExecutionContext ctx = BasicExecutionContext.getCurrentExecutionContext();
        if (ctx==null) throw new IllegalStateException("Pool.start is only permitted within a task (effector)");
        final AtomicReference<Task<?>> t = new AtomicReference<Task<?>>();
        synchronized (t) {
            t.set(ctx.submit(new Runnable() {
                public void run() {
                    synchronized (t) {
                        if (log.isDebugEnabled()) log.debug("Pool "+this+", task "+t.get()+" claiming a "+template);
                        SshMachineLocation m = obtain(template);
                        if (log.isDebugEnabled()) log.debug("Pool "+this+", task "+t.get()+" got "+m+"; starting "+entities);
                        for (Startable entity: entities)
                            addTask( ((Entity)entity).invoke(Startable.START, MutableMap.of("locations", Arrays.asList(m))) );
                    }
                }
            }));
        }
        addTask(t.get());
        return t.get();
    }

    /** @see #start(ReusableMachineTemplate, List) */
    public Task<?> start(ReusableMachineTemplate template, Startable ...entities) {
        return start(template, Arrays.asList(entities));
    }


}
