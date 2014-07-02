package brooklyn.entity.zookeeper;

/**
 * An {@link brooklyn.entity.Entity} that represents a single standalone zookeeper instance.
 */
public class ZooKeeperNodeImpl extends AbstractZooKeeperImpl implements ZooKeeperNode {

    public ZooKeeperNodeImpl() {}

    @Override
    public Class<?> getDriverInterface() {
        return ZooKeeperDriver.class;
    }

}
