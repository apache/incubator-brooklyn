package brooklyn.entity.database.postgresql;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Effector;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.EffectorStartableImpl;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.chef.ChefConfig;
import brooklyn.entity.chef.ChefLifecycleEffectorTasks;
import brooklyn.entity.chef.ChefServerTasks;
import brooklyn.entity.effector.EffectorBody;
import brooklyn.entity.effector.Effectors;
import brooklyn.entity.software.SshEffectorTasks;
import brooklyn.event.feed.ssh.SshFeed;
import brooklyn.event.feed.ssh.SshPollConfig;
import brooklyn.location.Location;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.ResourceUtils;
import brooklyn.util.collections.Jsonya;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.ssh.BashCommands;
import brooklyn.util.task.DynamicTasks;

import com.google.common.collect.Iterables;

public class PostgreSqlNodeChefImpl extends EffectorStartableImpl implements PostgreSqlNode, SoftwareProcess {

    private static final long serialVersionUID = -6172426032214683646L;

    private static final Logger LOG = LoggerFactory.getLogger(PostgreSqlNodeChefImpl.class);

    public static final Effector<String> EXECUTE_SCRIPT = Effectors.effector(String.class, "executeScript")
            .description("invokes a script")
            .parameter(ExecuteScriptEffectorBody.SCRIPT)
            .impl(new ExecuteScriptEffectorBody()).build();
    
    private SshFeed feed;

    public void init() {
        super.init();
        new ChefPostgreSqlLifecycle().attachLifecycleEffectors(this);
    }
    
    public static class ChefPostgreSqlLifecycle extends ChefLifecycleEffectorTasks {
        {
            usePidFile("/var/run/postgresql/*.pid");
            useService("postgresql");
        }
        protected void startWithKnifeAsync() {
            Entities.warnOnIgnoringConfig(entity(), ChefConfig.CHEF_RUN_LIST);
            Entities.warnOnIgnoringConfig(entity(), ChefConfig.CHEF_LAUNCH_ATTRIBUTES);
            
            DynamicTasks.queue(
                    ChefServerTasks
                        .knifeConvergeRunList("postgresql::server")
                        .knifeAddAttributes(Jsonya
                            .at("postgresql", "config").add(
                                "port", entity().getAttribute(PostgreSqlNode.POSTGRESQL_PORT), 
                                "listen_addresses", "*").getRootMap())
                        .knifeAddAttributes(Jsonya
                            .at("postgresql", "pg_hba").list().map().add(
                                "type", "host", "db", "all", "user", "all", 
                                "addr", "0.0.0.0/0", "method", "md5").getRootMap()) 
                        // no other arguments currenty supported; chef will pick a password for us
                );
        }
        protected void postStartCustom() {
            super.postStartCustom();

            // now run the creation script
            String creationScript;
            String creationScriptUrl = entity().getConfig(PostgreSqlNode.CREATION_SCRIPT_URL);
            if (creationScriptUrl != null)
                creationScript = new ResourceUtils(entity()).getResourceAsString(creationScriptUrl);
            else creationScript = entity().getConfig(PostgreSqlNode.CREATION_SCRIPT_CONTENTS);
            entity().invoke(PostgreSqlNodeChefImpl.EXECUTE_SCRIPT, 
                    ConfigBag.newInstance().configure(ExecuteScriptEffectorBody.SCRIPT, creationScript).getAllConfig()).getUnchecked();

            // and finally connect sensors
            ((PostgreSqlNodeChefImpl)entity()).connectSensors();
        }
        protected void preStopCustom() {
            ((PostgreSqlNodeChefImpl)entity()).disconnectSensors();
            super.preStopCustom();
        }
    }
    
    public static class ExecuteScriptEffectorBody extends EffectorBody<String> {
        public static final ConfigKey<String> SCRIPT = ConfigKeys.newStringConfigKey("script", "contents of script to run");
        
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
