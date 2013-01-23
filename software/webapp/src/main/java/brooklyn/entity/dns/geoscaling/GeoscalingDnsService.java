package brooklyn.entity.dns.geoscaling;

import brooklyn.entity.dns.AbstractGeoDnsService;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.util.flags.SetFromFlag;

@ImplementedBy(GeoscalingDnsServiceImpl.class)
public interface GeoscalingDnsService extends AbstractGeoDnsService {
    
    @SetFromFlag("randomizeSubdomainName")
    public static final BasicConfigKey<Boolean> RANDOMIZE_SUBDOMAIN_NAME = new BasicConfigKey<Boolean>(
            Boolean.class, "randomize.subdomain.name");
    @SetFromFlag("username")
    public static final BasicConfigKey<String> GEOSCALING_USERNAME = new BasicConfigKey<String>(
            String.class, "geoscaling.username");
    @SetFromFlag("password")
    public static final BasicConfigKey<String> GEOSCALING_PASSWORD = new BasicConfigKey<String>(
            String.class, "geoscaling.password");
    @SetFromFlag("primaryDomainName")
    public static final BasicConfigKey<String> GEOSCALING_PRIMARY_DOMAIN_NAME = new BasicConfigKey<String>(
            String.class, "geoscaling.primary.domain.name");
    @SetFromFlag("smartSubdomainName")
    public static final BasicConfigKey<String> GEOSCALING_SMART_SUBDOMAIN_NAME = new BasicConfigKey<String>(
            String.class, "geoscaling.smart.subdomain.name");
    
    public static final BasicAttributeSensor<String> GEOSCALING_ACCOUNT = new BasicAttributeSensor<String>(
            String.class, "geoscaling.account", "Active user account for the GeoScaling.com service");
    public static final BasicAttributeSensor<String> MANAGED_DOMAIN = new BasicAttributeSensor<String>(
            String.class, "geoscaling.managed.domain", "Fully qualified domain name that will be geo-redirected");
    
    public void applyConfig();
    
    /** minimum/default TTL here is 300s = 5m */
    public long getTimeToLiveSeconds();
}
