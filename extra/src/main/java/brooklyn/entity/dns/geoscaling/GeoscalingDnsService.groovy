package brooklyn.entity.dns.geoscaling

import brooklyn.entity.dns.AbstractGeoDnsService

import java.util.Map
import java.util.Set

import brooklyn.entity.Entity
import brooklyn.entity.dns.AbstractGeoDnsService
import brooklyn.entity.dns.HostGeoInfo
import brooklyn.entity.dns.geoscaling.GeoscalingWebClient.Domain
import brooklyn.entity.dns.geoscaling.GeoscalingWebClient.SmartSubdomain
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.event.basic.BasicConfigKey
import brooklyn.event.basic.ConfiguredAttributeSensor


class GeoscalingDnsService extends AbstractGeoDnsService {
    
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
        
        username = retrieveFromPropertyOrConfig(properties, "username", GEOSCALING_USERNAME);
        password = retrieveFromPropertyOrConfig(properties, "password", GEOSCALING_PASSWORD);
        primaryDomainName = retrieveFromPropertyOrConfig(properties, "primaryDomainName", GEOSCALING_PRIMARY_DOMAIN_NAME);
        smartSubdomainName = retrieveFromPropertyOrConfig(properties, "smartSubdomainName", GEOSCALING_SMART_SUBDOMAIN_NAME);
        
        // FIXME: complain about any missing config
        
        setAttribute(GEOSCALING_ACCOUNT, username);
        setAttribute(MANAGED_DOMAIN, smartSubdomainName+"."+primaryDomainName);
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
        
        smartSubdomain.configure(
                false, // provide network info
                true,  // provide city info
                false, // provide country info
                false, // provide "extra" info
                false, // provide uptime info
                script);
        
        gwc.logout();
    }
    
    private static <T> T retrieveFromPropertyOrConfig(Map properties, String propertyKey, BasicConfigKey<T> configKey) {
        Object p = properties.get(propertyKey);
        if (p instanceof T) return (T) p;
        return getConfig(configKey);
    }

}
