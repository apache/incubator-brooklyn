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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.Beta;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

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
import brooklyn.entity.basic.Sanitizer;
import brooklyn.entity.basic.ServiceStateLogic;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.basic.SoftwareProcess.RestartSoftwareParameters;
import brooklyn.entity.basic.SoftwareProcess.RestartSoftwareParameters.RestartMachineMode;
import brooklyn.entity.basic.SoftwareProcess.StopSoftwareParameters;
import brooklyn.entity.basic.SoftwareProcess.StopSoftwareParameters.StopMode;
import brooklyn.entity.effector.EffectorBody;
import brooklyn.entity.effector.Effectors;
import brooklyn.entity.trait.Startable;
import brooklyn.entity.trait.StartableMethods;
import brooklyn.event.feed.ConfigToAttributes;
import brooklyn.location.Location;
import brooklyn.location.MachineLocation;
import brooklyn.location.MachineManagementMixins.SuspendsMachines;
import brooklyn.location.MachineProvisioningLocation;
import brooklyn.location.NoMachinesAvailableException;
import brooklyn.location.basic.AbstractLocation;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.location.basic.Locations;
import brooklyn.location.basic.Machines;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.location.cloud.CloudLocationConfig;
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

/**
 * Default skeleton for start/stop/restart tasks on machines.
 * <p>
 * Knows how to provision machines, making use of {@link ProvidesProvisioningFlags#obtainProvisioningFlags(MachineProvisioningLocation)},
 * and provides hooks for injecting behaviour at common places.
 * <p>
 * Methods are designed for overriding, with the convention that *Async methods should queue (and not block).
 * The following methods are commonly overridden (and you can safely queue tasks, block, or return immediately in them):
 * <ul>
 *  <li> {@link #startProcessesAtMachine(Supplier)} (required)
 *  <li> {@link #stopProcessesAtMachine()} (required, but can be left blank if you assume the VM will be destroyed)
 *  <li> {@link #preStartCustom(MachineLocation)}
 *  <li> {@link #postStartCustom()}
 *  <li> {@link #preStopCustom()}
 *  <li> {@link #postStopCustom()}
 * </ul>
 * Note methods at this level typically look after the {@link Attributes#SERVICE_STATE} sensor.
 *
 * @since 0.6.0
 */
@Beta
public abstract class MachineLifecycleEffectorTasks {

    private static final Logger log = LoggerFactory.getLogger(MachineLifecycleEffectorTasks.class);

    public static final ConfigKey<Boolean> ON_BOX_BASE_DIR_RESOLVED = ConfigKeys.newBooleanConfigKey("onbox.base.dir.resolved",
        "Whether the on-box base directory has been resolved (for internal use)");

    public static final ConfigKey<Collection<? extends Location>> LOCATIONS = StartParameters.LOCATIONS;
    public static final ConfigKey<Duration> STOP_PROCESS_TIMEOUT = ConfigKeys.newConfigKey(Duration.class,
            "process.stop.timeout", "How long to wait for the processes to be stopped; use null to mean forever", Duration.TWO_MINUTES);

    protected final MachineInitTasks machineInitTasks = new MachineInitTasks();
    
    /** Attaches lifecycle effectors (start, restart, stop) to the given entity post-creation. */
    public void attachLifecycleEffectors(Entity entity) {
        ((EntityInternal) entity).getMutableEntityType().addEffector(newStartEffector());
        ((EntityInternal) entity).getMutableEntityType().addEffector(newRestartEffector());
        ((EntityInternal) entity).getMutableEntityType().addEffector(newStopEffector());
    }

    /**
     * Return an effector suitable for setting in a {@code public static final} or attaching dynamically.
     * <p>
     * The effector overrides the corresponding effector from {@link Startable} with
     * the behaviour in this lifecycle class instance.
     */
    public Effector<Void> newStartEffector() {
        return Effectors.effector(Startable.START).impl(newStartEffectorTask()).build();
    }

