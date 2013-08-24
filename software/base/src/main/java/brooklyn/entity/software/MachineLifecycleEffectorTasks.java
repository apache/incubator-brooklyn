package brooklyn.entity.software;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.Callable;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Effector;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.BrooklynTasks;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.effector.EffectorBody;
import brooklyn.entity.effector.Effectors;
import brooklyn.entity.trait.Startable;
import brooklyn.event.feed.ConfigToAttributes;
import brooklyn.location.Location;
import brooklyn.location.MachineLocation;
import brooklyn.location.MachineProvisioningLocation;
import brooklyn.location.NoMachinesAvailableException;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.management.Task;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.task.DynamicTasks;
import brooklyn.util.task.Tasks;

import com.google.common.annotations.Beta;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.reflect.TypeToken;

/** 
 * Default skeleton for start/stop/restart tasks on machines.
 * Knows how to provision machines, making use of {@link ProvidesProvisioningFlags#obtainProvisioningFlags(MachineProvisioningLocation)},
 * and provides hooks for injecting behaviour at common places.
 * Methods are designed for overriding, with the convention that *Async methods should queue (and not block).
 * The following methods are commonly overridden (and you can safely queue tasks, block, or return immediately in these):
 * 
 *  <li> {@link #startProcessesAtMachine(Supplier)} (required)
 *  <li> {@link #stopProcessesAtMachine()} (required, but can be left blank if you assume the VM will be destroyed)
 *  <li> {@link #preStartCustom(MachineLocation)}
 *  <li> {@link #postStartCustom(MachineLocation)}
 *  <li> {@link #preStopCustom(MachineLocation)}
 *  
 * @since 0.6.0
 **/
@Beta
public abstract class MachineLifecycleEffectorTasks {

    private static final Logger log = LoggerFactory.getLogger(MachineLifecycleEffectorTasks.class);
    
    // TODO polymorphic parametrisation of effetor, take LOCATION, take strings, etc
    @SuppressWarnings("serial")
    public static final ConfigKey<Collection<? extends Location>> LOCATIONS =
            ConfigKeys.newConfigKey(new TypeToken<Collection<? extends Location>>() {}, "locations", 
            "locations where the entity should be started");

    /** attaches lifecycle effectors (start, restart, stop) to the given entity (post-creation) */ 
    public void attachLifecycleEffectors(Entity entity) {
        ((EntityInternal)entity).getMutableEntityType().addEffector(newStartEffector());
        ((EntityInternal)entity).getMutableEntityType().addEffector(newRestartEffector());
        ((EntityInternal)entity).getMutableEntityType().addEffector(newStopEffector());
    }
    
    /** returns an effector suitable for setting in a public static final or attaching dynamically;
     * the effector overrides the corresponding effector from {@link Startable} with 
     * the behaviour in this lifecycle class instance */ 
    public Effector<Void> newStartEffector() {
        return Effectors.effector(Startable.START).impl(newStartEffectorTask()).build();
    }

    /** as {@link #newStartEffector()} */
    public Effector<Void> newRestartEffector() {
        return Effectors.effector(Startable.RESTART).impl(newRestartEffectorTask()).build();
    }
    
    /** as {@link #newStartEffector()} */
    public Effector<Void> newStopEffector() {
        return Effectors.effector(Startable.STOP).impl(newStopEffectorTask()).build();
    }
    
    /** returns the TaskFactory which supplies the implementation for this effector,
     * calling the relevant method in this class ({@link #start(Collection)} */
    public EffectorBody<Void> newStartEffectorTask() {
        return new EffectorBody<Void>() {
            @Override
            public Void call(ConfigBag parameters) {
                Collection<? extends Location> locations = parameters.get(LOCATIONS);
                Preconditions.checkNotNull(locations, "locations");
                start(locations);
                return null;
            }
        };
    }

    /** as {@link #newStartEffectorTask()}, calling {@link #restart()} */
    public EffectorBody<Void> newRestartEffectorTask() {
        return new EffectorBody<Void>() {
            @Override
            public Void call(ConfigBag parameters) {
                restart();
                return null;
            }
        };
    }

