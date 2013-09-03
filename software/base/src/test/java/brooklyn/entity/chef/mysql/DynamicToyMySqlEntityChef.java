package brooklyn.entity.chef.mysql;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.BasicStartable;
import brooklyn.entity.basic.EffectorStartableImpl;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.chef.ChefConfig;
import brooklyn.entity.chef.ChefConfigs;
import brooklyn.entity.chef.ChefLifecycleEffectorTasks;
import brooklyn.entity.proxying.EntityInitializer;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.util.collections.MutableMap;

public class DynamicToyMySqlEntityChef implements ChefConfig {

    private static final Logger log = LoggerFactory.getLogger(DynamicToyMySqlEntityChef.class);

    protected static EntitySpec<? extends Entity> specBase() {
        EntitySpec<BasicStartable> spec = EntitySpec.create(BasicStartable.class, EffectorStartableImpl.class).addInitializer(ChefMySqlEntityInitializer.class);
        
        ChefConfigs.addToRunList(spec, "mysql::server");
        
        // chef mysql fails on first run but works on second if switching between server and solo modes
        spec.configure(ChefConfig.CHEF_RUN_CONVERGE_TWICE, true);
        
        return spec;
    }

    public static EntitySpec<? extends Entity> spec() {
        EntitySpec<? extends Entity> spec = specBase();
        
        addChefSoloConfig(spec);
        
        log.debug("Created entity spec for MySql: "+spec);
        return spec;
    }

    protected static void addChefSoloConfig(EntitySpec<? extends Entity> spec) {
        // for solo we always need dependent cookbooks set, and mysql requires password set
        ChefConfigs.addToCookbooksFromGithub(spec, "mysql", "build-essential", "openssl");
        ChefConfigs.addLaunchAttributes(spec, MutableMap.of("mysql",  
                MutableMap.of()
                .add("server_root_password", "MyPassword")
                .add("server_debian_password", "MyPassword")
                .add("server_repl_password", "MyPassword")
            ));
    }

    public static EntitySpec<? extends Entity> specSolo() {
        EntitySpec<? extends Entity> spec = specBase();
        addChefSoloConfig(spec);
        
        spec.configure(ChefConfig.CHEF_MODE, ChefConfig.ChefModes.SOLO);
        
        return spec;
    }

    public static EntitySpec<? extends Entity> specKnife() {
        EntitySpec<? extends Entity> spec = specBase();
        
        spec.configure(ChefConfig.CHEF_MODE, ChefConfig.ChefModes.KNIFE);
        
        log.debug("Created entity spec for MySql: "+spec);
        return spec;
    }

    public static class ChefMySqlEntityInitializer implements EntityInitializer {
        @Override
        public void apply(EntityLocal entity) {
            new ChefLifecycleEffectorTasks().
                    usePidFile("/var/run/mysqld/mysql*.pid").
                    useService("mysql").
                attachLifecycleEffectors(entity);
        }
    }
    
}
