package brooklyn.entity.monitoring.zabbix;

import brooklyn.config.ConfigKey;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.event.basic.PortAttributeSensorAndConfigKey;
import brooklyn.util.flags.SetFromFlag;

public interface ZabbixMonitored {

    /** The entity representing the Zabbix server monitoring an entity. */
    @SetFromFlag("zabbixServer")
    ConfigKey<ZabbixServer> ZABBIX_SERVER = new BasicConfigKey<ZabbixServer>(ZabbixServer.class, "zabbix.server.entity", "Zabbix server for this entity");

    PortAttributeSensorAndConfigKey ZABBIX_AGENT_PORT = new PortAttributeSensorAndConfigKey("zabbix.agent.port", "The port the Zabbix agent is listening on", "10050+");

    AttributeSensor<String> ZABBIX_AGENT_HOSTID = new BasicAttributeSensor<String>(String.class, "zabbix.agent.hostid", "The hostId for a Zabbix monitored agent");

}