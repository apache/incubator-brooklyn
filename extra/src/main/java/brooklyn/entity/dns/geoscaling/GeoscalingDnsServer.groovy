package brooklyn.entity.dns.geoscaling

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


class GeoscalingDnsServer extends AbstractGeoDnsService {
    
    public static BasicConfigKey<String> GEOSCALING_PROTOCOL = [ String.class, "geoscaling.protocol" ];
    public static BasicConfigKey<String> GEOSCALING_HOST = [ String.class, "geoscaling.host" ];
    public static BasicConfigKey<Integer> GEOSCALING_PORT = [ Integer.class, "geoscaling.port" ];
    public static BasicConfigKey<String> GEOSCALING_USERNAME = [ String.class, "geoscaling.username" ];
    public static BasicConfigKey<String> GEOSCALING_PASSWORD = [ String.class, "geoscaling.password" ];
    public static BasicConfigKey<String> GEOSCALING_PRIMARY_DOMAIN_NAME = [ String.class, "geoscaling.primary.domain.name" ];
    public static BasicConfigKey<String> GEOSCALING_SMART_SUBDOMAIN_NAME = [ String.class, "geoscaling.smart.subdomain.name" ];
    

    public GeoscalingDnsServer(Entity owner, AbstractGroup group) {
        super(owner, group);
    }

    protected void reconfigureService(Set<HostGeoInfo> targetHosts) {
        String protocol = getConfig(GEOSCALING_PROTOCOL);
        String host = getConfig(GEOSCALING_HOST);
        Integer port = getConfig(GEOSCALING_PORT);
        String username = getConfig(GEOSCALING_USERNAME);
        String password = getConfig(GEOSCALING_PASSWORD);
        String primaryDomainName = getConfig(GEOSCALING_PRIMARY_DOMAIN_NAME);
        String smartSubdomainName = getConfig(GEOSCALING_SMART_SUBDOMAIN_NAME);
        
        protocol = protocol ?: GeoscalingWebClient.DEFAULT_PROTOCOL;
        host = host ?: GeoscalingWebClient.DEFAULT_HOST;
        port = port ?: GeoscalingWebClient.DEFAULT_PORT;
        // TODO: complain if required config is missing
        
        String script = GeoscalingScriptGenerator.generateScriptString(targetHosts);
        
        GeoscalingWebClient gwc = [ protocol, host, port ];
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
