package brooklyn.entity.database.mysql;

import java.util.Map;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.SoftwareProcessImpl;
import brooklyn.event.feed.ssh.SshFeed;
import brooklyn.event.feed.ssh.SshPollConfig;
import brooklyn.event.feed.ssh.SshPollValue;
import brooklyn.location.Location;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.text.Identifiers;
import brooklyn.util.text.Strings;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.collect.Iterables;

public class MySqlNodeImpl extends SoftwareProcessImpl implements MySqlNode {

    private SshFeed feed;

    public MySqlNodeImpl() {
    }

    public MySqlNodeImpl(Entity parent) {
        this(MutableMap.of(), parent);
    }

    public MySqlNodeImpl(Map flags) {
        super(flags, null);
    }

    public MySqlNodeImpl(Map flags, Entity parent) {
        super(flags, parent);
    }

    @Override
    public Class getDriverInterface() {
        return MySqlDriver.class;
    }

    @Override
    public MySqlDriver getDriver() {
        return (MySqlDriver) super.getDriver();
    }
    
    @Override
    protected void connectSensors() {
        super.connectSensors();
        setAttribute(DB_URL, String.format("mysql://%s:%s/", getAttribute(HOSTNAME), getAttribute(MYSQL_PORT)));
        
        /*        
         * TODO status gives us things like:
         *   Uptime: 2427  Threads: 1  Questions: 581  Slow queries: 0  Opens: 53  Flush tables: 1  Open tables: 35  Queries per second avg: 0.239
         * So can extract lots of sensors from that.
         */
        Location machine = Iterables.get(getLocations(), 0, null);
        
        if (machine instanceof SshMachineLocation) {
            String cmd = getDriver().getStatusCmd();
            
            feed = SshFeed.builder()
                    .entity(this)
                    .machine((SshMachineLocation)machine)
                    .poll(new SshPollConfig<Boolean>(SERVICE_UP)
                            .command(cmd)
                            .onSuccess(Functions.constant(true))
                            .onFailure(Functions.constant(false))
                            .onException(Functions.constant(false)))
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

    public int getPort() {
        return getAttribute(MYSQL_PORT);
    }
    
    public String getSocketUid() {
        String result = getAttribute(MySqlNode.SOCKET_UID);
        if (Strings.isBlank(result)) {
            result = Identifiers.makeRandomId(6);
            setAttribute(MySqlNode.SOCKET_UID, result);
        }
        return result;
    }

    public String getPassword() {
        String result = getAttribute(MySqlNode.PASSWORD);
        if (Strings.isBlank(result)) {
            result = Identifiers.makeRandomId(6);
            setAttribute(MySqlNode.PASSWORD, result);
        }
        return result;
    }
    
    @Override
    public String getShortName() {
        return "MySQL";
    }
}
