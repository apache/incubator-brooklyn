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
package brooklyn.entity.salt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.BrooklynServerConfig;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.entity.software.MachineLifecycleEffectorTasks;
import brooklyn.entity.software.SshEffectorTasks;
import brooklyn.location.MachineLocation;
import brooklyn.util.net.Urls;
import brooklyn.util.ssh.BashCommands;
import brooklyn.util.task.DynamicTasks;
import brooklyn.util.task.Tasks;
import brooklyn.util.time.Duration;
import brooklyn.util.time.Time;

import com.google.common.annotations.Beta;
import com.google.common.base.Supplier;

/**
 * Creates effectors to start, restart, and stop processes using SaltStack.
 * <p>
 * Instances of this should use the {@link SaltConfig} config attributes to configure startup,
 * and invoke {@link #usePidFile(String)} or {@link #useService(String)} to determine check-running and stop behaviour.
 * Alternatively this can be subclassed and {@link #postStartCustom()} and {@link #stopProcessesAtMachine()} overridden.
 *
 * @since 0.6.0
 */
@Beta
public class SaltLifecycleEffectorTasks extends MachineLifecycleEffectorTasks implements SaltConfig {

    private static final Logger log = LoggerFactory.getLogger(SaltLifecycleEffectorTasks.class);

    protected SaltStackMaster master = null;
    protected String pidFile, serviceName, windowsServiceName;

    public SaltLifecycleEffectorTasks() {
    }

    public SaltLifecycleEffectorTasks usePidFile(String pidFile) {
        this.pidFile = pidFile;
        return this;
    }
    public SaltLifecycleEffectorTasks useService(String serviceName) {
        this.serviceName = serviceName;
        return this;
    }
    public SaltLifecycleEffectorTasks useWindowsService(String serviceName) {
        this.windowsServiceName = serviceName;
        return this;
    }
    public SaltLifecycleEffectorTasks master(SaltStackMaster master) {
        this.master = master;
        return this;
    }

    @Override
    public void attachLifecycleEffectors(Entity entity) {
        if (pidFile==null && serviceName==null && getClass().equals(SaltLifecycleEffectorTasks.class)) {
            // warn on incorrect usage
            log.warn("Uses of "+getClass()+" must define a PID file or a service name (or subclass and override {start,stop} methods as per javadoc) " +
                    "in order for check-running and stop to work");
        }

        super.attachLifecycleEffectors(entity);
    }

    @Override
    protected String startProcessesAtMachine(Supplier<MachineLocation> machineS) {
        startMinionAsync();
        return "salt start tasks submitted";
    }

    protected void startMinionAsync() {
        // TODO make directories more configurable (both for ssh-drivers and for this)
        String installDir = Urls.mergePaths(BrooklynServerConfig.getMgmtBaseDir(entity().getManagementContext()), "salt-install");
        String runDir = Urls.mergePaths(BrooklynServerConfig.getMgmtBaseDir(entity().getManagementContext()),
                "apps/"+entity().getApplicationId()+"/salt-entities/"+entity().getId());

        Boolean masterless = entity().getConfig(SaltConfig.MASTERLESS_MODE);
        if (masterless) {
            DynamicTasks.queue(
                    SaltTasks.installFormulas(installDir, SaltConfigs.getRequiredConfig(entity(), SALT_FORMULAS), false),
                    SaltTasks.buildSaltFile(runDir,
                            SaltConfigs.getRequiredConfig(entity(), SALT_RUN_LIST),
                            entity().getConfig(SALT_LAUNCH_ATTRIBUTES)),
                    SaltTasks.installSaltMinion(entity(), runDir, installDir, false),
                    SaltTasks.runSalt(runDir));
        } else {
            throw new UnsupportedOperationException("Salt master mode not yet supported for minions");
        }
    }

    @Override
    protected void postStartCustom() {
        boolean result = false;
        result |= tryCheckStartPid();
        result |= tryCheckStartService();
        result |= tryCheckStartWindowsService();
        if (!result) {
            throw new IllegalStateException("The process for "+entity()+" appears not to be running (no way to check!)");
        }
    }