    /** @see {@link #newStartEffector()} */
    public Effector<Void> newRestartEffector() {
        return Effectors.effector(Startable.RESTART)
                .parameter(RestartSoftwareParameters.RESTART_CHILDREN)
                .parameter(RestartSoftwareParameters.RESTART_MACHINE)
                .impl(newRestartEffectorTask())
                .build();
    }
    
    /** @see {@link #newStartEffector()} */
    public Effector<Void> newStopEffector() {
        return Effectors.effector(Startable.STOP)
                .parameter(StopSoftwareParameters.STOP_PROCESS_MODE)
                .parameter(StopSoftwareParameters.STOP_MACHINE_MODE)
                .impl(newStopEffectorTask())
                .build();
    }

    /** @see {@link #newStartEffector()} */
    public Effector<Void> newSuspendEffector() {
        return Effectors.effector(Void.class, "suspend")
                .description("Suspend the process/service represented by an entity")
                .parameter(StopSoftwareParameters.STOP_PROCESS_MODE)
                .parameter(StopSoftwareParameters.STOP_MACHINE_MODE)
                .impl(newSuspendEffectorTask())
                .build();
    }

    /**
     * Returns the {@link EffectorBody} which supplies the implementation for the start effector.
     * <p>
     * Calls {@link #start(Collection)} in this class.
     */
    public EffectorBody<Void> newStartEffectorTask() {
        // TODO included anonymous inner class for backwards compatibility with persisted state.
        new EffectorBody<Void>() {
            @Override
            public Void call(ConfigBag parameters) {
                Collection<? extends Location> locations  = null;

                Object locationsRaw = parameters.getStringKey(LOCATIONS.getName());
                locations = Locations.coerceToCollection(entity().getManagementContext(), locationsRaw);

                if (locations==null) {
                    // null/empty will mean to inherit from parent
                    locations = Collections.emptyList();
                }

                start(locations);
                return null;
            }
        };
        return new StartEffectorBody();
    }

    private class StartEffectorBody extends EffectorBody<Void> {
        @Override
        public Void call(ConfigBag parameters) {
            Collection<? extends Location> locations = null;

            Object locationsRaw = parameters.getStringKey(LOCATIONS.getName());
            locations = Locations.coerceToCollection(entity().getManagementContext(), locationsRaw);

            if (locations == null) {
                // null/empty will mean to inherit from parent
                locations = Collections.emptyList();
            }

            start(locations);
            return null;
        }

    }

    /**
     * Calls {@link #restart(ConfigBag)}.
     *
     * @see {@link #newStartEffectorTask()}
     */
    public EffectorBody<Void> newRestartEffectorTask() {
        // TODO included anonymous inner class for backwards compatibility with persisted state.
        new EffectorBody<Void>() {
            @Override
            public Void call(ConfigBag parameters) {
                restart(parameters);
                return null;
            }
        };
        return new RestartEffectorBody();
    }

    private class RestartEffectorBody extends EffectorBody<Void> {
        @Override
        public Void call(ConfigBag parameters) {
            restart(parameters);
            return null;
        }
    }

    /**
     * Calls {@link #stop(ConfigBag)}.
     *
     * @see {@link #newStartEffectorTask()}
     */
    public EffectorBody<Void> newStopEffectorTask() {
        // TODO included anonymous inner class for backwards compatibility with persisted state.
        new EffectorBody<Void>() {
            @Override
            public Void call(ConfigBag parameters) {
                stop(parameters);
                return null;
            }
        };
        return new StopEffectorBody();
    }

    private class StopEffectorBody extends EffectorBody<Void> {
        @Override
        public Void call(ConfigBag parameters) {
            stop(parameters);
            return null;
        }
    }

    /**
     * Calls {@link #suspend(ConfigBag)}.
     *
     * @see {@link #newStartEffectorTask()}
     */
    public EffectorBody<Void> newSuspendEffectorTask() {
        return new SuspendEffectorBody();
    }

    private class SuspendEffectorBody extends EffectorBody<Void> {
        @Override
        public Void call(ConfigBag parameters) {
            suspend(parameters);
            return null;
        }
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
    
