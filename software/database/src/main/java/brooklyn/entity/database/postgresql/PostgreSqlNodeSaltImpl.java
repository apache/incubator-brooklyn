package brooklyn.entity.database.postgresql;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Effector;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.EffectorStartableImpl;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.effector.EffectorBody;
import brooklyn.entity.effector.Effectors;
import brooklyn.entity.salt.SaltConfig;
import brooklyn.entity.salt.SaltConfigs;
import brooklyn.entity.salt.SaltLifecycleEffectorTasks;
import brooklyn.entity.software.SshEffectorTasks;
import brooklyn.event.basic.DependentConfiguration;
import brooklyn.event.feed.ssh.SshFeed;
import brooklyn.event.feed.ssh.SshPollConfig;
import brooklyn.location.Location;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.ResourceUtils;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.ssh.BashCommands;
import brooklyn.util.task.DynamicTasks;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;

public class PostgreSqlNodeSaltImpl extends EffectorStartableImpl implements PostgreSqlNode, SoftwareProcess {

    private static final Logger LOG = LoggerFactory.getLogger(PostgreSqlNodeSaltImpl.class);

    public static final Effector<String> EXECUTE_SCRIPT = Effectors.effector(String.class, "executeScript")
            .description("invokes a script")
            .parameter(ExecuteScriptEffectorBody.SCRIPT)
            .impl(new ExecuteScriptEffectorBody()).build();
    
    private SshFeed feed;

    @Override
    public void init() {
        super.init();
        new SaltPostgreSqlLifecycle().attachLifecycleEffectors(this);
    }
    
    public static class SaltPostgreSqlLifecycle extends SaltLifecycleEffectorTasks {
        public SaltPostgreSqlLifecycle() {
            usePidFile("/var/run/postgresql/*.pid");
            useService("postgresql");
        }
        
        @Override
        protected void startMinionAsync() {
            Entities.warnOnIgnoringConfig(entity(), SaltConfig.SALT_FORMULAS);
            Entities.warnOnIgnoringConfig(entity(), SaltConfig.SALT_RUN_LIST);
            Entities.warnOnIgnoringConfig(entity(), SaltConfig.SALT_LAUNCH_ATTRIBUTES);
            
            // TODO Set these as defaults, rather than replacing user's value!?
            SaltConfigs.addToFormulas(entity(), "postgresql", "https://github.com/saltstack-formulas/postgres-formula");
            SaltConfigs.addLaunchAttributes(entity(), ImmutableMap.<String,Object>builder()
                    .put("port", DependentConfiguration.attributeWhenReady(entity(), PostgreSqlNode.POSTGRESQL_PORT))
                    .put("listen_addresses", "*")
                    .put("pg_hba.type", "host")
                    .put("pg_hba.db", "all")
                    .put("pg_hba.user", "all") 
                    .put("pg_hba.addr", "0.0.0.0/0")
                    .put("pg_hba.method", "md5")
                    .build());
            
            super.startMinionAsync();
        }
        
        @Override
        protected void postStartCustom() {
            super.postStartCustom();

            // now run the creation script
            String creationScript;
            String creationScriptUrl = entity().getConfig(PostgreSqlNode.CREATION_SCRIPT_URL);
            if (creationScriptUrl != null)
                creationScript = new ResourceUtils(entity()).getResourceAsString(creationScriptUrl);
            else creationScript = entity().getConfig(PostgreSqlNode.CREATION_SCRIPT_CONTENTS);
            entity().invoke(PostgreSqlNodeSaltImpl.EXECUTE_SCRIPT, 
                    ConfigBag.newInstance().configure(ExecuteScriptEffectorBody.SCRIPT, creationScript).getAllConfig()).getUnchecked();

            // and finally connect sensors
            ((PostgreSqlNodeSaltImpl)entity()).connectSensors();
        }
        
        @Override
        protected void preStopCustom() {
            ((PostgreSqlNodeSaltImpl)entity()).disconnectSensors();
            super.preStopCustom();
        }
    }
    
    public static class ExecuteScriptEffectorBody extends EffectorBody<String> {
        public static final ConfigKey<String> SCRIPT = ConfigKeys.newStringConfigKey("script", "contents of script to run");
        
        @Override
        public String call(ConfigBag parameters) {
            return DynamicTasks.queue(SshEffectorTasks.ssh(
                    BashCommands.pipeTextTo(
                        parameters.get(SCRIPT),
                        BashCommands.sudoAsUser("postgres", "psql --file -")))
                    .requiringExitCodeZero()).getStdout();
        }
    }
    
    protected void connectSensors() {
        setAttribute(DB_URL, String.format("postgresql://%s:%s/", getAttribute(HOSTNAME), getAttribute(POSTGRESQL_PORT)));

        Location machine = Iterables.get(getLocations(), 0, null);

        if (machine instanceof SshMachineLocation) {
            feed = SshFeed.builder()
                    .entity(this)
                    .machine((SshMachineLocation)machine)
                    .poll(new SshPollConfig<Boolean>(SERVICE_UP)
                            .command("ps -ef | grep [p]ostgres")
                            .setOnSuccess(true)
                            .setOnFailureOrException(false))
                    .build();
        } else {
            LOG.warn("Location(s) %s not an ssh-machine location, so not polling for status; setting serviceUp immediately", getLocations());
        }
    }

    protected void disconnectSensors() {
        if (feed != null) feed.stop();
    }

}
