package brooklyn.entity.dns.geoscaling

import java.util.Map;
import java.util.Set

import brooklyn.entity.Entity
import brooklyn.entity.basic.AbstractEntity
import brooklyn.entity.basic.AbstractGroup
import brooklyn.entity.dns.AbstractGeoDnsService
import brooklyn.entity.dns.HostGeoInfo
import brooklyn.entity.dns.geoscaling.GeoscalingWebClient.Domain
import brooklyn.entity.dns.geoscaling.GeoscalingWebClient.SmartSubdomain
import brooklyn.event.AttributeSensor
import brooklyn.event.basic.BasicConfigKey


class GeoscalingDnsService extends AbstractGeoDnsService {
    
    public static BasicConfigKey<String> GEOSCALING_USERNAME = [ String.class, "geoscaling.username" ];
    public static BasicConfigKey<String> GEOSCALING_PASSWORD = [ String.class, "geoscaling.password" ];
    public static BasicConfigKey<String> GEOSCALING_PRIMARY_DOMAIN_NAME = [ String.class, "geoscaling.primary.domain.name" ];
    public static BasicConfigKey<String> GEOSCALING_SMART_SUBDOMAIN_NAME = [ String.class, "geoscaling.smart.subdomain.name" ];
    
    private String username;
    private String password;
    private String primaryDomainName;
    private String smartSubdomainName;

    
    public GeoscalingDnsService(Map properties = [:], Entity owner = null) {
        super(properties, owner);
        
        username = getConfig(GEOSCALING_USERNAME);
        password = getConfig(GEOSCALING_PASSWORD);
        primaryDomainName = getConfig(GEOSCALING_PRIMARY_DOMAIN_NAME);
        smartSubdomainName = getConfig(GEOSCALING_SMART_SUBDOMAIN_NAME);
        
        // FIXME: complain about any missing config
    }

    protected void reconfigureService(Set<HostGeoInfo> targetHosts) {
        String script = GeoscalingScriptGenerator.generateScriptString(targetHosts);
        
        GeoscalingWebClient gwc = [ ];
        gwc.login(username, password);
        Domain primaryDomain = gwc.getPrimaryDomain(primaryDomainName);
        SmartSubdomain smartSubdomain = primaryDomain.getSmartSubdomain(smartSubdomainName);
        
        smartSubdomain.configure(
                false, // provide network info
                true,  // provide city info
                false, // provide country info
                false, // provide "extra" info
                false, // provide uptime info
                script);
        
        gwc.logout();
    }
    
}