    /** as {@link #newStartEffectorTask()}, calling {@link #stop()} */
    public EffectorBody<Void> newStopEffectorTask() {
        return new EffectorBody<Void>() {
            @Override
            public Void call(ConfigBag parameters) {
                stop();
                return null;
            }
        };
    }
        
    protected EntityInternal entity() {
        return (EntityInternal) BrooklynTasks.getTargetOrContextEntity(Tasks.current());
    }

    protected Location getLocation(@Nullable Collection<? extends Location> locations) {
        if (locations==null || locations.isEmpty()) locations = entity().getLocations();
        if (locations.isEmpty()) {
            MachineProvisioningLocation<?> provisioner = entity().getAttribute(SoftwareProcess.PROVISIONING_LOCATION);
            if (provisioner!=null) locations = Arrays.<Location>asList(provisioner);
        }
        if (locations.size() != 1 || Iterables.getOnlyElement(locations)==null)
            throw new IllegalArgumentException("Expected one non-null location when starting "+entity()+", but given "+locations);
        return Iterables.getOnlyElement(locations);
    }
    
    // ---------------------
    
    /** runs the tasks needed to start, wrapped by setting {@link Attributes#SERVICE_STATE} appropriately */ 
    protected void start(Collection<? extends Location> locations) {
        entity().setAttribute(Attributes.SERVICE_STATE, Lifecycle.STARTING);
        try {
            startInLocations(locations);
            DynamicTasks.waitForLast();
            if (entity().getAttribute(Attributes.SERVICE_STATE) == Lifecycle.STARTING) 
                entity().setAttribute(Attributes.SERVICE_STATE, Lifecycle.RUNNING);
        } catch (Throwable t) {
            entity().setAttribute(Attributes.SERVICE_STATE, Lifecycle.ON_FIRE);
            throw Exceptions.propagate(t);
        }
    }

    /** asserts there is a single location and calls {@link #startInLocation(Location)} with that location */
    protected void startInLocations(Collection<? extends Location> locations) {
        startInLocation( getLocation(locations) );
    }

    /** dispatches to the appropriate method(s) to start in the given location */
    protected void startInLocation(final Location location) {
        Supplier<MachineLocation> locationS = null;
        if (location instanceof MachineProvisioningLocation) {
            Task<MachineLocation> machineTask = provisionAsync((MachineProvisioningLocation<?>)location);
            locationS = Tasks.supplier(machineTask);
        }
        if (location instanceof MachineLocation) {
            locationS = Suppliers.ofInstance((MachineLocation)location);
        }
        if (locationS==null)
            throw new IllegalArgumentException("Unsupported location "+location+", when starting "+entity());
        
        final Supplier<MachineLocation> locationSF = locationS;
        preStartAtMachineAsync(locationSF);
        DynamicTasks.queue("start (processes)", new Runnable() { public void run() {
            startProcessesAtMachine(locationSF);
        }});
        postStartAtMachineAsync();
        return;
    }

    /** returns a queued _task_ which provisions a machine in the given location 
     * and returns that machine, so the task can be used as a supplier to subsequent methods */
    protected Task<MachineLocation> provisionAsync(final MachineProvisioningLocation<?> location) {
        return DynamicTasks.queue(Tasks.<MachineLocation>builder().name("provisioning ("+location.getDisplayName()+")").body(
                new Callable<MachineLocation>() {
                    public MachineLocation call() throws Exception {
                        final Map<String,Object> flags = obtainProvisioningFlags(location);
                        if (!(location instanceof LocalhostMachineProvisioningLocation))
                            log.info("Starting {}, obtaining a new location instance in {} with ports {}", new Object[] {this, location, flags.get("inboundPorts")});
                        entity().setAttribute(SoftwareProcess.PROVISIONING_LOCATION, location);
                        MachineLocation machine;
                        try {
                            machine = Tasks.withBlockingDetails("Provisioning machine in "+location, new Callable<MachineLocation>() {
                                public MachineLocation call() throws NoMachinesAvailableException {
                                    return location.obtain(flags);
                                }});
                            if (machine == null) throw new NoMachinesAvailableException("Failed to obtain machine in "+location.toString());
                        } catch (Exception e) {
                            throw Exceptions.propagate(e);
                        }
                        
                        if (log.isDebugEnabled())
                            log.debug("While starting {}, obtained new location instance {}", this, 
                                    (machine instanceof SshMachineLocation ? 
                                            machine+", details "+((SshMachineLocation)machine).getUser()+":"+Entities.sanitize(((SshMachineLocation)machine).getAllConfig(false)) 
                                            : machine));
                        if (!(location instanceof LocalhostMachineProvisioningLocation))
                            log.info("While starting {}, obtained a new location instance {}, now preparing process there", this, machine);
                        return machine;
                    }
                }).build());
    }

