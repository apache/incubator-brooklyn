package brooklyn.rest.client;

import org.jboss.resteasy.client.ProxyFactory;

import brooklyn.rest.api.AccessApi;
import brooklyn.rest.api.ActivityApi;
import brooklyn.rest.api.ApplicationApi;
import brooklyn.rest.api.CatalogApi;
import brooklyn.rest.api.EffectorApi;
import brooklyn.rest.api.EntityApi;
import brooklyn.rest.api.EntityConfigApi;
import brooklyn.rest.api.LocationApi;
import brooklyn.rest.api.PolicyApi;
import brooklyn.rest.api.PolicyConfigApi;
import brooklyn.rest.api.ScriptApi;
import brooklyn.rest.api.SensorApi;
import brooklyn.rest.api.UsageApi;
import brooklyn.rest.api.VersionApi;


/**
 * @author Adam Lowe
 */
@SuppressWarnings("deprecation")
public class BrooklynApi {
    private final String target;
    
    public BrooklynApi(String endpoint) {
        target = endpoint;
    }
    
    public ActivityApi getActivityApi() {
        return ProxyFactory.create(ActivityApi.class, target);
    }

    public ApplicationApi getApplicationApi() {
        return ProxyFactory.create(ApplicationApi.class, target);
    }

    public CatalogApi getCatalogApi() {
        return ProxyFactory.create(CatalogApi.class, target);
    }

    public EffectorApi getEffectorApi() {
        return ProxyFactory.create(EffectorApi.class, target);
    }
    
    public EntityConfigApi getEntityConfigApi() {
        return ProxyFactory.create(EntityConfigApi.class, target);
    }
    
    public EntityApi getEntityApi() {
        return ProxyFactory.create(EntityApi.class, target);
    }
    
    public LocationApi getLocationApi() {
        return ProxyFactory.create(LocationApi.class, target);
    }
    
    public PolicyConfigApi getPolicyConfigApi() {
        return ProxyFactory.create(PolicyConfigApi.class, target);
    }
    
    public PolicyApi getPolicyApi() {
        return ProxyFactory.create(PolicyApi.class, target);
    }
    
    public ScriptApi getScriptApi() {
        return ProxyFactory.create(ScriptApi.class, target);
    }
    
    public SensorApi getSensorApi() {
        return ProxyFactory.create(SensorApi.class, target);
    }

    public UsageApi getUsageApi() {
        return ProxyFactory.create(UsageApi.class, target);
    }
    
    public VersionApi getVersionApi() {
        return ProxyFactory.create(VersionApi.class, target);
    }
    
    public AccessApi getAccessApi() {
        return ProxyFactory.create(AccessApi.class, target);
    }

}
