package brooklyn.entity.webapp.jboss;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.enricher.basic.SensorTransformingEnricher;
import brooklyn.entity.Entity;
import brooklyn.entity.webapp.HttpsSslConfig;
import brooklyn.entity.webapp.JavaWebAppSoftwareProcess;
import brooklyn.entity.webapp.JavaWebAppSoftwareProcessImpl;
import brooklyn.event.AttributeSensor;
import brooklyn.event.feed.http.HttpFeed;
import brooklyn.event.feed.http.HttpPollConfig;
import brooklyn.event.feed.http.HttpValueFunctions;
import brooklyn.location.access.BrooklynAccessUtils;

import com.google.common.base.Functions;
import com.google.common.collect.ImmutableMap;
import com.google.common.net.HostAndPort;

public class JBoss7ServerImpl extends JavaWebAppSoftwareProcessImpl implements JBoss7Server {

	public static final Logger log = LoggerFactory.getLogger(JBoss7ServerImpl.class);

    private volatile HttpFeed httpFeed;
    
    public JBoss7ServerImpl(){
        super();
    }

    public JBoss7ServerImpl(Map flags){
        this(flags, null);
    }

    public JBoss7ServerImpl(Map flags, Entity parent) {
        super(flags, parent);
    }

    @Override
    public Class getDriverInterface() {
        return JBoss7Driver.class;
    }

    @Override
    public JBoss7Driver getDriver() {
        return (JBoss7Driver) super.getDriver();
    }
    
    @Override
    protected void connectSensors() {
        super.connectSensors();

        HostAndPort hp = BrooklynAccessUtils.getBrooklynAccessibleAddress(this, 
                getAttribute(MANAGEMENT_HTTP_PORT) + getConfig(PORT_INCREMENT));
        
        String managementUri = String.format("http://%s:%s/management/subsystem/web/connector/http/read-resource",
                hp.getHostText(), hp.getPort());
        log.debug("JBoss sensors for "+this+" reading from "+managementUri);
        Map<String, String> includeRuntimeUriVars = ImmutableMap.of("include-runtime","true");
        
        httpFeed = HttpFeed.builder()
                .entity(this)
                .period(200)
                .baseUri(managementUri)
                .credentials(getConfig(MANAGEMENT_USER), getConfig(MANAGEMENT_PASSWORD))
                .poll(new HttpPollConfig<Integer>(MANAGEMENT_STATUS)
                        .onSuccess(HttpValueFunctions.responseCode()))
                .poll(new HttpPollConfig<Boolean>(MANAGEMENT_URL_UP)
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
        
        connectServiceUp();
    }
    
    protected void connectServiceUp() {
        addEnricher(SensorTransformingEnricher.newInstanceTransforming(this,
            MANAGEMENT_URL_UP, Functions.<Boolean>identity(), SERVICE_UP));
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
        return getConfig(PORT_INCREMENT);
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
        return getConfig(DEPLOYMENT_TIMEOUT);
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

    @Override
    public String getShortName() {
        return "JBossAS7";
    }
}
