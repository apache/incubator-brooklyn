package brooklyn.entity.webapp.jboss;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.SoftwareProcessEntity;
import brooklyn.entity.webapp.HttpsSslConfig;
import brooklyn.entity.webapp.JavaWebAppService;
import brooklyn.entity.webapp.JavaWebAppSoftwareProcessImpl;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.event.basic.PortAttributeSensorAndConfigKey;
import brooklyn.event.feed.http.HttpFeed;
import brooklyn.event.feed.http.HttpPollConfig;
import brooklyn.event.feed.http.HttpValueFunctions;
import brooklyn.util.flags.SetFromFlag;

import com.google.common.base.Functions;
import com.google.common.collect.ImmutableMap;

public class JBoss7Server extends JavaWebAppSoftwareProcessImpl implements JavaWebAppService {

	public static final Logger log = LoggerFactory.getLogger(JBoss7Server.class);

    @SetFromFlag("version")
    public static final BasicConfigKey<String> SUGGESTED_VERSION =
            new BasicConfigKey<String>(SoftwareProcessEntity.SUGGESTED_VERSION, "7.1.1.Final");
    // note: 7.1.2.Final fixes many bugs but is not available for download,
    // see https://community.jboss.org/thread/197780
    // 7.2.0.Final should be out during Q3 2012

    @SetFromFlag("bindAddress")
    public static final BasicAttributeSensorAndConfigKey<String> BIND_ADDRESS =
            new BasicAttributeSensorAndConfigKey<String>(String.class, "jboss.bind.address", 
                "Address of interface JBoss should listen on, defaulting 0.0.0.0 (but could set e.g. to attributeWhenReady(HOSTNAME)", 
                "0.0.0.0");

    @SetFromFlag("managementHttpPort")
    public static final PortAttributeSensorAndConfigKey MANAGEMENT_HTTP_PORT =
            new PortAttributeSensorAndConfigKey("webapp.jboss.managementHttpPort", "Management port", "9990+");

    /**
     * @deprecated since 1.5; use MANAGEMENT_HTTP_PORT instead
     */
    @SetFromFlag("managementPort")
    public static final PortAttributeSensorAndConfigKey MANAGEMENT_PORT = MANAGEMENT_HTTP_PORT;

    @SetFromFlag("managementHttpsPort")
    public static final PortAttributeSensorAndConfigKey MANAGEMENT_HTTPS_PORT =
            new PortAttributeSensorAndConfigKey("webapp.jboss.managementHttpPort", "Management port", "9443+");

    @SetFromFlag("managementNativePort")
    public static final PortAttributeSensorAndConfigKey MANAGEMENT_NATIVE_PORT =
            new PortAttributeSensorAndConfigKey("webapp.jboss.managementNativePort", "Management native port", "10999+");

    @SetFromFlag("portIncrement")
    public static final BasicAttributeSensorAndConfigKey<Integer> PORT_INCREMENT =
            new BasicAttributeSensorAndConfigKey<Integer>(Integer.class, "webapp.jboss.portIncrement", "Port increment, for all ports in config file", 0);

    @SetFromFlag("deploymentTimeout")
    public static final BasicAttributeSensorAndConfigKey<Integer> DEPLOYMENT_TIMEOUT =
            new BasicAttributeSensorAndConfigKey<Integer>(Integer.class, "webapp.jboss.deploymentTimeout", "Deployment timeout, in seconds", 600);
    
    public static final BasicAttributeSensorAndConfigKey<String> TEMPLATE_CONFIGURATION_URL = new BasicAttributeSensorAndConfigKey<String>(
            String.class, "webapp.jboss.templateConfigurationUrl", "Template file (in freemarker format) for the standalone.xml file", 
            "classpath://brooklyn/entity/webapp/jboss/jboss7-standalone.xml");

    public static final BasicAttributeSensor<Integer> MANAGEMENT_STATUS =
            new BasicAttributeSensor<Integer>(Integer.class, "webapp.jboss.managementStatus", "HTTP response code for the management server");

    private HttpFeed httpFeed;
    
    public JBoss7Server(Map flags){
        this(flags, null);
    }

    public JBoss7Server(Map flags, Entity parent) {
        super(flags, parent);
    }

    @Override
    public Class getDriverInterface() {
        return JBoss7Driver.class;
    }

    public JBoss7Driver getDriver() {
        return (JBoss7Driver) super.getDriver();
    }
    
