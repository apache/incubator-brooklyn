package brooklyn.entity.zookeeper;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.group.DynamicClusterImpl;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.trait.Startable;
import brooklyn.location.Location;
import com.google.common.collect.Lists;

import java.util.Collection;
import java.util.List;

public class ZooKeeperEnsembleImpl extends DynamicClusterImpl implements ZooKeeperEnsemble {

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
    public void start(Collection<? extends Location> locations) {
        super.start(locations);

        setAttribute(Startable.SERVICE_UP, calculateServiceUp());
        List<String> zookeeperServers = Lists.newArrayList();
        for(Entity zookeeper : getMembers()) {
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