    /** runs the tasks needed to start, wrapped by setting {@link Attributes#SERVICE_STATE_EXPECTED} appropriately */ 
    public void start(Collection<? extends Location> locations) {
        ServiceStateLogic.setExpectedState(entity(), Lifecycle.STARTING);
        try {
            startInLocations(locations);
            DynamicTasks.waitForLast();
            ServiceStateLogic.setExpectedState(entity(), Lifecycle.RUNNING);
        } catch (Throwable t) {
            ServiceStateLogic.setExpectedState(entity(), Lifecycle.ON_FIRE);
            throw Exceptions.propagate(t);
        }
    }

    /** Asserts there is a single location and calls {@link #startInLocation(Location)} with that location. */
    protected void startInLocations(Collection<? extends Location> locations) {
        startInLocation(getLocation(locations));
    }

    /** Dispatches to the appropriate method(s) to start in the given location. */
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
        DynamicTasks.queue("start (processes)", new StartProcessesAtMachineTask(locationSF));
        postStartAtMachineAsync();
    }

    private class StartProcessesAtMachineTask implements Runnable {
        private final Supplier<MachineLocation> machineSupplier;
        private StartProcessesAtMachineTask(Supplier<MachineLocation> machineSupplier) {
            this.machineSupplier = machineSupplier;
        }
        @Override
        public void run() {
            startProcessesAtMachine(machineSupplier);
        }
    }

    /**
     * Returns a queued {@link Task} which provisions a machine in the given location
     * and returns that machine. The task can be used as a supplier to subsequent methods.
     */
    protected Task<MachineLocation> provisionAsync(final MachineProvisioningLocation<?> location) {
        return DynamicTasks.queue(Tasks.<MachineLocation>builder().name("provisioning (" + location.getDisplayName() + ")").body(
                new ProvisionMachineTask(location)).build());
    }

    private class ProvisionMachineTask implements Callable<MachineLocation> {
        final MachineProvisioningLocation<?> location;

        private ProvisionMachineTask(MachineProvisioningLocation<?> location) {
            this.location = location;
        }

        public MachineLocation call() throws Exception {
            // Blocks if a latch was configured.
            entity().getConfig(BrooklynConfigKeys.PROVISION_LATCH);
            final Map<String, Object> flags = obtainProvisioningFlags(location);
            if (!(location instanceof LocalhostMachineProvisioningLocation))
                log.info("Starting {}, obtaining a new location instance in {} with ports {}", new Object[]{entity(), location, flags.get("inboundPorts")});
            entity().setAttribute(SoftwareProcess.PROVISIONING_LOCATION, location);
            MachineLocation machine;
            try {
                machine = Tasks.withBlockingDetails("Provisioning machine in " + location, new ObtainLocationTask(location, flags));
                if (machine == null)
                    throw new NoMachinesAvailableException("Failed to obtain machine in " + location.toString());
            } catch (Exception e) {
                throw Exceptions.propagate(e);
            }

            if (log.isDebugEnabled())
                log.debug("While starting {}, obtained new location instance {}", entity(),
                        (machine instanceof SshMachineLocation ?
                         machine + ", details " + ((SshMachineLocation) machine).getUser() + ":" + Sanitizer.sanitize(((SshMachineLocation) machine).config().getLocalBag())
                                                               : machine));
            return machine;
        }
    }

    private static class ObtainLocationTask implements Callable<MachineLocation> {
        final MachineProvisioningLocation<?> location;
        final Map<String, Object> flags;

        private ObtainLocationTask(MachineProvisioningLocation<?> location, Map<String, Object> flags) {
            this.flags = flags;
            this.location = location;
        }

        public MachineLocation call() throws NoMachinesAvailableException {
            return location.obtain(flags);
        }
    }

    /** Wraps a call to {@link #preStartCustom(MachineLocation)}, after setting the hostname and address. */
    protected void preStartAtMachineAsync(final Supplier<MachineLocation> machineS) {
        DynamicTasks.queue("pre-start", new PreStartTask(machineS.get()));
    }

