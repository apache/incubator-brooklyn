package io.brooklyn.camp.brooklyn;

import io.brooklyn.camp.spi.Assembly;
import io.brooklyn.camp.spi.AssemblyTemplate;

import java.io.Reader;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Application;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.BrooklynShutdownHooks;
import brooklyn.entity.basic.BrooklynTaskTags;
import brooklyn.entity.basic.Entities;
import brooklyn.management.ManagementContext;
import brooklyn.management.Task;
import brooklyn.util.ResourceUtils;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.stream.Streams;

import com.google.common.annotations.Beta;

/** convenience for launching YAML files directly */
@Beta
public abstract class YamlLauncherAbstract {

    private static final Logger log = LoggerFactory.getLogger(YamlLauncherAbstract.class);
       
    protected final BrooklynCampPlatformLauncherAbstract platformLauncher;

    protected final BrooklynCampPlatform platform;
    protected final ManagementContext brooklynMgmt;
    protected boolean shutdownAppsOnExit = false;

    public YamlLauncherAbstract() {
        this.platformLauncher = newPlatformLauncher();
        platformLauncher.launch();
        this.platform = platformLauncher.getCampPlatform();
        this.brooklynMgmt = platformLauncher.getBrooklynMgmt();
    }

    public ManagementContext getManagementContext() {
        return brooklynMgmt;
    }

    public boolean getShutdownAppsOnExit() {
        return shutdownAppsOnExit;
    }
    
    public void setShutdownAppsOnExit(boolean shutdownAppsOnExit) {
        this.shutdownAppsOnExit = shutdownAppsOnExit;
    }
    
    protected abstract BrooklynCampPlatformLauncherAbstract newPlatformLauncher();

    public Application launchAppYaml(String url) {
        try {
            Reader input = Streams.reader(new ResourceUtils(this).getResourceFromUrl(url));
            AssemblyTemplate at = platform.pdp().registerDeploymentPlan(input);

            Assembly assembly = at.getInstantiator().newInstance().instantiate(at, platform);
            Entity app = brooklynMgmt.getEntityManager().getEntity(assembly.getId());
            log.info("Launching "+app);

            if (getShutdownAppsOnExit()) BrooklynShutdownHooks.invokeStopOnShutdown(app);
            
            Set<Task<?>> tasks = BrooklynTaskTags.getTasksInEntityContext(brooklynMgmt.getExecutionManager(), app);
            log.info("Waiting on "+tasks.size()+" task(s)");
            for (Task<?> t: tasks) t.blockUntilEnded();

            log.info("Application started from YAML file "+url+": "+app);
            Entities.dumpInfo(app);
            return (Application)app;
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }

    /** kills all apps, web servers, and mgmt context
     * <p>
     * this launcher does not support being used again subsequently */
    public void destroyAll() {
        Entities.destroyAll(getManagementContext());
        try {
            platformLauncher.stopServers();
        } catch (Exception e) {
            log.warn("Unable to stop servers (ignoring): "+e);
        }
    }

}
