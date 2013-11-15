package io.brooklyn.camp.brooklyn;

import io.brooklyn.camp.CampServer;
import io.brooklyn.camp.brooklyn.spi.creation.BrooklynAssemblyTemplateInstantiator;
import io.brooklyn.camp.brooklyn.spi.lookup.BrooklynUrlLookup;
import io.brooklyn.camp.spi.Assembly;
import io.brooklyn.camp.spi.AssemblyTemplate;
import io.brooklyn.camp.spi.PlatformRootSummary;

import java.io.Reader;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.BrooklynProperties;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.BrooklynTasks;
import brooklyn.entity.basic.Entities;
import brooklyn.launcher.BrooklynLauncher;
import brooklyn.management.ManagementContext;
import brooklyn.management.Task;
import brooklyn.util.ResourceUtils;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.stream.Streams;

public class YamlLauncher {

    private static final Logger log = LoggerFactory.getLogger(YamlLauncher.class);
    
    private ManagementContext brooklynMgmt;
    private BrooklynCampPlatform platform;

    public void launchPlatform() {
        BrooklynLauncher launcher = BrooklynLauncher.newInstance()
              .start();
        ((BrooklynProperties)launcher.getServerDetails().getManagementContext().getConfig()).
          put(BrooklynUrlLookup.BROOKLYN_ROOT_URL, launcher.getServerDetails().getWebServerUrl());
        brooklynMgmt = launcher.getServerDetails().getManagementContext();
      
        platform = new BrooklynCampPlatform(
              PlatformRootSummary.builder().name("Brooklyn CAMP Platform").build(),
              brooklynMgmt);
        
        new CampServer(platform, "").start();
    }
    
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

    public static void main(String[] args) {
        BrooklynAssemblyTemplateInstantiator.TARGET_LOCATION =
            "localhost"
            //"named:hpcloud-compute-us-west-az1"
            //"aws-ec2:us-west-2"
            ;
        
        YamlLauncher l = new YamlLauncher();
        l.launchPlatform();
        
//        l.launchAppYaml("java-web-app-and-db-with-function.yaml");
//        l.launchAppYaml("java-web-app-and-memsql.yaml");
//        l.launchAppYaml("memsql.yaml");
        l.launchAppYaml("playing.yaml");
    }
    
}