    private class PreStartTask implements Runnable {
        final MachineLocation machine;
        private PreStartTask(MachineLocation machine) {
            this.machine = machine;
        }
        public void run() {
            log.info("Starting {} on machine {}", entity(), machine);
            Collection<Location> oldLocs = entity().getLocations();
            if (!oldLocs.isEmpty()) {
                List<MachineLocation> oldSshLocs = ImmutableList.copyOf(Iterables.filter(oldLocs, MachineLocation.class));
                if (!oldSshLocs.isEmpty()) {
                    // check if existing locations are compatible
                    log.debug("Entity " + entity() + " had machine locations " + oldSshLocs + " when starting at " + machine + "; checking if they are compatible");
                    for (MachineLocation oldLoc : oldSshLocs) {
                        // machines are deemed compatible if hostname and address are the same, or they are localhost
                        // this allows a machine create by jclouds to then be defined with an ip-based spec
                        if (!"localhost".equals(machine.getConfig(AbstractLocation.ORIGINAL_SPEC))) {
                            checkLocationParametersCompatible(machine, oldLoc, "hostname",
                                    oldLoc.getAddress().getHostName(), machine.getAddress().getHostName());
                            checkLocationParametersCompatible(machine, oldLoc, "address",
                                    oldLoc.getAddress().getHostAddress(), machine.getAddress().getHostAddress());
                        }
                    }
                    log.debug("Entity " + entity() + " old machine locations " + oldSshLocs + " were compatible, removing them to start at " + machine);
                    entity().removeLocations(oldSshLocs);
                }
            }
            entity().addLocations(ImmutableList.of((Location) machine));

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
                @SuppressWarnings("resource")
                SshMachineLocation sshMachine = (SshMachineLocation) machine;
                UserAndHostAndPort sshAddress = UserAndHostAndPort.fromParts(sshMachine.getUser(), sshMachine.getAddress().getHostName(), sshMachine.getPort());
                entity().setAttribute(Attributes.SSH_ADDRESS, sshAddress);
            }