    protected boolean tryCheckStartPid() {
        if (pidFile==null) return false;

        // if it's still up after 5s assume we are good (default behaviour)
        Time.sleep(Duration.FIVE_SECONDS);
        if (!DynamicTasks.queue(SshEffectorTasks.isPidFromFileRunning(pidFile).runAsRoot()).get()) {
            throw new IllegalStateException("The process for "+entity()+" appears not to be running (pid file "+pidFile+")");
        }

        // and set the PID
        entity().setAttribute(Attributes.PID,
                Integer.parseInt(DynamicTasks.queue(SshEffectorTasks.ssh("cat "+pidFile).runAsRoot()).block().getStdout().trim()));
        return true;
    }

    protected boolean tryCheckStartService() {
        if (serviceName==null) return false;

        // if it's still up after 5s assume we are good (default behaviour)
        Time.sleep(Duration.FIVE_SECONDS);
        if (!((Integer)0).equals(DynamicTasks.queue(SshEffectorTasks.ssh("/etc/init.d/"+serviceName+" status").runAsRoot()).get())) {
            throw new IllegalStateException("The process for "+entity()+" appears not to be running (service "+serviceName+")");
        }

        return true;
    }

    protected boolean tryCheckStartWindowsService() {
        if (windowsServiceName==null) return false;

        // if it's still up after 5s assume we are good (default behaviour)
        Time.sleep(Duration.FIVE_SECONDS);
        if (!((Integer)0).equals(DynamicTasks.queue(SshEffectorTasks.ssh("sc query \""+serviceName+"\" | find \"RUNNING\"").runAsCommand()).get())) {
            throw new IllegalStateException("The process for "+entity()+" appears not to be running (windowsService "+windowsServiceName+")");
        }

        return true;
    }

    @Override
    protected String stopProcessesAtMachine() {
        boolean result = false;
        result |= tryStopService();
        result |= tryStopPid();
        if (!result) {
            throw new IllegalStateException("The process for "+entity()+" appears could not be stopped (no impl!)");
        }
        return "stopped";
    }

    protected boolean tryStopService() {
        if (serviceName==null) return false;
        int result = DynamicTasks.queue(SshEffectorTasks.ssh("/etc/init.d/"+serviceName+" stop").runAsRoot()).get();
        if (0==result) return true;
        if (entity().getAttribute(Attributes.SERVICE_STATE)!=Lifecycle.RUNNING)
            return true;

        throw new IllegalStateException("The process for "+entity()+" appears could not be stopped (exit code "+result+" to service stop)");
    }

    protected boolean tryStopPid() {
        Integer pid = entity().getAttribute(Attributes.PID);
        if (pid==null) {
            if (entity().getAttribute(Attributes.SERVICE_STATE)==Lifecycle.RUNNING && pidFile==null)
                log.warn("No PID recorded for "+entity()+" when running, with PID file "+pidFile+"; skipping kill in "+Tasks.current());
            else
                if (log.isDebugEnabled())
                    log.debug("No PID recorded for "+entity()+"; skipping ("+entity().getAttribute(Attributes.SERVICE_STATE)+" / "+pidFile+")");
            return false;
        }

        // allow non-zero exit as process may have already been killed
        DynamicTasks.queue(SshEffectorTasks.ssh(
                "kill "+pid, "sleep 5", BashCommands.ok("kill -9 "+pid)).allowingNonZeroExitCode().runAsRoot()).block();

        if (DynamicTasks.queue(SshEffectorTasks.isPidRunning(pid).runAsRoot()).get()) {
            throw new IllegalStateException("Process for "+entity()+" in "+pid+" still running after kill");
        }
        entity().setAttribute(Attributes.PID, null);
        return true;
    }

    /**
     * {@inheritDoc}
     *
     * @return the Salt master entity if it exists.
     * @see #master(SaltStackMaster)
     * @see SaltConfig#MASTERLESS_MODE
     */
    @Override
    public SaltStackMaster getMaster() {
        return master;
    }

}
