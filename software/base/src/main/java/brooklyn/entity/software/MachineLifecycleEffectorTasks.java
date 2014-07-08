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
package brooklyn.entity.software;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Effector;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.BrooklynConfigKeys;
import brooklyn.entity.basic.BrooklynTaskTags;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.EffectorStartableImpl.StartParameters;
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
import brooklyn.location.basic.AbstractLocation;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.location.basic.Locations;
import brooklyn.location.basic.Machines;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.management.Task;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.guava.Maybe;
import brooklyn.util.net.UserAndHostAndPort;
import brooklyn.util.os.Os;
import brooklyn.util.ssh.BashCommands;
import brooklyn.util.task.DynamicTasks;
import brooklyn.util.task.Tasks;
import brooklyn.util.task.system.ProcessTaskWrapper;
import brooklyn.util.text.Strings;
import brooklyn.util.time.Duration;

import com.google.common.annotations.Beta;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

/** 
 * Default skeleton for start/stop/restart tasks on machines.
 * Knows how to provision machines, making use of {@link ProvidesProvisioningFlags#obtainProvisioningFlags(MachineProvisioningLocation)},
 * and provides hooks for injecting behaviour at common places.
 * Methods are designed for overriding, with the convention that *Async methods should queue (and not block).
 * The following methods are commonly overridden (and you can safely queue tasks, block, or return immediately in them):
 * <ul>
 *  <li> {@link #startProcessesAtMachine(Supplier)} (required)
 *  <li> {@link #stopProcessesAtMachine()} (required, but can be left blank if you assume the VM will be destroyed)
 *  <li> {@link #preStartCustom(MachineLocation)}
 *  <li> {@link #postStartCustom()}
 *  <li> {@link #preStopCustom()}
 * </ul>
 * Note methods at this level typically look after the {@link Attributes#SERVICE_STATE} sensor.
 *  
 * @since 0.6.0
 **/
@Beta
public abstract class MachineLifecycleEffectorTasks {

    private static final Logger log = LoggerFactory.getLogger(MachineLifecycleEffectorTasks.class);
    
    public static final ConfigKey<Boolean> ON_BOX_BASE_DIR_RESOLVED = ConfigKeys.newBooleanConfigKey("onbox.base.dir.resolved",
        "Whether the on-box base directory has been resolved (for internal use)");
    
    public static final ConfigKey<Collection<? extends Location>> LOCATIONS = StartParameters.LOCATIONS;
    public static final ConfigKey<Duration> STOP_PROCESS_TIMEOUT = 
        ConfigKeys.newConfigKey(Duration.class, "process.stop.timeout", "How long to wait for the processes to be stopped; "
            + "use null to mean forever", Duration.TWO_MINUTES);

    /** attaches lifecycle effectors (start, restart, stop) to the given entity (post-creation) */ 
    public void attachLifecycleEffectors(Entity entity) {
        ((EntityInternal)entity).getMutableEntityType().addEffector(newStartEffector());
        ((EntityInternal)entity).getMutableEntityType().addEffector(newRestartEffector());
        ((EntityInternal)entity).getMutableEntityType().addEffector(newStopEffector());
    }
    
