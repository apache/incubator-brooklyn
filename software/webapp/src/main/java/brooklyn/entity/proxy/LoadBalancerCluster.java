package brooklyn.entity.proxy;

import java.util.Map;

import brooklyn.entity.Entity;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.util.MutableMap;

public class LoadBalancerCluster extends DynamicCluster implements LoadBalancer {

    public LoadBalancerCluster(Map<?, ?> flags, Entity owner) {
        super(flags, owner);
    }

    protected Map getCustomChildFlags() {
        // using MutableMap to accept nulls
        return MutableMap.builder()
                .putAll(super.getCustomChildFlags())
//                .put("port", port)
//                .put("serverPool", getConfig(SERVER_POOL))
//                .put("urlMappings", getConfig(URL_MAPPINGS))
                .build();
    }
}
