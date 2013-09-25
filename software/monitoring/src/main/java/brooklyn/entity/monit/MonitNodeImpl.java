package brooklyn.entity.monit;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.SoftwareProcessImpl;
import brooklyn.event.feed.ssh.SshFeed;
import brooklyn.event.feed.ssh.SshPollConfig;
import brooklyn.location.Location;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.time.Duration;

import com.google.common.collect.Iterables;

public class MonitNodeImpl extends SoftwareProcessImpl implements MonitNode {
    
    private static final Logger LOG = LoggerFactory.getLogger(MonitNodeImpl.class);
    
    private SshFeed feed;
    
    public MonitNodeImpl(Map flags, Entity parent) {
        super(flags, parent);
    }

    @Override
    public Class getDriverInterface() {
        return MonitNode.class;
    }
    
    @Override
    public MonitDriver getDriver() {
        return (MonitDriver) super.getDriver();
    }

    @Override
    protected void connectSensors() {
        super.connectSensors();
        Location machine = Iterables.get(getLocations(), 0, null);
        
        if (machine instanceof SshMachineLocation) {
            String cmd = getDriver().getStatusCmd();
            feed = SshFeed.builder()
                .entity(this)
                .period(Duration.FIVE_SECONDS)
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

    @Override
    public String getShortName() {
        return "Monit";
    }
}
