package brooklyn.entity.database.postgresql;

import brooklyn.entity.basic.SoftwareProcessImpl;
import brooklyn.event.feed.ssh.SshFeed;
import brooklyn.event.feed.ssh.SshPollConfig;
import brooklyn.event.feed.ssh.SshPollValue;
import brooklyn.location.Location;
import brooklyn.location.basic.SshMachineLocation;
import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.collect.Iterables;

public class PostgreSqlNodeImpl extends SoftwareProcessImpl implements PostgreSqlNode {

    private SshFeed feed;

    public Class getDriverInterface() {
        return PostgreSqlDriver.class;
    }
    @Override
    public PostgreSqlDriver getDriver() {
        return (PostgreSqlDriver) super.getDriver();
    }
    
    @Override
    protected void connectSensors() {
        super.connectSensors();
        setAttribute(DB_URL, String.format("postgresql://%s:%s/", getAttribute(HOSTNAME), getAttribute(POSTGRESQL_PORT)));

        Location machine = Iterables.get(getLocations(), 0, null);

        if (machine instanceof SshMachineLocation) {
            String cmd = getDriver().getStatusCmd();

            feed = SshFeed.builder()
                    .entity(this)
                    .machine((SshMachineLocation)machine)
                    .poll(new SshPollConfig<Boolean>(SERVICE_UP)
                            .command(cmd)
                            .setOnSuccess(true)
                            .setOnFailureOrException(false))
                    .build();
        } else {
            LOG.warn("Location(s) %s not an ssh-machine location, so not polling for status; setting serviceUp immediately", getLocations());
            setAttribute(SERVICE_UP, true);
        }
    }

    @Override
    protected void disconnectSensors() {
        if (feed != null) feed.stop();
    }
}
