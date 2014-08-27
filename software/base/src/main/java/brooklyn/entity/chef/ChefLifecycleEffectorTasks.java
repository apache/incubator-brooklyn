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
package brooklyn.entity.chef;

import java.util.Collection;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.software.MachineLifecycleEffectorTasks;
import brooklyn.entity.software.SshEffectorTasks;
import brooklyn.location.MachineLocation;
import brooklyn.location.basic.Machines;
import brooklyn.util.collections.Jsonya;
import brooklyn.util.collections.Jsonya.Navigator;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.net.Urls;
import brooklyn.util.ssh.BashCommands;
import brooklyn.util.task.DynamicTasks;
import brooklyn.util.task.Tasks;
import brooklyn.util.task.system.ProcessTaskWrapper;
import brooklyn.util.text.Strings;
import brooklyn.util.time.Duration;
import brooklyn.util.time.Time;

import com.google.common.annotations.Beta;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;

/** 
 * Creates effectors to start, restart, and stop processes using Chef.
 * <p>
 * Instances of this should use the {@link ChefConfig} config attributes to configure startup,
 * and invoke {@link #usePidFile(String)} or {@link #useService(String)} to determine check-running and stop behaviour.
 * Alternatively this can be subclassed and {@link #postStartCustom()} and {@link #stopProcessesAtMachine()} overridden.
 * 
 * @since 0.6.0
 **/
@Beta
public class ChefLifecycleEffectorTasks extends MachineLifecycleEffectorTasks implements ChefConfig {

    private static final Logger log = LoggerFactory.getLogger(ChefLifecycleEffectorTasks.class);
    
    protected String _pidFile, _serviceName, _windowsServiceName;
    
    public ChefLifecycleEffectorTasks() {
    }
    
    public ChefLifecycleEffectorTasks usePidFile(String pidFile) {
        this._pidFile = pidFile;
        return this;
    }
    public ChefLifecycleEffectorTasks useService(String serviceName) {
        this._serviceName = serviceName;
        return this;
    }
    public ChefLifecycleEffectorTasks useWindowsService(String serviceName) {
        this._windowsServiceName = serviceName;
        return this;
    }
    
    public String getPidFile() {
        if (_pidFile!=null) return _pidFile;
        return _pidFile = entity().getConfig(ChefConfig.PID_FILE);
    }

    public String getServiceName() {
        if (_serviceName!=null) return _serviceName;
        return _serviceName = entity().getConfig(ChefConfig.SERVICE_NAME);
    }

    public String getWindowsServiceName() {
        if (_windowsServiceName!=null) return _windowsServiceName;
        return _windowsServiceName = entity().getConfig(ChefConfig.WINDOWS_SERVICE_NAME);
    }

    @Override
    public void attachLifecycleEffectors(Entity entity) {
        if (getPidFile()==null && getServiceName()==null && getClass().equals(ChefLifecycleEffectorTasks.class)) {
            // warn on incorrect usage
            log.warn("Uses of "+getClass()+" must define a PID file or a service name (or subclass and override {start,stop} methods as per javadoc) " +
            		"in order for check-running and stop to work");
        }
            
        super.attachLifecycleEffectors(entity);
    }

    public static ChefModes detectChefMode(Entity entity) {
        ChefModes mode = entity.getConfig(ChefConfig.CHEF_MODE);
        if (mode == ChefModes.AUTODETECT) {
            // TODO server via API
            ProcessTaskWrapper<Boolean> installCheck = DynamicTasks.queue(
                    ChefServerTasks.isKnifeInstalled());
            mode = installCheck.get() ? ChefModes.KNIFE : ChefModes.SOLO;
            log.debug("Using Chef in "+mode+" mode due to autodetect exit code "+installCheck.getExitCode());
        }
        Preconditions.checkNotNull(mode, "Non-null "+ChefConfig.CHEF_MODE+" required for "+entity);
        return mode;
    }
    
    @Override
    protected String startProcessesAtMachine(Supplier<MachineLocation> machineS) {
        ChefModes mode = detectChefMode(entity());
        switch (mode) {
        case KNIFE:
            startWithKnifeAsync();
            break;
            
        case SOLO:
            startWithChefSoloAsync();
            break;
            
        default:
            throw new IllegalStateException("Unknown Chef mode "+mode+" when starting processes for "+entity());
        }
        
        return "chef start tasks submitted ("+mode+")";
    }

