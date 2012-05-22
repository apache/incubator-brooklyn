package brooklyn.location.basic.jclouds;

import static brooklyn.location.basic.jclouds.pool.MachinePoolPredicates.matching;

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
import brooklyn.location.basic.jclouds.JcloudsLocation.JcloudsSshMachineLocation;
import brooklyn.location.basic.jclouds.pool.MachinePool;
import brooklyn.location.basic.jclouds.pool.MachineSet;
import brooklyn.location.basic.jclouds.pool.ReusableMachineTemplate;
import brooklyn.management.Task;
import brooklyn.util.MutableMap;
import brooklyn.util.task.BasicExecutionContext;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;

public class BrooklynMachinePool extends MachinePool {

    private static final Logger log = LoggerFactory.getLogger(BrooklynMachinePool.class);
    
    final JcloudsLocation location;
    final List<Task> activeTasks = new ArrayList<Task>();
    final String providerLocationId;
    
    public BrooklynMachinePool(JcloudsLocation l) {
        super(l.getComputeService());
        providerLocationId = l.getJcloudsProviderLocationId();
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
            result = matching( new ReusableMachineTemplate().locationId(providerLocationId).strict(false) ).apply(input);
        }
        return result;
    }

    /** returns an SshMachineLocation, if one can be created and accessed; returns null if it cannot be created */
    protected SshMachineLocation toSshMachineLocation(NodeMetadata m) {
        try {
            JcloudsSshMachineLocation sshM = location.rebindMachine(m);
            if (sshM.exec(Arrays.asList("whoami")) != 0) {
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
                m = location.obtain(template);
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
    protected Task addTask(Task t) {
        synchronized (activeTasks) { activeTasks.add(t); }
        return t;
    }
    
    public List<Task> getActiveTasks() {
        List<Task> result;
        synchronized (activeTasks) { result = ImmutableList.copyOf(activeTasks); }
        return result;
    }

    public void blockUntilTasksEnded() {
        boolean allDone = true;
        while (true) {
            List<Task> tt = getActiveTasks();
            for (Task t: tt) {
                if (!t.isDone()) {
                    allDone = false;
                    if (log.isDebugEnabled()) log.debug("Pool "+this+", blocking for completion of: "+t);
                    t.blockUntilEnded();
                }
            }
            synchronized (activeTasks) {
                if (allDone && tt.equals(getActiveTasks())) {
                    //task list has stabilized, and there are no active tasks; clear and exit
                    if (log.isDebugEnabled()) log.debug("Pool "+this+", all known tasks have completed, clearing list");
                    activeTasks.clear();
                    break;
                }
                if (log.isDebugEnabled()) log.debug("Pool "+this+", all previously known tasks have completed, but there are new tasks, checking them");
            }
        }
    }

    /** starts the given template; for use only within a task (e.g. application's start effector).
     * returns a child task of the current task.
     * <p>
     * throws exception if not in a task. (you will have to claim, then invoke the effectors manually.) */
    public Task start(final ReusableMachineTemplate template, final List<? extends Startable> entities) {
        BasicExecutionContext ctx = BasicExecutionContext.getCurrentExecutionContext();
        if (ctx==null) throw new IllegalStateException("Pool.start is only permitted within a task (effector)");
        final AtomicReference<Task> t = new AtomicReference<Task>();
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
    public Task start(ReusableMachineTemplate template, Startable ...entities) {
        return start(template, Arrays.asList(entities));
    }


}
