package brooklyn.entity.chef.mysql;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.BasicStartable;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.chef.ChefConfig;
import brooklyn.entity.chef.ChefConfigs;
import brooklyn.entity.chef.ChefTasks;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.software.MachineLifecycleEffectorTasks;
import brooklyn.entity.software.SshEffectorTasks;
import brooklyn.location.MachineLocation;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.task.DynamicTasks;
import brooklyn.util.time.Duration;
import brooklyn.util.time.Time;

import com.google.common.base.Supplier;

public class DynamicToyMySqlEntityChefBuilder implements ChefConfig {

    private static final Logger log = LoggerFactory.getLogger(DynamicToyMySqlEntityChefBuilder.class);

    public static EntitySpec<? extends Entity> spec() {
        EntitySpec<? extends Entity> spec = EntitySpec.create(BasicStartable.class);
        // TODO use EntityInitializer rather than makeMySql below
        return spec;
    }

    public static Entity makeMySql(final EntityInternal entity) {
        new MachineLifecycleEffectorTasks() {
            @Override
            protected String startProcessesAtMachine(Supplier<MachineLocation> machineS) {
                // TODO these locations should be standardised (and cleaned up afterwards? 
                // or, probably better, use the same as the Drivers.)
                String installDir = "/tmp/brooklyn-chef/installation";
                String runDir = "/tmp/brooklyn-chef/run-"+entity.getId();
                
                // TODO this shoulw be part of a ChefMachineLifecycleEffector...
                DynamicTasks.queue(
                    ChefTasks.installChef(installDir, false), 
                    ChefTasks.installCookbooks(installDir, ChefConfigs.getRequiredConfig(entity, CHEF_COOKBOOKS), false));
                
                DynamicTasks.queue(ChefTasks.buildChefFile(runDir, installDir, "launch", 
                        ChefConfigs.getRequiredConfig(entity, CHEF_RUN_LIST),
                        entity.getConfig(CHEF_LAUNCH_ATTRIBUTES)));
                
                DynamicTasks.queue(ChefTasks.runChef(runDir, "launch"));
                
                return "chef mysql tasks submitted";
            }
            protected void postStartCustom() {
                // if it's still up after 5s assume we are good (in this toy example)
                Time.sleep(Duration.FIVE_SECONDS);
                if (!DynamicTasks.queue(SshEffectorTasks.isPidFromFileRunning("/var/run/mysqld/mysql*.pid").runAsRoot()).get()) {
                    throw new IllegalStateException("MySQL appears not to be running");
                }
                
                // and set the PID
                entity().setAttribute(Attributes.PID, 
                        Integer.parseInt(DynamicTasks.queue(SshEffectorTasks.ssh("cat /var/run/mysqld/mysql*.pid").runAsRoot()).block().getStdout().trim()));
            }
            
            @Override
            protected String stopProcessesAtMachine() {
                DynamicTasks.queue(SshEffectorTasks.ssh("/etc/init.d/mysql stop").allowingNonZeroExitCode().runAsRoot());
                
                return "submitted service stop";
            }
            
        }.attachLifecycleEffectors(entity);

        ChefConfigs.addToRunList(entity, "mysql::server");
        ChefConfigs.addToCookbooksFromGithub(entity, "mysql", "build-essential", "openssl");
        ChefConfigs.setLaunchAttribute(entity, "mysql",  
                MutableMap.of()
                    .add("server_root_password", "MyPassword")
                    .add("server_debian_password", "MyPassword")
                    .add("server_repl_password", "MyPassword")
                );
        
        log.debug("decorated "+entity+" for "+DynamicToyMySqlEntityChefBuilder.class);
        return entity;
    }
    
}
