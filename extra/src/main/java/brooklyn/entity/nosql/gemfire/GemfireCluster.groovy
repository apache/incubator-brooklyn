package brooklyn.entity.nosql.gemfire

import groovy.lang.MetaClass

import java.util.Arrays
import java.util.Collection
import java.util.Collections
import java.util.Map

import brooklyn.entity.Effector
import brooklyn.entity.Entity
import brooklyn.entity.ParameterType
import brooklyn.entity.basic.BasicParameterType
import brooklyn.entity.basic.EffectorWithExplicitImplementation
import brooklyn.entity.group.DynamicCluster
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.location.Location
import brooklyn.management.Task

public class GemfireCluster extends DynamicCluster {

    public static final BasicAttributeSensor<GatewayConnectionDetails> GATEWAY_CONNECTION_DETAILS = [ GatewayConnectionDetails.class, "gemfire.gateway.connectionDetails", "Gemfire gateway connection details" ]
    
    public static final Effector<Void> ADD_GATEWAYS = new EffectorWithExplicitImplementation<GemfireCluster, Void>("addGateways", Void.TYPE,
            Arrays.<ParameterType<?>>asList(new BasicParameterType<Collection>("gateways", Collection.class, "Gatways to be added", Collections.emptyList())),
            "Add gateways for this cluster to replicate to/from other clusters") {
        public Void invokeEffector(GemfireCluster entity, Map m) {
            entity.addGateways((Collection<GatewayConnectionDetails>) m.get("gateways"));
            return null;
        }
    };

    public GemfireCluster(Map flags=[:], Entity owner=null) {
        super(augmentedFlags(flags), owner)
    }
    
    private static Map augmentedFlags(Map flags) {
        Map result = new LinkedHashMap(flags)
        if (!result.displayName) result.displayName = "Gemfire Cluster"
        if (!result.newEntity) result.newEntity = { Map properties ->
                    return new GemfireServer(properties)
                }
        return result
    }
    

    public void addGateways(Collection<GatewayConnectionDetails> gateways) {
        // TODO Call on each server in cluster
    }
}