    protected String getPrimaryCookbook() {
        return entity().getConfig(CHEF_COOKBOOK_PRIMARY_NAME);
    }
    
    @SuppressWarnings({ "unchecked", "deprecation" })
    protected void startWithChefSoloAsync() {
        String baseDir = MachineLifecycleEffectorTasks.resolveOnBoxDir(entity(), Machines.findUniqueSshMachineLocation(entity().getLocations()).get());
        String installDir = Urls.mergePaths(baseDir, "installs/chef");
        
        @SuppressWarnings("rawtypes")
        Map<String, String> cookbooks = (Map) 
            ConfigBag.newInstance( entity().getConfig(CHEF_COOKBOOK_URLS) )
            .putIfAbsent( entity().getConfig(CHEF_COOKBOOKS) )
            .getAllConfig();
        if (cookbooks.isEmpty())
            log.warn("No cookbook_urls set for "+entity()+"; launch will likely fail subsequently");
        DynamicTasks.queue(
                ChefSoloTasks.installChef(installDir, false), 
                ChefSoloTasks.installCookbooks(installDir, cookbooks, false));

        // TODO chef for and run a prestart recipe if necessary
        // TODO open ports

        String primary = getPrimaryCookbook();
        
        // put all config under brooklyn/cookbook/config
        Navigator<MutableMap<Object, Object>> attrs = Jsonya.newInstancePrimitive().at("brooklyn");
        if (Strings.isNonBlank(primary)) attrs.at(primary);
        attrs.at("config");
        attrs.put( entity().getAllConfigBag().getAllConfig() );
        // and put launch attrs at root
        try {
            attrs.root().put((Map<?,?>)Tasks.resolveDeepValue(entity().getConfig(CHEF_LAUNCH_ATTRIBUTES), Object.class, entity().getExecutionContext()));
        } catch (Exception e) { Exceptions.propagate(e); }
        
        Collection<? extends String> runList = entity().getConfig(CHEF_LAUNCH_RUN_LIST);
        if (runList==null) runList = entity().getConfig(CHEF_RUN_LIST);
        if (runList==null) {
            if (Strings.isNonBlank(primary)) runList = ImmutableList.of(primary+"::"+"start");
            else throw new IllegalStateException("Require a primary cookbook or a run_list to effect "+"start"+" on "+entity());
        }
        
        String runDir = Urls.mergePaths(baseDir,  
            "apps/"+entity().getApplicationId()+"/chef/entities/"+entity().getEntityType().getSimpleName()+"_"+entity().getId());
        
        DynamicTasks.queue(ChefSoloTasks.buildChefFile(runDir, installDir, "launch", 
                runList, (Map<String, Object>) attrs.root().get()));
        
        DynamicTasks.queue(ChefSoloTasks.runChef(runDir, "launch", entity().getConfig(CHEF_RUN_CONVERGE_TWICE)));
    }
    
    @SuppressWarnings({ "unchecked", "rawtypes", "deprecation" })
    protected void startWithKnifeAsync() {
        // TODO prestart, ports (as above); also, note, some aspects of this are untested as we need a chef server
        
        String primary = getPrimaryCookbook();

        // put all config under brooklyn/cookbook/config
        Navigator<MutableMap<Object, Object>> attrs = Jsonya.newInstancePrimitive().at("brooklyn");
        if (Strings.isNonBlank(primary)) attrs.at(primary);
        attrs.at("config");
        attrs.put( entity().getAllConfigBag().getAllConfig() );
        // and put launch attrs at root
        try {
            attrs.root().put((Map<?,?>)Tasks.resolveDeepValue(entity().getConfig(CHEF_LAUNCH_ATTRIBUTES), Object.class, entity().getExecutionContext()));
        } catch (Exception e) { Exceptions.propagate(e); }

        Collection<? extends String> runList = entity().getConfig(CHEF_LAUNCH_RUN_LIST);
        if (runList==null) runList = entity().getConfig(CHEF_RUN_LIST);
        if (runList==null) {
            if (Strings.isNonBlank(primary)) runList = ImmutableList.of(primary+"::"+"start");
            else throw new IllegalStateException("Require a primary cookbook or a run_list to effect "+"start"+" on "+entity());
        }

        DynamicTasks.queue(
                ChefServerTasks.knifeConvergeTask()
                    .knifeRunList(Strings.join(runList, ","))
                    .knifeAddAttributes((Map<? extends Object, ? extends Object>)(Map) attrs.root().get())
                    .knifeRunTwice(entity().getConfig(CHEF_RUN_CONVERGE_TWICE)) );
    }

