package brooklyn.entity.dns.geoscaling

import java.util.Set

import brooklyn.entity.basic.AbstractEntity
import brooklyn.entity.dns.ServerGeoInfo
import brooklyn.event.AttributeSensor
import brooklyn.event.basic.BasicConfigKey

class GeoscalingDnsServer extends AbstractEntity {
    
    public static BasicConfigKey<String> GEOSCALING_HOST = [ String.class, "geoscaling.host" ];
    public static BasicConfigKey<Integer> GEOSCALING_PORT = [ Integer.class, "geoscaling.port" ];
    public static BasicConfigKey<String> GEOSCALING_USERNAME = [ String.class, "geoscaling.username" ];
    public static BasicConfigKey<String> GEOSCALING_PASSWORD = [ String.class, "geoscaling.password" ];
    public static BasicConfigKey<Integer> GEOSCALING_DOMAIN_ID = [ Integer.class, "geoscaling.domain.id" ];
    public static BasicConfigKey<Integer> GEOSCALING_SMART_SUBDOMAIN_ID = [ Integer.class, "geoscaling.smart.subdomain.id" ];
    public static BasicConfigKey<String> GEOSCALING_SMART_SUBDOMAIN_NAME = [ String.class, "geoscaling.smart.subdomain.name" ];
    
    public static AttributeSensor<Set<ServerGeoInfo>> DESTINATION_SERVERS = [ Set.class, "destination.servers" ];
    
    
    // TODO: expose as effector
    public boolean setDestinationServers(Set<ServerGeoInfo> servers) {
        String host = getConfig(GEOSCALING_HOST);
        int port = getConfig(GEOSCALING_PORT);
        String username = getConfig(GEOSCALING_USERNAME);
        String password = getConfig(GEOSCALING_PASSWORD);
        int primaryDomainId = getConfig(GEOSCALING_DOMAIN_ID);
        int smartSubdomainId = getConfig(GEOSCALING_SMART_SUBDOMAIN_ID);
        String smartSubdomainName = getConfig(GEOSCALING_SMART_SUBDOMAIN_NAME);
        
        String script = GeoscalingScriptGenerator.generateScriptString(servers);
        GeoscalingWebClient gwc = [ host, port ];
        gwc.login(username, password);
        gwc.configureSmartSubdomain(primaryDomainId, smartSubdomainId, smartSubdomainName,
            false, // provide network info
            true,  // provide city info
            false, // provide country info
            false, // provide "extra" info
            false, // provide uptime info
            script);
        gwc.logout();
    }
    
}
