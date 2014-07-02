package brooklyn.entity.messaging.kafka;

import java.util.Map;

import brooklyn.config.ConfigKey;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.collections.MutableMap;

public class KafkaZooKeeperSshDriver extends AbstractfKafkaSshDriver implements KafkaZooKeeperDriver {

    public KafkaZooKeeperSshDriver(KafkaZooKeeperImpl entity, SshMachineLocation machine) {
        super(entity, machine);
    }

    @Override
    protected Map<String, Integer> getPortMap() {
        return MutableMap.of("zookeeperPort", getZookeeperPort());
    }

    @Override
    protected ConfigKey<String> getConfigTemplateKey() {
        return KafkaZooKeeper.KAFKA_ZOOKEEPER_CONFIG_TEMPLATE;
    }

    @Override
    protected String getConfigFileName() {
        return "zookeeper.properties";
    }

    @Override
    protected String getLaunchScriptName() {
        return "zookeeper-server-start.sh";
    }

    @Override
    protected String getProcessIdentifier() {
        return "quorum\\.QuorumPeerMain";
    }

    @Override
    public Integer getZookeeperPort() {
        return getEntity().getAttribute(KafkaZooKeeper.ZOOKEEPER_PORT);
    }

}