    @Override
    protected void connectSensors() {
        super.connectSensors();

        String host = getAttribute(HOSTNAME);
        int port = getAttribute(MANAGEMENT_HTTP_PORT) + getAttribute(PORT_INCREMENT);

        String managementUri = String.format("http://%s:%s/management/subsystem/web/connector/http/read-resource", host, port);
        Map<String,String> includeRuntimeUriVars = ImmutableMap.of("include-runtime","true");

        httpFeed = HttpFeed.builder()
                .entity(this)
                .period(200)
                .baseUri(managementUri)
                .poll(new HttpPollConfig<Integer>(MANAGEMENT_STATUS)
                        .onSuccess(HttpValueFunctions.responseCode()))
                .poll(new HttpPollConfig<Boolean>(SERVICE_UP)
                        .onSuccess(HttpValueFunctions.responseCodeEquals(200))
                        .onError(Functions.constant(false)))
                .poll(new HttpPollConfig<Integer>(REQUEST_COUNT)
                        .vars(includeRuntimeUriVars)
                        .onSuccess(HttpValueFunctions.jsonContents("requestCount", Integer.class)))
                .poll(new HttpPollConfig<Integer>(ERROR_COUNT)
                        .vars(includeRuntimeUriVars)
                        .onSuccess(HttpValueFunctions.jsonContents("errorCount", Integer.class)))
                .poll(new HttpPollConfig<Integer>(TOTAL_PROCESSING_TIME)
                        .vars(includeRuntimeUriVars)
                        .onSuccess(HttpValueFunctions.jsonContents("processingTime", Integer.class)))
                .poll(new HttpPollConfig<Integer>(MAX_PROCESSING_TIME)
                        .vars(includeRuntimeUriVars)
                        .onSuccess(HttpValueFunctions.jsonContents("maxTime", Integer.class)))
                .poll(new HttpPollConfig<Long>(BYTES_RECEIVED)
                        .vars(includeRuntimeUriVars)
                        .onSuccess(HttpValueFunctions.jsonContents("bytesReceived", Long.class)))
                .poll(new HttpPollConfig<Long>(BYTES_SENT)
                        .vars(includeRuntimeUriVars)
                        .onSuccess(HttpValueFunctions.jsonContents("bytesSent", Long.class)))
                .build();
    }
    
    @Override
    protected void disconnectSensors() {
        super.disconnectSensors();
        
        if (httpFeed != null) httpFeed.stop();
    }

    public int getManagementHttpsPort() {
        return getAttribute(MANAGEMENT_HTTPS_PORT);
    }
    
    public int getManagementHttpPort() {
        return getAttribute(MANAGEMENT_HTTP_PORT);
    }
    
    public int getManagementNativePort() {
        return getAttribute(MANAGEMENT_NATIVE_PORT);
    }
    
    public int getPortOffset() {
        return getAttribute(PORT_INCREMENT);
    }
    
    public boolean isWelcomeRootEnabled() {
        return false;
    }

    public String getBindAddress() {
        return getConfig(BIND_ADDRESS);
    }
    
    public String getManagementBindAddress() {
        return getConfig(BIND_ADDRESS);
    }
    
    public String getUnsecureBindAddress() {
        return getConfig(BIND_ADDRESS);
    }
    
    // If empty-string, disables Management security (!) by excluding the security-realm attribute
    public String getHttpManagementInterfaceSecurityRealm() {
        return "";
    }

    public int getDeploymentTimeoutSecs() {
        return getAttribute(DEPLOYMENT_TIMEOUT);
    }

    public boolean isHttpEnabled() {
        return isProtocolEnabled("HTTP");
    }
    
    public boolean isHttpsEnabled() {
        return isProtocolEnabled("HTTPS");
    }
    
    public Integer getHttpPort() {
        return getAttribute(HTTP_PORT);
    }
    
    public Integer getHttpsPort() {
        return getAttribute(HTTPS_PORT);
    }
    
    public String getHttpsSslKeyAlias() {
        HttpsSslConfig config = getAttribute(HTTPS_SSL_CONFIG);
        return (config == null) ? null : config.getKeyAlias();
    }
    
    public String getHttpsSslKeystorePassword() {
        HttpsSslConfig config = getAttribute(HTTPS_SSL_CONFIG);
        return (config == null) ? null : config.getKeystorePassword();
    }
    
    /** Path of the keystore file on the AS7 server */
    public String getHttpsSslKeystoreFile() {
        return getDriver().getSslKeystoreFile();
    }
    
    protected boolean isProtocolEnabled(String protocol) {
        List<String> protocols = getAttribute(JavaWebAppSoftwareProcess.ENABLED_PROTOCOLS);
        for (String contender : protocols) {
            if (protocol.equalsIgnoreCase(contender)) {
                return true;
            }
        }
        return false;
    }
}