            if (Boolean.TRUE.equals(entity().getConfig(SoftwareProcess.OPEN_IPTABLES))) {
                if (machine instanceof SshMachineLocation) {
                    Iterable<Integer> inboundPorts = (Iterable<Integer>) machine.config().get(CloudLocationConfig.INBOUND_PORTS);
                    machineInitTasks.openIptablesAsync(inboundPorts, (SshMachineLocation)machine);
                } else {
                    log.warn("Ignoring flag OPEN_IPTABLES on non-ssh location {}", machine);
                }
            }
            if (Boolean.TRUE.equals(entity().getConfig(SoftwareProcess.STOP_IPTABLES))) {
                if (machine instanceof SshMachineLocation) {
                    machineInitTasks.stopIptablesAsync((SshMachineLocation)machine);
                } else {
                    log.warn("Ignoring flag STOP_IPTABLES on non-ssh location {}", machine);
                }
            }
            if (Boolean.TRUE.equals(entity().getConfig(SoftwareProcess.DONT_REQUIRE_TTY_FOR_SUDO))) {
                if (machine instanceof SshMachineLocation) {
                    machineInitTasks.dontRequireTtyForSudoAsync((SshMachineLocation)machine);
                } else {
                    log.warn("Ignoring flag DONT_REQUIRE_TTY_FOR_SUDO on non-ssh location {}", machine);
                }
            }
            resolveOnBoxDir(entity(), machine);
            preStartCustom(machine);
        }
    }

    /**
     * Resolves the on-box dir.
     * <p>
     * Initialize and pre-create the right onbox working dir, if an ssh machine location.
     * Logs a warning if not.
     */
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
        if (base.equals("~")) base=".";
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
            throw new IllegalStateException("Cannot start "+entity()+" in "+newLoc+" as it has already been started with incompatible location "+oldLoc+" " +
                    "("+paramSummary+" not compatible: "+oldParam+" / "+newParam+"); "+newLoc+" may require manual removal.");
    }

    /**
     * Default pre-start hooks.
     * <p>
     * Can be extended by subclasses if needed.
     */
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
        DynamicTasks.queue("post-start", new PostStartTask());
    }

    private class PostStartTask implements Runnable {
        public void run() {
            postStartCustom();
        }
    }

    /**
     * Default post-start hooks.
     * <p>
     * Can be extended by subclasses, and typically will wait for confirmation of start.
     * The service not set to running until after this. Also invoked following a restart.
     */
    protected void postStartCustom() {
        // nothing by default
    }

    /** @deprecated since 0.7.0 use {@link #restart(ConfigBag)} */
    @Deprecated
    public void restart() {
        restart(ConfigBag.EMPTY);
    }

    /**
     * whether when 'auto' mode is specified, the machine should be stopped when the restart effector is called
     * <p>
     * with {@link MachineLifecycleEffectorTasks}, a machine will always get created on restart if there wasn't one already
     * (unlike certain subclasses which might attempt a shortcut process-level restart)
     * so there is no reason for default behaviour of restart to throw away a provisioned machine,
     * hence default impl returns <code>false</code>.
     * <p>
     * if it is possible to tell that a machine is unhealthy, or if {@link #restart(ConfigBag)} is overridden,
     * then it might be appropriate to return <code>true</code> here.
     */
    protected boolean getDefaultRestartStopsMachine() {
        return false;
    }

    /**
     * Default restart implementation for an entity.
     * <p>
     * Stops processes if possible, then starts the entity again.
     */
    public void restart(ConfigBag parameters) {
        ServiceStateLogic.setExpectedState(entity(), Lifecycle.STOPPING);

        RestartMachineMode isRestartMachine = parameters.get(RestartSoftwareParameters.RESTART_MACHINE_TYPED);
        if (isRestartMachine==null)
            isRestartMachine=RestartMachineMode.AUTO;
        if (isRestartMachine==RestartMachineMode.AUTO)
            isRestartMachine = getDefaultRestartStopsMachine() ? RestartMachineMode.TRUE : RestartMachineMode.FALSE;

        // Calling preStopCustom without a corresponding postStopCustom invocation
        // doesn't look right so use a separate callback pair; Also depending on the arguments
        // stop() could be called which will call the {pre,post}StopCustom on its own.
        DynamicTasks.queue("pre-restart", new PreRestartTask());

        if (isRestartMachine==RestartMachineMode.FALSE) {
            DynamicTasks.queue("stopping (process)", new StopProcessesAtMachineTask());
        } else {
            DynamicTasks.queue("stopping (machine)", new StopMachineTask());
        }

        DynamicTasks.queue("starting", new StartInLocationsTask());
        restartChildren(parameters);
        DynamicTasks.queue("post-restart", new PostRestartTask());

        DynamicTasks.waitForLast();
        ServiceStateLogic.setExpectedState(entity(), Lifecycle.RUNNING);
    }

    private class PreRestartTask implements Runnable {
        @Override
        public void run() {
            preRestartCustom();
        }
    }
    private class PostRestartTask implements Runnable {
        @Override
        public void run() {
            postRestartCustom();
        }
    }
    private class StartInLocationsTask implements Runnable {
        @Override
        public void run() {
            // startInLocations will look up the location, and provision a machine if necessary
            // (if it remembered the provisioning location)
            ServiceStateLogic.setExpectedState(entity(), Lifecycle.STARTING);
            startInLocations(null);
        }
    }

    protected void restartChildren(ConfigBag parameters) {
        // TODO should we consult ChildStartableMode?

        Boolean isRestartChildren = parameters.get(RestartSoftwareParameters.RESTART_CHILDREN);
        if (isRestartChildren==null || !isRestartChildren) {
            return;
        }

        if (isRestartChildren) {
            DynamicTasks.queue(StartableMethods.restartingChildren(entity(), parameters));
            return;
        }

        throw new IllegalArgumentException("Invalid value '"+isRestartChildren+"' for "+RestartSoftwareParameters.RESTART_CHILDREN.getName());
    }

    /** @deprecated since 0.7.0 use {@link #stop(ConfigBag)} */
    @Deprecated
    public void stop() {
        stop(ConfigBag.EMPTY);
    }

    /**
     * Default stop implementation for an entity.
     * <p>
     * Aborts if already stopped, otherwise sets state {@link Lifecycle#STOPPING} then
     * invokes {@link #preStopCustom()}, {@link #stopProcessesAtMachine()}, then finally
     * {@link #stopAnyProvisionedMachines()} and sets state {@link Lifecycle#STOPPED}.
     * If no errors were encountered call {@link #postStopCustom()} at the end.
     */
    public void stop(ConfigBag parameters) {
        doStop(parameters, new StopAnyProvisionedMachinesTask());
    }

    /**
     * As {@link #stop} but calling {@link #suspendAnyProvisionedMachines} rather than
     * {@link #stopAnyProvisionedMachines}.
     */
    public void suspend(ConfigBag parameters) {
        doStop(parameters, new SuspendAnyProvisionedMachinesTask());
    }

    protected void doStop(ConfigBag parameters, Callable<StopMachineDetails<Integer>> stopTask) {
        preStopConfirmCustom();

        log.info("Stopping {} in {}", entity(), entity().getLocations());

        StopMode stopMachineMode = getStopMachineMode(parameters);
        StopMode stopProcessMode = parameters.get(StopSoftwareParameters.STOP_PROCESS_MODE);

        DynamicTasks.queue("pre-stop", new PreStopCustomTask());

        Maybe<MachineLocation> machine = Machines.findUniqueMachineLocation(entity().getLocations());
        Task<String> stoppingProcess = null;
        if (canStop(stopProcessMode, entity())) {
            stoppingProcess = DynamicTasks.queue("stopping (process)", new StopProcessesAtMachineTask());
        }

        Task<StopMachineDetails<Integer>> stoppingMachine = null;
        if (canStop(stopMachineMode, machine.isAbsent())) {
            // Release this machine (even if error trying to stop process - we rethrow that after)
            stoppingMachine = DynamicTasks.queue("stopping (machine)", stopTask);

            DynamicTasks.drain(entity().getConfig(STOP_PROCESS_TIMEOUT), false);

            // shutdown the machine if stopping process fails or takes too long
            synchronized (stoppingMachine) {
                // task also used as mutex by DST when it submits it; ensure it only submits once!
                if (!stoppingMachine.isSubmitted()) {
                    // force the stoppingMachine task to run by submitting it here
                    StringBuilder msg = new StringBuilder("Submitting machine stop early in background for ").append(entity());
                    if (stoppingProcess == null) {
                        msg.append(". Process stop skipped, pre-stop not finished?");
                    } else {
                        msg.append(" because process stop has ").append(
                                (stoppingProcess.isDone() ? "finished abnormally" : "not finished"));
                    }
                    log.warn(msg.toString());
                    Entities.submit(entity(), stoppingMachine);
                }
            }
        }

        try {
            // This maintains previous behaviour of silently squashing any errors on the stoppingProcess task if the
            // stoppingMachine exits with a nonzero value
            boolean checkStopProcesses = (stoppingProcess != null && (stoppingMachine == null || stoppingMachine.get().value == 0));

            if (checkStopProcesses) {
                // TODO we should test for destruction above, not merely successful "stop", as things like localhost and ssh won't be destroyed
                DynamicTasks.waitForLast();
                if (machine.isPresent()) {
                    // throw early errors *only if* there is a machine and we have not destroyed it
                    stoppingProcess.get();
                }
            }
        } catch (Throwable e) {
            ServiceStateLogic.setExpectedState(entity(), Lifecycle.ON_FIRE);
            Exceptions.propagate(e);
        }
        entity().setAttribute(SoftwareProcess.SERVICE_UP, false);
        ServiceStateLogic.setExpectedState(entity(), Lifecycle.STOPPED);

        DynamicTasks.queue("post-stop", new PostStopCustomTask());

        if (log.isDebugEnabled()) log.debug("Stopped software process entity "+entity());
    }

    private class StopAnyProvisionedMachinesTask implements Callable<StopMachineDetails<Integer>> {
        public StopMachineDetails<Integer> call() {
            return stopAnyProvisionedMachines();
        }
    }

    private class SuspendAnyProvisionedMachinesTask implements Callable<StopMachineDetails<Integer>> {
        public StopMachineDetails<Integer> call() {
            return suspendAnyProvisionedMachines();
        }
    }

    private class StopProcessesAtMachineTask implements Callable<String> {
        public String call() {
            DynamicTasks.markInessential();
            stopProcessesAtMachine();
            DynamicTasks.waitForLast();
            return "Stop processes completed with no errors.";
        }
    }

    private class StopMachineTask implements Callable<String> {
        public String call() {
            DynamicTasks.markInessential();
            stop(ConfigBag.newInstance().configure(StopSoftwareParameters.STOP_MACHINE_MODE, StopMode.IF_NOT_STOPPED));
            DynamicTasks.waitForLast();
            return "Stop of machine completed with no errors.";
        }
    }

    private class PreStopCustomTask implements Callable<String> {
        public String call() {
            if (entity().getAttribute(SoftwareProcess.SERVICE_STATE_ACTUAL) == Lifecycle.STOPPED) {
                log.debug("Skipping stop of entity " + entity() + " when already stopped");
                return "Already stopped";
            }
            ServiceStateLogic.setExpectedState(entity(), Lifecycle.STOPPING);
            entity().setAttribute(SoftwareProcess.SERVICE_UP, false);
            preStopCustom();
            return null;
        }
    }

    private class PostStopCustomTask implements Callable<Void> {
        public Void call() {
            postStopCustom();
            return null;
        }
    }

    public static StopMode getStopMachineMode(ConfigBag parameters) {
        @SuppressWarnings("deprecation")
        final boolean hasStopMachine = parameters.containsKey(StopSoftwareParameters.STOP_MACHINE);
        @SuppressWarnings("deprecation")
        final Boolean isStopMachine = parameters.get(StopSoftwareParameters.STOP_MACHINE);

        final boolean hasStopMachineMode = parameters.containsKey(StopSoftwareParameters.STOP_MACHINE_MODE);
        final StopMode stopMachineMode = parameters.get(StopSoftwareParameters.STOP_MACHINE_MODE);

        if (hasStopMachine && isStopMachine != null) {
            checkCompatibleMachineModes(isStopMachine, hasStopMachineMode, stopMachineMode);
            if (isStopMachine) {
                return StopMode.IF_NOT_STOPPED;
            } else {
                return StopMode.NEVER;
            }
        }
        return stopMachineMode;
    }

    public static boolean canStop(StopMode stopMode, Entity entity) {
        boolean isEntityStopped = entity.getAttribute(SoftwareProcess.SERVICE_STATE_ACTUAL)==Lifecycle.STOPPED;
        return canStop(stopMode, isEntityStopped);
    }

    protected static boolean canStop(StopMode stopMode, boolean isStopped) {
        return stopMode == StopMode.ALWAYS ||
                stopMode == StopMode.IF_NOT_STOPPED && !isStopped;
    }

    @SuppressWarnings("deprecation")
    private static void checkCompatibleMachineModes(Boolean isStopMachine, boolean hasStopMachineMode, StopMode stopMachineMode) {
        if (hasStopMachineMode &&
                (isStopMachine && stopMachineMode != StopMode.IF_NOT_STOPPED ||
                 !isStopMachine && stopMachineMode != StopMode.NEVER)) {
            throw new IllegalStateException("Incompatible values for " +
                    StopSoftwareParameters.STOP_MACHINE.getName() + " (" + isStopMachine + ") and " +
                    StopSoftwareParameters.STOP_MACHINE_MODE.getName() + " (" + stopMachineMode + "). " +
                    "Use only one of the parameters.");
        }
    }

    /** 
     * Override to check whether stop can be executed.
     * Throw if stop should be aborted.
     */
    protected void preStopConfirmCustom() {
        // nothing needed here
    }

    protected void preStopCustom() {
        // nothing needed here
    }

    protected void postStopCustom() {
        // nothing needed here
    }

    protected void preRestartCustom() {
        // nothing needed here
    }

    protected void postRestartCustom() {
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

    /**
     * Return string message of result.
     * <p>
     * Can run synchronously or not, caller will submit/queue as needed, and will block on any submitted tasks.
     */
    protected abstract String stopProcessesAtMachine();

    /**
     * Stop and release the {@link MachineLocation} the entity is provisioned at.
     * <p>
     * Can run synchronously or not, caller will submit/queue as needed, and will block on any submitted tasks.
     */
    protected StopMachineDetails<Integer> stopAnyProvisionedMachines() {
        @SuppressWarnings("unchecked")
        MachineProvisioningLocation<MachineLocation> provisioner = entity().getAttribute(SoftwareProcess.PROVISIONING_LOCATION);

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
        
        clearEntityLocationAttributes(machine);
        provisioner.release((MachineLocation)machine);

        return new StopMachineDetails<Integer>("Decommissioned "+machine, 1);
    }

    /**
     * Suspend the {@link MachineLocation} the entity is provisioned at.
     * <p>
     * Expects the entity's {@link SoftwareProcess#PROVISIONING_LOCATION provisioner} to be capable of
     * {@link SuspendsMachines suspending machines}.
     *
     * @throws java.lang.UnsupportedOperationException if the entity's provisioner cannot suspend machines.
     * @see brooklyn.location.MachineManagementMixins.SuspendsMachines
     */
    protected StopMachineDetails<Integer> suspendAnyProvisionedMachines() {
        @SuppressWarnings("unchecked")
        MachineProvisioningLocation<MachineLocation> provisioner = entity().getAttribute(SoftwareProcess.PROVISIONING_LOCATION);

        if (Iterables.isEmpty(entity().getLocations())) {
            log.debug("No machine decommissioning necessary for " + entity() + " - no locations");
            return new StopMachineDetails<>("No machine suspend necessary - no locations", 0);
        }

        // Only release this machine if we ourselves provisioned it (e.g. it might be running other services)
        if (provisioner == null) {
            log.debug("No machine decommissioning necessary for " + entity() + " - did not provision");
            return new StopMachineDetails<>("No machine suspend necessary - did not provision", 0);
        }

        Location machine = getLocation(null);
        if (!(machine instanceof MachineLocation)) {
            log.debug("No decommissioning necessary for " + entity() + " - not a machine location (" + machine + ")");
            return new StopMachineDetails<>("No machine suspend necessary - not a machine (" + machine + ")", 0);
        }

        if (!(provisioner instanceof SuspendsMachines)) {
            log.debug("Location provisioner ({}) cannot suspend machines", provisioner);
            throw new UnsupportedOperationException("Location provisioner cannot suspend machines: " + provisioner);
        }

        clearEntityLocationAttributes(machine);
        SuspendsMachines.class.cast(provisioner).suspendMachine(MachineLocation.class.cast(machine));

        return new StopMachineDetails<>("Suspended " + machine, 1);
    }

    /**
     * Nulls the attached entity's hostname, address, subnet hostname and subnet address sensors
     * and removes the given machine from its locations.
     */
    protected void clearEntityLocationAttributes(Location machine) {
        entity().removeLocations(ImmutableList.of(machine));
        entity().setAttribute(Attributes.HOSTNAME, null);
        entity().setAttribute(Attributes.ADDRESS, null);
        entity().setAttribute(Attributes.SUBNET_HOSTNAME, null);
        entity().setAttribute(Attributes.SUBNET_ADDRESS, null);
    }

}
