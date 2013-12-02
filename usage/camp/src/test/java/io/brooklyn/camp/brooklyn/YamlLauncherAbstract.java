package io.brooklyn.camp.brooklyn;

import io.brooklyn.camp.spi.Assembly;
import io.brooklyn.camp.spi.AssemblyTemplate;

import java.io.Reader;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.BrooklynTasks;
import brooklyn.entity.basic.Entities;
import brooklyn.launcher.BrooklynLauncher;
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

    protected final BrooklynLauncher launcher;
    protected final BrooklynCampPlatform platform;
    protected final ManagementContext brooklynMgmt;

    public YamlLauncherAbstract() {
        this.platformLauncher = newPlatformLauncher();
        platformLauncher.launch();
        this.launcher = platformLauncher.getBrooklynLauncher();
        this.platform = platformLauncher.getCampPlatform();
        this.brooklynMgmt = platformLauncher.getBrooklynMgmt();
    }

    protected abstract BrooklynCampPlatformLauncherAbstract newPlatformLauncher();

    public void launchAppYaml(String filename) {
        try {
            Reader input = Streams.reader(new ResourceUtils(this).getResourceFromUrl(filename));
            AssemblyTemplate at = platform.pdp().registerDeploymentPlan(input);

            Assembly assembly = at.getInstantiator().newInstance().instantiate(at, platform);
            Entity app = brooklynMgmt.getEntityManager().getEntity(assembly.getId());
            log.info("Launching "+app);

            Set<Task<?>> tasks = BrooklynTasks.getTasksInEntityContext(brooklynMgmt.getExecutionManager(), app);
            log.info("Waiting on "+tasks.size()+" task(s)");
            for (Task<?> t: tasks) t.blockUntilEnded();

            log.info("Application started from YAML file "+filename+": "+app);
            Entities.dumpInfo(app);
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }
    
}
