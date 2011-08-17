package brooklyn.entity.dns.geoscaling

import static brooklyn.entity.dns.geoscaling.GeoscalingWebClient.*

import java.util.Map
import java.util.Set

import brooklyn.entity.Entity
import brooklyn.entity.dns.AbstractGeoDnsService
import brooklyn.entity.dns.HostGeoInfo
import brooklyn.entity.dns.geoscaling.GeoscalingWebClient.Domain
import brooklyn.entity.dns.geoscaling.GeoscalingWebClient.SmartSubdomain
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.event.basic.BasicConfigKey
import brooklyn.util.IdGenerator


class GeoscalingDnsService extends AbstractGeoDnsService {
    public static final boolean RANDOMIZE_SUBDOMAIN_NAME = true;
    public static final BasicConfigKey GEOSCALING_USERNAME = [ String, "geoscaling.username" ];
    public static final BasicConfigKey GEOSCALING_PASSWORD = [ String, "geoscaling.password" ];
    public static final BasicConfigKey GEOSCALING_PRIMARY_DOMAIN_NAME = [ String, "geoscaling.primary.domain.name" ];
    public static final BasicConfigKey GEOSCALING_SMART_SUBDOMAIN_NAME = [ String, "geoscaling.smart.subdomain.name" ];
    
    public static final BasicAttributeSensor GEOSCALING_ACCOUNT =
        [ String, "geoscaling.account", "Active user account for the GeoScaling.com service" ];
    public static final BasicAttributeSensor MANAGED_DOMAIN =
        [ String, "geoscaling.managed.domain", "Fully qualified domain name that will be geo-redirected" ];
    
    private String username;
    private String password;
    private String primaryDomainName;
    private String smartSubdomainName;
    
    
    public GeoscalingDnsService(Map properties = [:], Entity owner = null) {
        super(properties, owner);
        
        // TODO But what if config has not been set yet? e.g. user calls:
        //   def geo = GeoscalingDnsService()
        //   geo.setConfig(GEOSCALING_USERNAME, "myname")
        
        username = retrieveFromPropertyOrConfig(properties, "username", GEOSCALING_USERNAME);
        password = retrieveFromPropertyOrConfig(properties, "password", GEOSCALING_PASSWORD);
        primaryDomainName = retrieveFromPropertyOrConfig(properties, "primaryDomainName", GEOSCALING_PRIMARY_DOMAIN_NAME);
        smartSubdomainName = retrieveFromPropertyOrConfig(properties, "smartSubdomainName", GEOSCALING_SMART_SUBDOMAIN_NAME);
        
        if (RANDOMIZE_SUBDOMAIN_NAME)
            smartSubdomainName += "-"+IdGenerator.makeRandomId(8);
        
        // FIXME: complain about any missing config
        
        String fullDomain = smartSubdomainName+"."+primaryDomainName;
        log.info("GeoScaling service will configure redirection for '"+fullDomain+"' domain");
        setAttribute(GEOSCALING_ACCOUNT, username);
        setAttribute(MANAGED_DOMAIN, fullDomain);
    }
    
    @Override
    public void destroy() {
        // Don't leave randomized subdomains configured on our GeoScaling account.
        if (RANDOMIZE_SUBDOMAIN_NAME) {
            GeoscalingWebClient gwc = [ ];
            gwc.login(username, password);
            Domain primaryDomain = gwc.getPrimaryDomain(primaryDomainName);
            SmartSubdomain smartSubdomain = primaryDomain?.getSmartSubdomain(smartSubdomainName);
            if (smartSubdomain) {
                log.info("Deleting randomized GeoScaling smart subdomain '"+smartSubdomainName+"."+primaryDomainName+"'");
                smartSubdomain.delete();
            }
            gwc.logout();
        }
        
        super.destroy();
    }
    
    
    protected void reconfigureService(Set<HostGeoInfo> targetHosts) {
        String script = GeoscalingScriptGenerator.generateScriptString(targetHosts);
        
        GeoscalingWebClient gwc = [ ];
        gwc.login(username, password);
        Domain primaryDomain = gwc.getPrimaryDomain(primaryDomainName);
        SmartSubdomain smartSubdomain = primaryDomain.getSmartSubdomain(smartSubdomainName);
        
        if (!smartSubdomain) {
            log.info("GeoScaling smart subdomain '"+smartSubdomainName+"."+primaryDomainName+"' does not exist, creating it now");
            primaryDomain.createSmartSubdomain(smartSubdomainName);
            smartSubdomain = primaryDomain.getSmartSubdomain(smartSubdomainName);
        }
        
        if (smartSubdomain)
            smartSubdomain.configure(PROVIDE_CITY_INFO, script);
        else
            log.warn("Failed to retrieve or create GeoScaling smart subdomain '"+smartSubdomainName+"."+primaryDomainName+
                "', aborting attempt to configure service");
        
        gwc.logout();
    }
    
    private static <T> T retrieveFromPropertyOrConfig(Map properties, String propertyKey, BasicConfigKey<T> configKey) {
        Object p = properties.get(propertyKey);
        if (p instanceof T) return (T) p;
        return getConfig(configKey);
    }
    
}
