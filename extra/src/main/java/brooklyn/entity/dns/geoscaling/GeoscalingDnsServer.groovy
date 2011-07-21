package brooklyn.entity.dns.geoscaling

import java.util.Set

import brooklyn.entity.Entity
import brooklyn.entity.basic.AbstractEntity
import brooklyn.entity.basic.AbstractGroup
import brooklyn.entity.dns.HostGeoInfo
import brooklyn.entity.group.AbstractMembershipTrackingPolicy;
import brooklyn.event.AttributeSensor
import brooklyn.event.basic.BasicConfigKey


class GeoscalingDnsServer extends AbstractEntity {
    
    public static BasicConfigKey<String> GEOSCALING_HOST = [ String.class, "geoscaling.host" ];
    public static BasicConfigKey<Integer> GEOSCALING_PORT = [ Integer.class, "geoscaling.port" ];
    public static BasicConfigKey<String> GEOSCALING_USERNAME = [ String.class, "geoscaling.username" ];
    public static BasicConfigKey<String> GEOSCALING_PASSWORD = [ String.class, "geoscaling.password" ];
    public static BasicConfigKey<Integer> GEOSCALING_PRIMARY_DOMAIN_ID = [ Integer.class, "geoscaling.primary.domain.id" ];
    public static BasicConfigKey<Integer> GEOSCALING_SMART_SUBDOMAIN_ID = [ Integer.class, "geoscaling.smart.subdomain.id" ];
    public static BasicConfigKey<String> GEOSCALING_SMART_SUBDOMAIN_NAME = [ String.class, "geoscaling.smart.subdomain.name" ];
    
    public static AttributeSensor<Set<HostGeoInfo>> TARGET_HOSTS = [ Set.class, "target.hosts" ];
    
    private final Map<Entity,HostGeoInfo> targetHosts = new HashMap<Entity,HostGeoInfo>();
    

    public GeoscalingDnsServer(AbstractGroup group) {
        addPolicy(new AbstractMembershipTrackingPolicy(group) {
            protected void onEntityAdded(Entity entity) { addTargetHost(entity); }
            protected void onEntityRemoved(Entity entity) { removeTargetHost(entity); }
        });
    }

    private void addTargetHost(Entity e) {
        if (targetHosts.containsKey(e)) return;
        targetHosts.put(e, HostGeoInfo.fromEntity(e));
        update();
    }

    private void removeTargetHost(Entity e) {
        if (targetHosts.remove(e))
            update();
    }
    
    private void update() {
        String host = getConfig(GEOSCALING_HOST);
        Integer port = getConfig(GEOSCALING_PORT);
        String username = getConfig(GEOSCALING_USERNAME);
        String password = getConfig(GEOSCALING_PASSWORD);
        Integer primaryDomainId = getConfig(GEOSCALING_PRIMARY_DOMAIN_ID);
        Integer smartSubdomainId = getConfig(GEOSCALING_SMART_SUBDOMAIN_ID);
        String smartSubdomainName = getConfig(GEOSCALING_SMART_SUBDOMAIN_NAME);
        
        // TODO: complain if required config is missing
        
        configureGeoscalingService(host, port, username, password,
            primaryDomainId, smartSubdomainId, smartSubdomainName, targetHosts);
        
        emit(TARGET_HOSTS, targetHosts);
    }
    
    private static void configureGeoscalingService(String host, int port, String username, String password,
        int primaryDomainId, int smartSubdomainId, String smartSubdomainName, Set<HostGeoInfo> targetHosts) {
        
        String script = GeoscalingScriptGenerator.generateScriptString(targetHosts);
        
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
