package brooklyn.entity.zookeeper;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.group.AbstractMembershipTrackingPolicy;
import brooklyn.entity.group.DynamicClusterImpl;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.trait.Startable;
import brooklyn.location.Location;
import brooklyn.util.collections.MutableMap;
import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class ZooKeeperEnsembleImpl extends DynamicClusterImpl implements ZooKeeperEnsemble {

    private static final Logger log = LoggerFactory.getLogger(ZooKeeperEnsembleImpl.class);
    private static final AtomicInteger myId = new AtomicInteger();

    public ZooKeeperEnsembleImpl() {}

    /**
     * Sets the default {@link #MEMBER_SPEC} to describe the ZooKeeper nodes.
     */
    @Override
    protected EntitySpec<?> getMemberSpec() {
        return getConfig(MEMBER_SPEC, EntitySpec.create(ZooKeeperNode.class));
    }

    @Override
    public String getClusterName() {
        return getAttribute(CLUSTER_NAME);
    }

    @Override
    public void init() {
        log.info("Initializing the ZooKeeper Ensemble");
        super.init();

        AbstractMembershipTrackingPolicy policy = new AbstractMembershipTrackingPolicy(MutableMap.of("name", "Members tracker")) {
            @Override
            protected void onEntityChange(Entity member) {
            }

            @Override
            protected void onEntityAdded(Entity member) {
                if (member.getAttribute(ZooKeeperNode.MY_ID) == null) {
                    ((EntityInternal) member).setAttribute(ZooKeeperNode.MY_ID, myId.incrementAndGet());
                }
                setAttribute(SERVICE_UP, calculateServiceUp());
            }

            @Override
            protected void onEntityRemoved(Entity member) {
                setAttribute(SERVICE_UP, calculateServiceUp());
            }
        };
        addPolicy(policy);
        policy.setGroup(this);

    }

    @Override
    public synchronized boolean addMember(Entity member) {
        boolean result = super.addMember(member);
        setAttribute(SERVICE_UP, calculateServiceUp());
        return result;
    }

    @Override
    public void start(Collection<? extends Location> locations) {
        super.start(locations);
        setAttribute(Startable.SERVICE_UP, calculateServiceUp());
        List<String> zookeeperServers = Lists.newArrayList();
        for (Entity zookeeper : getMembers()) {
            zookeeperServers.add(zookeeper.getAttribute(Attributes.HOSTNAME));
        }
        setAttribute(ZOOKEEPER_SERVERS, zookeeperServers);
    }

    @Override
    protected boolean calculateServiceUp() {
        boolean up = false;
        for (Entity member : getMembers()) {
            if (Boolean.TRUE.equals(member.getAttribute(SERVICE_UP))) up = true;
        }
        return up;
    }

}
