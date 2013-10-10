package brooklyn.entity.proxy;

import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.group.Cluster;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.entity.webapp.WebAppService;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.event.basic.PortAttributeSensorAndConfigKey;
import brooklyn.util.flags.SetFromFlag;

import com.google.common.collect.ImmutableList;

/**
 * Represents a controller mechanism for a {@link Cluster}.
 */
@ImplementedBy(AbstractControllerImpl.class)
public interface AbstractController extends SoftwareProcess, LoadBalancer {
    
    /** sensor for port to forward to on target entities */
    @SetFromFlag("portNumberSensor")
    public static final BasicAttributeSensorAndConfigKey<AttributeSensor> PORT_NUMBER_SENSOR = new BasicAttributeSensorAndConfigKey<AttributeSensor>(
            AttributeSensor.class, "member.sensor.portNumber", "Port number sensor on members (defaults to http.port)", Attributes.HTTP_PORT);

    /** sensor for hostname to forward to on target entities */
    @SetFromFlag("hostnameSensor")
    public static final BasicAttributeSensorAndConfigKey<AttributeSensor> HOSTNAME_SENSOR = new BasicAttributeSensorAndConfigKey<AttributeSensor>(
            AttributeSensor.class, "member.sensor.hostname", "Port number sensor on members (defaults to host.name)", Attributes.HOSTNAME);

    @SetFromFlag("port")
    /** port where this controller should live */
    public static final PortAttributeSensorAndConfigKey PROXY_HTTP_PORT = new PortAttributeSensorAndConfigKey(
            "proxy.http.port", "Main HTTP port where this proxy listens", ImmutableList.of(8000,"8001+"));
    
    @SetFromFlag("protocol")
    public static final BasicAttributeSensorAndConfigKey<String> PROTOCOL = new BasicAttributeSensorAndConfigKey<String>(
            String.class, "proxy.protocol", "Main URL protocol this proxy answers (typically http or https)", null);
    
    @SetFromFlag("domain")
    public static final BasicAttributeSensorAndConfigKey<String> DOMAIN_NAME = new BasicAttributeSensorAndConfigKey<String>(
            String.class, "proxy.domainName", "Domain name that this controller responds to, or null if it responds to all domains", null);
        
    @SetFromFlag("ssl")
    public static final BasicConfigKey<ProxySslConfig> SSL_CONFIG = 
        new BasicConfigKey<ProxySslConfig>(ProxySslConfig.class, "proxy.ssl.config", "configuration (e.g. certificates) for SSL; will use SSL if set, not use SSL if not set");

    public static final AttributeSensor<String> ROOT_URL = WebAppService.ROOT_URL;


    public boolean isActive();
    
    public String getProtocol();

    /** returns primary domain this controller responds to, or null if it responds to all domains */
    public String getDomain();
    
    public Integer getPort();

    /** primary URL this controller serves, if one can / has been inferred */
    public String getUrl();

    public AttributeSensor getPortNumberSensor();
}