    /** wraps a call to {@link #preStartCustom(MachineLocation)}, after setting the hostname and address */
    protected void preStartAtMachineAsync(final Supplier<MachineLocation> machineS) {
        DynamicTasks.queue("pre-start", new Runnable() { public void run() {
            MachineLocation machine = machineS.get();
            log.info("Starting {} on machine {}", this, machine);
            entity().addLocations(ImmutableList.of((Location)machine));

            // 20 Aug 2013: previously we set these fields _after_ calling the preStart hooks,
            // but this feels more useful (TBC)
            
            if (entity().getAttribute(Attributes.HOSTNAME)==null)
                entity().setAttribute(Attributes.HOSTNAME, machine.getAddress().getHostName());
            if (entity().getAttribute(Attributes.ADDRESS)==null)
                entity().setAttribute(Attributes.ADDRESS, machine.getAddress().getHostAddress());
            
            preStartCustom(machine);
        }});
    }
        
    /** default pre-start hooks, can be extended by subclasses if needed*/
    protected void preStartCustom(MachineLocation machine) {
        ConfigToAttributes.apply(entity());

        // Opportunity to block startup until other dependent components are available
        Object val = entity().getConfig(SoftwareProcess.START_LATCH);
        if (val != null) log.debug("{} finished waiting for start-latch; continuing...", this, val);
    }

    protected Map<String, Object> obtainProvisioningFlags(final MachineProvisioningLocation<?> location) {
        if (entity() instanceof ProvidesProvisioningFlags) {
            return ((ProvidesProvisioningFlags)entity()).obtainProvisioningFlags(location).getAllConfig();
        }
        return MutableMap.<String, Object>of();
    }

    protected abstract String startProcessesAtMachine(final Supplier<MachineLocation> machineS);
    
    protected void postStartAtMachineAsync() {
        DynamicTasks.queue("post-start", new Runnable() { public void run() {
            postStartCustom();
        }});
    }

    /** default post-start hooks, can be extended by subclasses, and typically will do to wait for confirmation of start */
    protected void postStartCustom() {
        // nothing by default
    }
    
    // ---------------------
    
    /** default restart impl, stops processes if possible, then starts the entity again */
    protected void restart() {
        DynamicTasks.queue("stopping (process)", new Callable<String>() { public String call() {
            try {
                stopProcessesAtMachine();
                DynamicTasks.waitForLast();
            } catch (Exception e) {
                String msg = "Could not stop "+entity()+" (process) when restarting: "+e;
                log.debug(msg);
                return msg;
            }
            return "Stop of process completed with no errors.";
        }});
        
        DynamicTasks.queue("starting", new Runnable() { public void run() {
            // startInLocations will look up the location, and provision a machine if necessary
            // (if it remembered the provisioning location)
            startInLocations(null);
        }});
    }

    // ---------------------

