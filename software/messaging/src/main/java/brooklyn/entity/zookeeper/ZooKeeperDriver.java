package brooklyn.entity.zookeeper;

import brooklyn.entity.java.JavaSoftwareProcessDriver;

public interface ZooKeeperDriver extends JavaSoftwareProcessDriver {

    Integer getZooKeeperPort();

}
