package brooklyn.entity.monitoring.zabbix;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.util.flags.SetFromFlag;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;

@ImplementedBy(ZabbixServerImpl.class)
public interface ZabbixServer extends Entity {

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @SetFromFlag("filter")
    ConfigKey<Predicate<? super Entity>> ENTITY_FILTER = new BasicConfigKey(Predicate.class, "zabbix.server.filter", "Filter for entities which will automatically be monitored", Predicates.instanceOf(ZabbixMonitored.class));

    @SetFromFlag("serverApiUrl")
    ConfigKey<String> ZABBIX_SERVER_API_URL = new BasicConfigKey<String>(String.class, "zabbix.server.apiUrl", "Main Zabbix server API URL");

    @SetFromFlag("username")
    ConfigKey<String> ZABBIX_SERVER_USERNAME = new BasicConfigKey<String>(String.class, "zabbix.server.username", "Zabbix server API login user");

    @SetFromFlag("password")
    ConfigKey<String> ZABBIX_SERVER_PASSWORD = new BasicConfigKey<String>(String.class, "zabbix.server.password", "Zabbix server API login password");

    ConfigKey<Integer> ZABBIX_SESSION_TIMEOUT = new BasicConfigKey<Integer>(Integer.class, "zabbix.server.sessionTimeout", "Zabbix server API session timeout period (seconds)", 3600);

    AttributeSensor<String> ZABBIX_TOKEN = new BasicAttributeSensor<String>(String.class, "zabbix.server.token", "Zabbix server API authentication token");

}