    /**
     * @return an effector suitable for setting in a public static final or attaching dynamically;
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
     * calling the relevant method in this class ({@link #start(Collection)}) */
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
        return (EntityInternal) BrooklynTaskTags.getTargetOrContextEntity(Tasks.current());
    }

    protected Location getLocation(@Nullable Collection<? extends Location> locations) {
        if (locations==null || locations.isEmpty()) locations = entity().getLocations();
        if (locations.isEmpty()) {
            MachineProvisioningLocation<?> provisioner = entity().getAttribute(SoftwareProcess.PROVISIONING_LOCATION);
            if (provisioner!=null) locations = Arrays.<Location>asList(provisioner);
        }
        locations = Locations.getLocationsCheckingAncestors(locations, entity());

        Maybe<MachineLocation> ml = Locations.findUniqueMachineLocation(locations);
        if (ml.isPresent()) return ml.get();
    
        if (locations.isEmpty())
            throw new IllegalArgumentException("No locations specified when starting "+entity());
        if (locations.size() != 1 || Iterables.getOnlyElement(locations)==null)
            throw new IllegalArgumentException("Ambiguous locations detected when starting "+entity()+": "+locations);
        return Iterables.getOnlyElement(locations);
    }
    
    // ---------------------
    
    /** runs the tasks needed to start, wrapped by setting {@link Attributes#SERVICE_STATE} appropriately */ 
    public void start(Collection<? extends Location> locations) {
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
        } else if (location instanceof MachineLocation) {
            locationS = Suppliers.ofInstance((MachineLocation)location);
        }
        Preconditions.checkState(locationS != null, "Unsupported location "+location+", when starting "+entity());

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
                            log.info("Starting {}, obtaining a new location instance in {} with ports {}", new Object[] {entity(), location, flags.get("inboundPorts")});
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
                            log.debug("While starting {}, obtained new location instance {}", entity(), 
                                    (machine instanceof SshMachineLocation ? 
                                            machine+", details "+((SshMachineLocation)machine).getUser()+":"+Entities.sanitize(((SshMachineLocation)machine).getLocalConfigBag()) 
                                            : machine));
                        return machine;
                    }
                }).build());
    }

    /** wraps a call to {@link #preStartCustom(MachineLocation)}, after setting the hostname and address */
    protected void preStartAtMachineAsync(final Supplier<MachineLocation> machineS) {
        DynamicTasks.queue("pre-start", new Runnable() { public void run() {
            MachineLocation machine = machineS.get();
            log.info("Starting {} on machine {}", entity(), machine);
            Collection<Location> oldLocs = entity().getLocations();
            if (!oldLocs.isEmpty()) {
                List<MachineLocation> oldSshLocs = ImmutableList.copyOf(Iterables.filter(oldLocs, MachineLocation.class));
                if (!oldSshLocs.isEmpty()) {
                    // check if existing locations are compatible
                    log.debug("Entity "+entity()+" had machine locations "+oldSshLocs+" when starting at "+machine+"; checking if they are compatible");
                    for (MachineLocation oldLoc: oldSshLocs) {
                        // machines are deemed compatible if hostname and address are the same, or they are localhost
                        // this allows a machine create by jclouds to then be defined with an ip-based spec
                        if (machine.getConfig(AbstractLocation.ORIGINAL_SPEC)!="localhost") {
                            checkLocationParametersCompatible(machine, oldLoc, "hostname", 
                                oldLoc.getAddress().getHostName(), machine.getAddress().getHostName());
                            checkLocationParametersCompatible(machine, oldLoc, "address", 
                                oldLoc.getAddress().getHostAddress(), machine.getAddress().getHostAddress());
                        }
                    }
                    log.debug("Entity "+entity()+" old machine locations "+oldSshLocs+" were compatible, removing them to start at "+machine);
                    entity().removeLocations(oldSshLocs);
                }
            }
            entity().addLocations(ImmutableList.of((Location)machine));

            // elsewhere we rely on (public) hostname being set _after_ subnet_hostname
            // (to prevent the tiny possibility of races resulting in hostname being returned
            // simply because subnet is still being looked up)
            Maybe<String> lh = Machines.getSubnetHostname(machine);
            Maybe<String> la = Machines.getSubnetIp(machine);
            if (lh.isPresent()) entity().setAttribute(Attributes.SUBNET_HOSTNAME, lh.get());
            if (la.isPresent()) entity().setAttribute(Attributes.SUBNET_ADDRESS, la.get());
            entity().setAttribute(Attributes.HOSTNAME, machine.getAddress().getHostName());
            entity().setAttribute(Attributes.ADDRESS, machine.getAddress().getHostAddress());
            if (machine instanceof SshMachineLocation) {
                SshMachineLocation sshMachine = (SshMachineLocation) machine;
                UserAndHostAndPort sshAddress = UserAndHostAndPort.fromParts(sshMachine.getUser(), sshMachine.getAddress().getHostName(), sshMachine.getPort());
                entity().setAttribute(Attributes.SSH_ADDRESS, sshAddress);
            }
            
            resolveOnBoxDir(entity(), machine);            
            preStartCustom(machine);
        }});
    }

    /** resolves the on-box dir; logs a warning if not */
    // initialize and pre-create the right onbox working dir, if an ssh machine location
    @SuppressWarnings("deprecation")
    public static String resolveOnBoxDir(EntityInternal entity, MachineLocation machine) {
        String base = entity.getConfig(BrooklynConfigKeys.ONBOX_BASE_DIR); 
        if (base==null) base = machine.getConfig(BrooklynConfigKeys.ONBOX_BASE_DIR);
        if (base!=null && Boolean.TRUE.equals(entity.getConfig(ON_BOX_BASE_DIR_RESOLVED))) return base;
        if (base==null) base = entity.getManagementContext().getConfig().getConfig(BrooklynConfigKeys.ONBOX_BASE_DIR);
        if (base==null) base = entity.getConfig(BrooklynConfigKeys.BROOKLYN_DATA_DIR); 
        if (base==null) base = machine.getConfig(BrooklynConfigKeys.BROOKLYN_DATA_DIR);
        if (base==null) base = entity.getManagementContext().getConfig().getConfig(BrooklynConfigKeys.BROOKLYN_DATA_DIR);
        if (base==null) base = "~/brooklyn-managed-processes";
        if (base=="~") base=".";
        if (base.startsWith("~/")) base = "."+base.substring(1);
        
        String resolvedBase = null;
        if (entity.getConfig(BrooklynConfigKeys.SKIP_ON_BOX_BASE_DIR_RESOLUTION) || machine.getConfig(BrooklynConfigKeys.SKIP_ON_BOX_BASE_DIR_RESOLUTION)) {
            if (log.isDebugEnabled()) log.debug("Skipping on-box base dir resolution for "+entity+" at "+machine);
            if (!Os.isAbsolutish(base)) base = "~/"+base;
            resolvedBase = Os.tidyPath(base);
        } else if (machine instanceof SshMachineLocation) {
            SshMachineLocation ms = (SshMachineLocation)machine;
            ProcessTaskWrapper<Integer> baseTask = SshEffectorTasks.ssh(
                BashCommands.alternatives("mkdir -p \"${BASE_DIR}\"",
                    BashCommands.chain(
                        BashCommands.sudo("mkdir -p \"${BASE_DIR}\""),
                        BashCommands.sudo("chown "+ms.getUser()+" \"${BASE_DIR}\""))),
                "cd ~",
                "cd ${BASE_DIR}",
                "echo BASE_DIR_RESULT':'`pwd`:BASE_DIR_RESULT")
                .environmentVariable("BASE_DIR", base)
                .requiringExitCodeZero()
                .summary("initializing on-box base dir "+base).newTask();
            DynamicTasks.queueIfPossible(baseTask).orSubmitAsync(entity);
            resolvedBase = Strings.getFragmentBetween(baseTask.block().getStdout(), "BASE_DIR_RESULT:", ":BASE_DIR_RESULT");
        }
        if (resolvedBase==null) {
            if (!Os.isAbsolutish(base)) base = "~/"+base;
            resolvedBase = Os.tidyPath(base);
            log.warn("Could not resolve on-box directory for "+entity+" at "+machine+"; using "+resolvedBase+", though this may not be accurate at the target (and may fail shortly)");
        }
        entity.setConfig(BrooklynConfigKeys.ONBOX_BASE_DIR, resolvedBase);
        entity.setConfig(ON_BOX_BASE_DIR_RESOLVED, true);
        return resolvedBase;
    }
    
    protected void checkLocationParametersCompatible(MachineLocation oldLoc, MachineLocation newLoc, String paramSummary,
        Object oldParam, Object newParam) {
        if (oldParam==null || newParam==null || !oldParam.equals(newParam))
            throw new IllegalStateException("Cannot start "+entity()+" in "+newLoc+" as it has already been started with incompatible location "+oldLoc+" "
                + "("+paramSummary+" not compatible: "+oldParam+" / "+newParam+"); "
                + newLoc+" may require manual removal.");
    }

    /** default pre-start hooks, can be extended by subclasses if needed*/
    protected void preStartCustom(MachineLocation machine) {
        ConfigToAttributes.apply(entity());

        // Opportunity to block startup until other dependent components are available
        Object val = entity().getConfig(SoftwareProcess.START_LATCH);
        if (val != null) log.debug("{} finished waiting for start-latch; continuing...", entity(), val);
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

    /** default post-start hooks, can be extended by subclasses, and typically will wait for confirmation of start 
     * (the service not set to running until after this); also invoked following a restart */
    protected void postStartCustom() {
        // nothing by default
    }

    // ---------------------
    
    /** default restart impl, stops processes if possible, then starts the entity again */
    public void restart() {
        entity().setAttribute(Attributes.SERVICE_STATE, Lifecycle.STOPPING);
        DynamicTasks.queue("stopping (process)", new Callable<String>() { public String call() {
            DynamicTasks.markInessential();
            stopProcessesAtMachine();
            DynamicTasks.waitForLast();
            return "Stop of process completed with no errors.";
        }});
        
        DynamicTasks.queue("starting", new Runnable() { public void run() {
            // startInLocations will look up the location, and provision a machine if necessary
            // (if it remembered the provisioning location)
            entity().setAttribute(Attributes.SERVICE_STATE, Lifecycle.STARTING);
            startInLocations(null);
            DynamicTasks.waitForLast();
            if (entity().getAttribute(Attributes.SERVICE_STATE) == Lifecycle.STARTING) 
                entity().setAttribute(Attributes.SERVICE_STATE, Lifecycle.RUNNING);
        }});
    }

    // ---------------------

    /** default stop impl, aborts if already stopped, otherwise sets state STOPPING then
     * invokes {@link #preStopCustom()}, {@link #stopProcessesAtMachine()}, then finally
     * {@link #stopAnyProvisionedMachines()} and sets state STOPPED */
    public void stop() {
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
               
        Maybe<SshMachineLocation> sshMachine = Machines.findUniqueSshMachineLocation(entity().getLocations());
        Task<String> stoppingProcess = DynamicTasks.queue("stopping (process)", new Callable<String>() { public String call() {
            DynamicTasks.markInessential();
            stopProcessesAtMachine();
            DynamicTasks.waitForLast();
            return "Stop at machine completed with no errors.";
        }});
        
        // Release this machine (even if error trying to stop process - we rethrow that after)
        Task<StopMachineDetails<Integer>> stoppingMachine = DynamicTasks.queue("stopping (machine)", new Callable<StopMachineDetails<Integer>>() { public StopMachineDetails<Integer> call() {
            if (entity().getAttribute(SoftwareProcess.SERVICE_STATE)==Lifecycle.STOPPED) {
                log.debug("Skipping stop of entity "+entity()+" when already stopped");
                return new StopMachineDetails<Integer>("Already stopped", 0);
            }
            return stopAnyProvisionedMachines();
        }});

        DynamicTasks.drain(entity().getConfig(STOP_PROCESS_TIMEOUT), false);
        
        // shutdown the machine if stopping process fails or takes too long
        synchronized (stoppingMachine) {
            // task also used as mutex by DST when it submits it; ensure it only submits once!
            if (!stoppingMachine.isSubmitted()) {
                // force the stoppingMachine task to run by submitting it here
                log.warn("Submitting machine stop early in background for "+entity()+" because process stop has "+
                    (stoppingProcess.isDone() ? "finished abnormally" : "not finished"));
                Entities.submit(entity(), stoppingMachine);
            }
        }
        
        try {
            if (stoppingMachine.get().value==0) {
                // TODO we should test for destruction above, not merely successful "stop", as things like localhost and ssh won't be destroyed
                DynamicTasks.waitForLast();
                if (sshMachine.isPresent())
                    // throw early errors *only if* there is a machine and we have not destroyed it
                    stoppingProcess.get();
            }
            
            entity().setAttribute(SoftwareProcess.SERVICE_UP, false);
            entity().setAttribute(SoftwareProcess.SERVICE_STATE, Lifecycle.STOPPED);
        } catch (Throwable e) {
            entity().setAttribute(SoftwareProcess.SERVICE_STATE, Lifecycle.ON_FIRE);
            Exceptions.propagate(e);
        }
        
        if (log.isDebugEnabled()) log.debug("Stopped software process entity "+entity());
    }
    
    protected void preStopCustom() {
        // nothing needed here
    }
    
    public static class StopMachineDetails<T> implements Serializable {
        private static final long serialVersionUID = 3256747214315895431L;
        final String message;
        final T value;
        protected StopMachineDetails(String message, T value) {
            this.message = message;
            this.value = value;
        }
        @Override
        public String toString() {
            return message;
        }
    }
    
    /** can run synchronously (or not) -- caller will submit/queue as needed, and will block on any submitted tasks. */
    protected StopMachineDetails<Integer> stopAnyProvisionedMachines() {
        @SuppressWarnings("unchecked")
        MachineProvisioningLocation<MachineLocation> provisioner = entity().getAttribute(SoftwareProcess.PROVISIONING_LOCATION);

        // NB: previously has logic about "removeFirstMachine" but elsewhere had assumptions that there was only one,
        // so i think that was an aborted bit of work (which has been removed here). Alex, Aug 2013
        
        if (Iterables.isEmpty(entity().getLocations())) {
            log.debug("No machine decommissioning necessary for "+entity()+" - no locations");
            return new StopMachineDetails<Integer>("No machine decommissioning necessary - no locations", 0);
        }
        
        // Only release this machine if we ourselves provisioned it (e.g. it might be running other services)
        if (provisioner==null) {
            log.debug("No machine decommissioning necessary for "+entity()+" - did not provision");
            return new StopMachineDetails<Integer>("No machine decommissioning necessary - did not provision", 0);
        }

        Location machine = getLocation(null);
        if (!(machine instanceof MachineLocation)) {
            log.debug("No decommissioning necessary for "+entity()+" - not a machine location ("+machine+")");
            return new StopMachineDetails<Integer>("No machine decommissioning necessary - not a machine ("+machine+")", 0);
        }
        
        try {
            entity().removeLocations(ImmutableList.of(machine));
            entity().setAttribute(Attributes.HOSTNAME, null);
            entity().setAttribute(Attributes.ADDRESS, null);
            entity().setAttribute(Attributes.SUBNET_HOSTNAME, null);
            entity().setAttribute(Attributes.SUBNET_ADDRESS, null);
            if (provisioner != null) provisioner.release((MachineLocation)machine);
        } catch (Throwable t) {
            throw Exceptions.propagate(t);
        }
        return new StopMachineDetails<Integer>("Decommissioned "+machine, 1);
    }

    /** can run synchronously (or not) -- caller will submit/queue as needed, and will block on any submitted tasks. 
     * @return string message of result */
    protected abstract String stopProcessesAtMachine();

}
