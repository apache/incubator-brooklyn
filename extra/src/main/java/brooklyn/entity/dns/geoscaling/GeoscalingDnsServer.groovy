package brooklyn.entity.dns.geoscaling

import java.util.List
import java.util.Set

import brooklyn.entity.Effector
import brooklyn.entity.Entity
import brooklyn.entity.basic.AbstractEntity
import brooklyn.entity.basic.BasicParameterType
import brooklyn.entity.basic.EffectorWithExplicitImplementation
import brooklyn.entity.dns.ServerGeoInfo
import brooklyn.event.AttributeSensor
import brooklyn.event.basic.BasicConfigKey
import brooklyn.management.SubscriptionHandle

class GeoscalingDnsServer extends AbstractEntity {
    
    public static BasicConfigKey<String> GEOSCALING_HOST = [ String.class, "geoscaling.host" ];
    public static BasicConfigKey<Integer> GEOSCALING_PORT = [ Integer.class, "geoscaling.port" ];
    public static BasicConfigKey<String> GEOSCALING_USERNAME = [ String.class, "geoscaling.username" ];
    public static BasicConfigKey<String> GEOSCALING_PASSWORD = [ String.class, "geoscaling.password" ];
    public static BasicConfigKey<Integer> GEOSCALING_PRIMARY_DOMAIN_ID = [ Integer.class, "geoscaling.primary.domain.id" ];
    public static BasicConfigKey<Integer> GEOSCALING_SMART_SUBDOMAIN_ID = [ Integer.class, "geoscaling.smart.subdomain.id" ];
    public static BasicConfigKey<String> GEOSCALING_SMART_SUBDOMAIN_NAME = [ String.class, "geoscaling.smart.subdomain.name" ];
    
    public static AttributeSensor<Set<ServerGeoInfo>> DESTINATION_SERVERS = [ Set.class, "destination.servers" ];
    
    public static Effector<String> SET_DESTINATION_SERVERS =
        new EffectorWithExplicitImplementation<GeoscalingDnsServer, Void>(
            "setDestinationServers", Void.class,
            [ new BasicParameterType<Set>("servers", Set.class, "Set of all destination servers, including address and lat/long information") ],
            "Reconfigures the GeoScaling account to redirect users to their nearest server from the passed set") {
        
        public Void invokeEffector(GeoscalingDnsServer entity, Map args) {
            Set<ServerGeoInfo> servers = args.get("servers");
            entity.setDestinationServers(servers);
            return null;
        }
    };


    public void setDestinationServers(Set<ServerGeoInfo> servers) {
        String host = getConfig(GEOSCALING_HOST);
        Integer port = getConfig(GEOSCALING_PORT);
        String username = getConfig(GEOSCALING_USERNAME);
        String password = getConfig(GEOSCALING_PASSWORD);
        Integer primaryDomainId = getConfig(GEOSCALING_PRIMARY_DOMAIN_ID);
        Integer smartSubdomainId = getConfig(GEOSCALING_SMART_SUBDOMAIN_ID);
        String smartSubdomainName = getConfig(GEOSCALING_SMART_SUBDOMAIN_NAME);
        
        // TODO: complain if required config is missing
        
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
        
        emit(DESTINATION_SERVERS, servers);
    }
    
}
