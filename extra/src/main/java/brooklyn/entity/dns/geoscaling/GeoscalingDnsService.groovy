package brooklyn.entity.dns.geoscaling

import static brooklyn.entity.dns.geoscaling.GeoscalingWebClient.*
import static com.google.common.base.Preconditions.*

import java.util.Map
import java.util.Set

import brooklyn.entity.Entity
import brooklyn.entity.dns.AbstractGeoDnsService
import brooklyn.entity.dns.HostGeoInfo
import brooklyn.entity.dns.geoscaling.GeoscalingWebClient.Domain
import brooklyn.entity.dns.geoscaling.GeoscalingWebClient.SmartSubdomain
import brooklyn.entity.trait.Configurable
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.event.basic.BasicConfigKey
import brooklyn.management.Task
import brooklyn.util.IdGenerator


class GeoscalingDnsService extends AbstractGeoDnsService implements Configurable {
    public static final BasicConfigKey RANDOMIZE_SUBDOMAIN_NAME = [ Boolean, "randomize.subdomain.name" ];
    public static final BasicConfigKey GEOSCALING_USERNAME = [ String, "geoscaling.username" ];
    public static final BasicConfigKey GEOSCALING_PASSWORD = [ String, "geoscaling.password" ];
    public static final BasicConfigKey GEOSCALING_PRIMARY_DOMAIN_NAME = [ String, "geoscaling.primary.domain.name" ];
    public static final BasicConfigKey GEOSCALING_SMART_SUBDOMAIN_NAME = [ String, "geoscaling.smart.subdomain.name" ];
    
    public static final BasicAttributeSensor GEOSCALING_ACCOUNT =
        [ String, "geoscaling.account", "Active user account for the GeoScaling.com service" ];
    public static final BasicAttributeSensor MANAGED_DOMAIN =
        [ String, "geoscaling.managed.domain", "Fully qualified domain name that will be geo-redirected" ];
    
    // Must remember any desired redirection targets if they're specified before configure() has been called.
    private Set<HostGeoInfo> rememberedTargetHosts;
    private final GeoscalingWebClient webClient = [ ];
    
    // These are available only after the configure() method has been invoked.
    private boolean randomizeSmartSubdomainName;
    private String username;
    private String password;
    private String primaryDomainName;
    private String smartSubdomainName;
    
    
    public GeoscalingDnsService(Map properties = [:], Entity owner = null) {
        super(properties, owner);
        
        setConfig(RANDOMIZE_SUBDOMAIN_NAME, true); // TODO: eventually default to non-randomized subdomains?
        setConfigIfValNonNull(RANDOMIZE_SUBDOMAIN_NAME, properties.randomizeSubdomainName);
        setConfigIfValNonNull(GEOSCALING_USERNAME, properties.username);
        setConfigIfValNonNull(GEOSCALING_PASSWORD, properties.password);
        setConfigIfValNonNull(GEOSCALING_PRIMARY_DOMAIN_NAME, properties.primaryDomainName);
        setConfigIfValNonNull(GEOSCALING_SMART_SUBDOMAIN_NAME, properties.smartSubdomainName);
    }
    
    // Ensure our configure() method gets called; may be able to remove this if/when the framework detects this
    // and invokes the configure() method automatically?
    @Override
    public void onManagementBecomingMaster() {
        super.onManagementBecomingMaster();
        configure();
    }

    public void configure() {
        if (getAttribute(SERVICE_CONFIGURED)) return;
        
        randomizeSmartSubdomainName = getConfig(RANDOMIZE_SUBDOMAIN_NAME);
        username = getConfig(GEOSCALING_USERNAME);
        password = getConfig(GEOSCALING_PASSWORD);
        primaryDomainName = getConfig(GEOSCALING_PRIMARY_DOMAIN_NAME);
        smartSubdomainName = getConfig(GEOSCALING_SMART_SUBDOMAIN_NAME);

        // Ensure all mandatory configuration is provided.
        checkNotNull(username, "The GeoScaling username is not specified");
        checkNotNull(password, "The GeoScaling password is not specified");
        checkNotNull(primaryDomainName, "The GeoScaling primary domain name is not specified");
        checkNotNull(smartSubdomainName, "The GeoScaling smart subdomain name is not specified");
        
        if (randomizeSmartSubdomainName)
            smartSubdomainName += "-"+IdGenerator.makeRandomId(8);
        
        String fullDomain = smartSubdomainName+"."+primaryDomainName;
        log.info("GeoScaling service will configure redirection for '"+fullDomain+"' domain");
        setAttribute(GEOSCALING_ACCOUNT, username);
        setAttribute(MANAGED_DOMAIN, fullDomain);
        
        setAttribute(SERVICE_CONFIGURED, true);
        if (rememberedTargetHosts != null) {
            reconfigureService(rememberedTargetHosts);
            rememberedTargetHosts = null;
        }
    }
    
    @Override
    public void destroy() {
        if (!getAttribute(SERVICE_CONFIGURED)) return;
        
        // Don't leave randomized subdomains configured on our GeoScaling account.
        if (randomizeSmartSubdomainName) {
            webClient.login(username, password);
            Domain primaryDomain = webClient.getPrimaryDomain(primaryDomainName);
            SmartSubdomain smartSubdomain = primaryDomain?.getSmartSubdomain(smartSubdomainName);
            if (smartSubdomain) {
                log.info("Deleting randomized GeoScaling smart subdomain '"+smartSubdomainName+"."+primaryDomainName+"'");
                smartSubdomain.delete();
            }
            webClient.logout();
        }
        
        super.destroy();
    }
    
    protected void reconfigureService(Set<HostGeoInfo> targetHosts) {
        if (!getAttribute(SERVICE_CONFIGURED)) {
            this.rememberedTargetHosts = targetHosts;
            return;
        }
        
        webClient.login(username, password);
        Domain primaryDomain = webClient.getPrimaryDomain(primaryDomainName);
        SmartSubdomain smartSubdomain = primaryDomain.getSmartSubdomain(smartSubdomainName);
        
        if (!smartSubdomain) {
            log.info("GeoScaling smart subdomain '"+smartSubdomainName+"."+primaryDomainName+"' does not exist, creating it now");
            primaryDomain.createSmartSubdomain(smartSubdomainName);
            smartSubdomain = primaryDomain.getSmartSubdomain(smartSubdomainName);
        }
        
        if (smartSubdomain) {
            String script = GeoscalingScriptGenerator.generateScriptString(targetHosts);
            smartSubdomain.configure(PROVIDE_CITY_INFO, script);
            
        } else
            log.warn("Failed to retrieve or create GeoScaling smart subdomain '"+smartSubdomainName+"."+primaryDomainName+
                    "', aborting attempt to configure service");
        
        webClient.logout();
    }
    
}
