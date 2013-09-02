package brooklyn.entity.chef;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractSoftwareProcessSshDriver;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.entity.software.MachineLifecycleEffectorTasks;
import brooklyn.entity.software.SshEffectorTasks;
import brooklyn.location.MachineLocation;
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
    
    protected String pidFile, serviceName, windowsServiceName;
    
    public ChefLifecycleEffectorTasks() {
    }
    
    public ChefLifecycleEffectorTasks usePidFile(String pidFile) {
        this.pidFile = pidFile;
        return this;
    }
    public ChefLifecycleEffectorTasks useService(String serviceName) {
        this.serviceName = serviceName;
        return this;
    }
    public ChefLifecycleEffectorTasks useWindowsService(String serviceName) {
        this.windowsServiceName = serviceName;
        return this;
    }

    @Override
    public void attachLifecycleEffectors(Entity entity) {
        if (pidFile==null && serviceName==null && getClass().equals(ChefLifecycleEffectorTasks.class)) {
            // warn on incorrect usage
            log.warn("Uses of "+getClass()+" must define a PID file or a service name (or subclass and override {start,stop} methods as per javadoc) " +
            		"in order for check-running and stop to work");
        }
            
        super.attachLifecycleEffectors(entity);
    }
    
    @Override
    protected String startProcessesAtMachine(Supplier<MachineLocation> machineS) {
        ChefModes mode = entity().getConfig(ChefConfig.CHEF_MODE);
        if (mode == ChefModes.AUTODETECT) {
            ProcessTaskWrapper<Boolean> installCheck = DynamicTasks.queue(
                    ChefServerTasks.isKnifeInstalled());
            mode = installCheck.get() ? ChefModes.KNIFE : ChefModes.SOLO;
            log.debug("Using Chef in "+mode+" mode due to autodetect exit code "+installCheck.getExitCode());
        }
        
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

    protected void startWithChefSoloAsync() {
        // TODO make directories more configurable (both for ssh-drivers and for this)
        String installDir = Urls.mergePaths(AbstractSoftwareProcessSshDriver.BROOKLYN_HOME_DIR, "chef-install");
        String runDir = Urls.mergePaths(AbstractSoftwareProcessSshDriver.BROOKLYN_HOME_DIR, 
                "apps/"+entity().getApplicationId()+"/chef-entities/"+entity().getId());
        
        DynamicTasks.queue(
                ChefSoloTasks.installChef(installDir, false), 
                ChefSoloTasks.installCookbooks(installDir, ChefConfigs.getRequiredConfig(entity(), CHEF_COOKBOOKS), false));
        
        DynamicTasks.queue(ChefSoloTasks.buildChefFile(runDir, installDir, "launch", 
                ChefConfigs.getRequiredConfig(entity(), CHEF_RUN_LIST),
                entity().getConfig(CHEF_LAUNCH_ATTRIBUTES)));
        
        DynamicTasks.queue(ChefSoloTasks.runChef(runDir, "launch", entity().getConfig(CHEF_RUN_CONVERGE_TWICE)));
    }
    
    protected void startWithKnifeAsync() {
        DynamicTasks.queue(
                ChefServerTasks.knifeConvergeTask()
                    .knifeRunList(Strings.join(Preconditions.checkNotNull(entity().getConfig(ChefConfig.CHEF_RUN_LIST), 
                                "%s must be supplied for %s", ChefConfig.CHEF_RUN_LIST, entity()), ","))
                    .knifeAddAttributes(entity().getConfig(CHEF_LAUNCH_ATTRIBUTES))
                    .knifeRunTwice(entity().getConfig(CHEF_RUN_CONVERGE_TWICE)) );
    }

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

}