    /** default stop impl, aborts if already stopped, otherwise sets state STOPPING then
     * invokes {@link #preStopCustom()}, {@link #stopProcessesAtMachine()}, then finally
     * {@link #stopAnyProvisionedMachines()} and sets state STOPPED */
    protected void stop() {
        log.info("Stopping {} in {}", entity(), entity().getLocations());
        
        DynamicTasks.queue("pre-stop", new Callable<String>() { public String call() {
            if (entity().getAttribute(SoftwareProcess.SERVICE_STATE)==Lifecycle.STOPPED) {
                log.debug("Skipping stop of entity "+entity()+" when already stopped");
                return "Already stopped";
            }
            entity().setAttribute(SoftwareProcess.SERVICE_STATE, Lifecycle.STOPPING);
            entity().setAttribute(SoftwareProcess.SERVICE_UP, false);
            preStopCustom();
            return null;
        }});
        
        if (entity().getAttribute(SoftwareProcess.SERVICE_STATE)==Lifecycle.STOPPED) {
            return;
        }
                
        Task<Object> stoppingProcess = DynamicTasks.queue("stopping (process)", new Callable<Object>() { public Object call() {
            try {
                stopProcessesAtMachine();
                DynamicTasks.waitForLast();
            } catch (Throwable error) {
                String msg = "Error stopping "+entity()+" (process): "+error;
                log.warn(msg);
                return error;
            }
            return "Stop at machine completed with no errors.";
        }});
        
        // Release this machine (even if error trying to stop process - we rethrow that after)
        DynamicTasks.queue("stopping (machine)", new Callable<String>() { public String call() {
            if (entity().getAttribute(SoftwareProcess.SERVICE_STATE)==Lifecycle.STOPPED) {
                log.debug("Skipping stop of entity "+entity()+" when already stopped");
                return "Already stopped";
            }
            return stopAnyProvisionedMachines();
        }});
        
        DynamicTasks.waitForLast();
        
        if (stoppingProcess.getUnchecked() instanceof Throwable)
            throw Exceptions.propagate((Throwable)stoppingProcess.getUnchecked());
        
        entity().setAttribute(SoftwareProcess.SERVICE_UP, false);
        entity().setAttribute(SoftwareProcess.SERVICE_STATE, Lifecycle.STOPPED);

        if (log.isDebugEnabled()) log.debug("Stopped software process entity "+entity());
    }
    
    protected void preStopCustom() {
        // nothing needed here
    }
    
    /** can run synchronously (or not) -- caller will submit/queue as needed, and will block on any submitted tasks. 
     * @return string message of result */
    protected String stopAnyProvisionedMachines() {
        @SuppressWarnings("unchecked")
        MachineProvisioningLocation<MachineLocation> provisioner = entity().getAttribute(SoftwareProcess.PROVISIONING_LOCATION);

        // NB: previously has logic about "removeFirstMachine" but elsewhere had assumptions that there was only one,
        // so i think that was an aborted bit of work (which has been removed here). Alex, Aug 2013
        
        if (Iterables.isEmpty(entity().getLocations())) {
            log.debug("No machine decommissioning necessary for "+entity()+" - no locations");
            return "No machine decommissioning necessary for - no locations";
        }
        
        // Only release this machine if we ourselves provisioned it (e.g. it might be running other services)
        if (provisioner==null) {
            log.debug("No machine decommissioning necessary for "+entity()+" - did not provision");
            return "No machine decommissioning necessary for - did not provision";
        }

        Location machine = getLocation(null);
        if (!(machine instanceof MachineLocation)) {
            log.debug("No decommissioning necessary for "+entity()+" - not a machine location ("+machine+")");
            return "No machine decommissioning necessary for - not a machine ("+machine+")";
        }
        
        try {
            entity().removeLocations(ImmutableList.of(machine));
            entity().setAttribute(SoftwareProcess.HOSTNAME, null);
            entity().setAttribute(SoftwareProcess.ADDRESS, null);
            if (provisioner != null) provisioner.release((MachineLocation)machine);
        } catch (Throwable t) {
            throw Exceptions.propagate(t);
        }
        return "Decommissioned "+machine;
    }

    /** can run synchronously (or not) -- caller will submit/queue as needed, and will block on any submitted tasks. 
     * @return string message of result */
    protected abstract String stopProcessesAtMachine();

}
