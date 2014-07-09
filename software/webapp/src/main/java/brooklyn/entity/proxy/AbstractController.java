package brooklyn.entity.proxy;

import java.util.Set;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.group.Cluster;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.util.flags.SetFromFlag;

/**
 * Represents a controller mechanism for a {@link Cluster}.
 */
@ImplementedBy(AbstractControllerImpl.class)
public interface AbstractController extends SoftwareProcess, LoadBalancer {

    @SetFromFlag("domain")
    BasicAttributeSensorAndConfigKey<String> DOMAIN_NAME = new BasicAttributeSensorAndConfigKey<String>(
            String.class, "proxy.domainName", "Domain name that this controller responds to, or null if it responds to all domains", null);

    @SetFromFlag("ssl")
    ConfigKey<ProxySslConfig> SSL_CONFIG = ConfigKeys.newConfigKey(ProxySslConfig.class,
            "proxy.ssl.config", "configuration (e.g. certificates) for SSL; will use SSL if set, not use SSL if not set");
    

    @SetFromFlag("serviceUpUrlPath")
    ConfigKey<String> SERVICE_UP_URL_PATH = ConfigKeys.newStringConfigKey(
            "controller.config.serviceUpUrlPath", "The path that will be appended to the root URL to determine SERVICE_UP", "");

    boolean isActive();

    ProxySslConfig getSslConfig();

    boolean isSsl();

    String getProtocol();

    /** returns primary domain this controller responds to, or null if it responds to all domains */
    String getDomain();

    Integer getPort();

    /** primary URL this controller serves, if one can / has been inferred */
    String getUrl();

    AttributeSensor<Integer> getPortNumberSensor();

    AttributeSensor<String> getHostnameSensor();

    AttributeSensor<String> getHostAndPortSensor();
    
    Set<String> getServerPoolAddresses();
}
