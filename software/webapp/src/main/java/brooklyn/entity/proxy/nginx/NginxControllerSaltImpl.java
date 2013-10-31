package brooklyn.entity.proxy.nginx;

import java.util.Map;

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
import brooklyn.entity.proxy.ProxySslConfig;
import brooklyn.entity.software.SshEffectorTasks;
import brooklyn.event.AttributeSensor;
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

public class NginxControllerSaltImpl extends EffectorStartableImpl implements NginxController, SoftwareProcess {

    private static final Logger LOG = LoggerFactory.getLogger(NginxControllerSaltImpl.class);

    public static final Effector<String> EXECUTE_SCRIPT = Effectors.effector(String.class, "executeScript")
            .description("invokes a script")
            .parameter(ExecuteScriptEffectorBody.SCRIPT)
            .impl(new ExecuteScriptEffectorBody()).build();

    private SshFeed feed;

    @Override
    public void init() {
        super.init();
        new SaltNginxControllerLifecycle().attachLifecycleEffectors(this);
    }

    public static class SaltNginxControllerLifecycle extends SaltLifecycleEffectorTasks {
        public SaltNginxControllerLifecycle() {
            usePidFile("/var/run/postgresql/*.pid");
            useService("postgresql");
        }

        @Override
        protected void startMinionAsync() {
            Entities.warnOnIgnoringConfig(entity(), SaltConfig.SALT_FORMULAS);
            Entities.warnOnIgnoringConfig(entity(), SaltConfig.SALT_RUN_LIST);
            Entities.warnOnIgnoringConfig(entity(), SaltConfig.SALT_LAUNCH_ATTRIBUTES);

            // TODO Set these as defaults, rather than replacing user's value!?
            SaltConfigs.addToFormulas(entity(), "postgres", "https://github.com/saltstack-formulas/postgres-formula/archive/master.tar.gz");
            SaltConfigs.addToRunList(entity(), "postgres");
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
            entity().invoke(NginxControllerSaltImpl.EXECUTE_SCRIPT,
                    ConfigBag.newInstance().configure(ExecuteScriptEffectorBody.SCRIPT, creationScript).getAllConfig()).getUnchecked();

            // and finally connect sensors
            ((NginxControllerSaltImpl)entity()).connectSensors();
        }

        @Override
        protected void preStopCustom() {
            ((NginxControllerSaltImpl)entity()).disconnectSensors();
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

    @Override
    public boolean isActive() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public String getProtocol() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String getDomain() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Integer getPort() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String getUrl() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public AttributeSensor getPortNumberSensor() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    @brooklyn.entity.annotation.Effector(description = "Forces reload of the configuration")
    public void reload() {
        // TODO Auto-generated method stub
        
    }

    @Override
    @brooklyn.entity.annotation.Effector(description = "Updates the entities configuration, and then forces reload of that configuration")
    public void update() {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void bind(Map flags) {
        // TODO Auto-generated method stub
        
    }

    @Override
    public String getShortName() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean isSticky() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    @brooklyn.entity.annotation.Effector(description = "Gets the current server configuration (by brooklyn recalculating what the config should be); does not affect the server")
    public String getCurrentConfiguration() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public String getConfigFile() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public boolean appendSslConfig(String id, StringBuilder out, String prefix, ProxySslConfig ssl, boolean sslBlock, boolean certificateBlock) {
        // TODO Auto-generated method stub
        return false;
    }

}