    protected void postStartCustom() {
        boolean result = false;
        result |= tryCheckStartPid();
        result |= tryCheckStartService();
        result |= tryCheckStartWindowsService();
        if (!result) {
            log.warn("No way to check whether "+entity()+" is running; assuming yes");
        }
        entity().setAttribute(SoftwareProcess.SERVICE_UP, true);
    }
    
    protected boolean tryCheckStartPid() {
        if (getPidFile()==null) return false;
        
        // if it's still up after 5s assume we are good (default behaviour)
        Time.sleep(Duration.FIVE_SECONDS);
        if (!DynamicTasks.queue(SshEffectorTasks.isPidFromFileRunning(getPidFile()).runAsRoot()).get()) {
            throw new IllegalStateException("The process for "+entity()+" appears not to be running (pid file "+getPidFile()+")");
        }

        // and set the PID
        entity().setAttribute(Attributes.PID, 
                Integer.parseInt(DynamicTasks.queue(SshEffectorTasks.ssh("cat "+getPidFile()).runAsRoot()).block().getStdout().trim()));
        return true;
    }

    protected boolean tryCheckStartService() {
        if (getServiceName()==null) return false;
        
        // if it's still up after 5s assume we are good (default behaviour)
        Time.sleep(Duration.FIVE_SECONDS);
        if (!((Integer)0).equals(DynamicTasks.queue(SshEffectorTasks.ssh("/etc/init.d/"+getServiceName()+" status").runAsRoot()).get())) {
            throw new IllegalStateException("The process for "+entity()+" appears not to be running (service "+getServiceName()+")");
        }

        return true;
    }

    protected boolean tryCheckStartWindowsService() {
        if (getWindowsServiceName()==null) return false;
        
        // if it's still up after 5s assume we are good (default behaviour)
        Time.sleep(Duration.FIVE_SECONDS);
        if (!((Integer)0).equals(DynamicTasks.queue(SshEffectorTasks.ssh("sc query \""+getWindowsServiceName()+"\" | find \"RUNNING\"").runAsCommand()).get())) {
            throw new IllegalStateException("The process for "+entity()+" appears not to be running (windowsService "+getWindowsServiceName()+")");
        }

        return true;
    }

    @Override
    protected String stopProcessesAtMachine() {
        boolean result = false;
        result |= tryStopService();
        result |= tryStopWindowsService();
        result |= tryStopPid();
        if (!result) {
            throw new IllegalStateException("The process for "+entity()+" appears could not be stopped (no impl!)");
        }
        return "stopped";
    }
    
    protected boolean tryStopService() {
        if (getServiceName()==null) return false;
        int result = DynamicTasks.queue(SshEffectorTasks.ssh("/etc/init.d/"+getServiceName()+" stop").runAsRoot()).get();
        if (0==result) return true;
        if (entity().getAttribute(Attributes.SERVICE_STATE_ACTUAL)!=Lifecycle.RUNNING)
            return true;
        
        throw new IllegalStateException("The process for "+entity()+" appears could not be stopped (exit code "+result+" to service stop)");
    }

    protected boolean tryStopWindowsService() {
        if (getWindowsServiceName()==null) return false;
                int result = DynamicTasks.queue(SshEffectorTasks.ssh("sc query \""+getWindowsServiceName()+"\"").runAsCommand()).get();
        if (0==result) return true;
        if (entity().getAttribute(Attributes.SERVICE_STATE_ACTUAL)!=Lifecycle.RUNNING)
            return true;

        throw new IllegalStateException("The process for "+entity()+" appears could not be stopped (exit code "+result+" to service stop)");
    }

    protected boolean tryStopPid() {
        Integer pid = entity().getAttribute(Attributes.PID);
        if (pid==null) {
            if (entity().getAttribute(Attributes.SERVICE_STATE_ACTUAL)==Lifecycle.RUNNING && getPidFile()==null)
                log.warn("No PID recorded for "+entity()+" when running, with PID file "+getPidFile()+"; skipping kill in "+Tasks.current());
            else 
                if (log.isDebugEnabled())
                    log.debug("No PID recorded for "+entity()+"; skipping ("+entity().getAttribute(Attributes.SERVICE_STATE_ACTUAL)+" / "+getPidFile()+")");
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

}
