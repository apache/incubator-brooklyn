package brooklyn.entity.zookeeper;

import java.util.concurrent.TimeUnit;

import javax.management.ObjectName;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.SoftwareProcessImpl;
import brooklyn.entity.java.JavaSoftwareProcessDriver;
import brooklyn.event.feed.jmx.JmxAttributePollConfig;
import brooklyn.event.feed.jmx.JmxFeed;
import brooklyn.event.feed.jmx.JmxHelper;

import com.google.common.base.Functions;
import com.google.common.base.Objects.ToStringHelper;

/**
 * An {@link brooklyn.entity.Entity} that represents a single Apache ZooKeeper instance.
 */
public abstract class AbstractZooKeeperImpl extends SoftwareProcessImpl implements ZooKeeperNode {

    @SuppressWarnings("unused")
    private static final Logger log = LoggerFactory.getLogger(AbstractZooKeeperImpl.class);
    private static final ObjectName ZOOKEEPER_MBEAN = JmxHelper.createObjectName("org.apache.ZooKeeperService:name0=StandaloneServer_port-1");

    private volatile JmxFeed jmxFeed;

    public AbstractZooKeeperImpl() {
    }

    @Override
    public Integer getZookeeperPort() { return getAttribute(ZOOKEEPER_PORT); }

    @Override
    public String getHostname() { return getAttribute(HOSTNAME); }

    @Override
    public void waitForServiceUp(long duration, TimeUnit units) {
        super.waitForServiceUp(duration, units);

        if (((JavaSoftwareProcessDriver)getDriver()).isJmxEnabled()) {
            // Wait for the MBean to exist
            JmxHelper helper = new JmxHelper(this);
            try {
                helper.assertMBeanExistsEventually(ZOOKEEPER_MBEAN, units.toMillis(duration));
            } finally {
                helper.disconnect();
            }
        }
    }

    @Override
    protected void connectSensors() {
        connectServiceUpIsRunning();

        if (((JavaSoftwareProcessDriver)getDriver()).isJmxEnabled()) {
            jmxFeed = JmxFeed.builder()
                .entity(this)
                .period(500, TimeUnit.MILLISECONDS)
                .pollAttribute(new JmxAttributePollConfig<Long>(OUTSTANDING_REQUESTS)
                        .objectName(ZOOKEEPER_MBEAN)
                        .attributeName("OutstandingRequests")
                        .onFailureOrException(Functions.constant(-1l)))
                .pollAttribute(new JmxAttributePollConfig<Long>(PACKETS_RECEIVED)
                        .objectName(ZOOKEEPER_MBEAN)
                        .attributeName("PacketsReceived")
                        .onFailureOrException(Functions.constant(-1l)))
                .pollAttribute(new JmxAttributePollConfig<Long>(PACKETS_SENT)
                        .objectName(ZOOKEEPER_MBEAN)
                        .attributeName("PacketsSent")
                        .onFailureOrException(Functions.constant(-1l)))
                .build();
        }
    }

    @Override
    public void disconnectSensors() {
        super.disconnectSensors();
        disconnectServiceUpIsRunning();
        if (jmxFeed != null) jmxFeed.stop();
    }

    @Override
    protected ToStringHelper toStringHelper() {
        return super.toStringHelper()
                .add("zookeeperPort", getZookeeperPort());
    }

}
