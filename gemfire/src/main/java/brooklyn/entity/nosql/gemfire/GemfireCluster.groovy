package brooklyn.entity.nosql.gemfire

import java.util.Arrays
import java.util.Collection
import java.util.Collections
import java.util.Map
import java.util.concurrent.ExecutionException

import brooklyn.entity.Effector
import brooklyn.entity.Entity
import brooklyn.entity.ParameterType
import brooklyn.entity.basic.BasicParameterType
import brooklyn.entity.basic.EffectorWithExplicitImplementation
import brooklyn.entity.basic.Entities;
import brooklyn.entity.group.DynamicCluster
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.management.Task
import brooklyn.util.internal.LanguageUtils

public class GemfireCluster extends DynamicCluster {

    public static final BasicAttributeSensor<GatewayConnectionDetails> GATEWAY_CONNECTION_DETAILS = [ GatewayConnectionDetails.class, "gemfire.gateway.connectionDetails", "Gemfire gateway connection details" ]
    
    public static final Effector<Void> ADD_GATEWAYS =
        new EffectorWithExplicitImplementation<GemfireCluster, Void>("addGateways", Void.TYPE,
            Arrays.<ParameterType<?>>asList(new BasicParameterType<Collection>("gateways", Collection.class,"Gateways to be added", Collections.emptyList())),
            "Add gateways to this cluster, to replicate to/from other clusters") {
        public Void invokeEffector(GemfireCluster entity, Map m) {
            entity.addGateways((Collection<GatewayConnectionDetails>) m.get("gateways"));
            return null;
        }
    };

	public static final Effector<Void> REMOVE_GATEWAYS =
		new EffectorWithExplicitImplementation<GemfireCluster, Void>("removeGateways", Void.TYPE,
			Arrays.<ParameterType<?>>asList(new BasicParameterType<Collection>("gateways", Collection.class,"Gateways to be removed", Collections.emptyList())),
			"Remove decomissioned gateways from this cluster") {
		public Void invokeEffector(GemfireCluster entity, Map m) {
			entity.removeGateways((Collection<GatewayConnectionDetails>) m.get("gateways"));
			return null;
		}
	};

	public static final Effector<Void> ADD_REGIONS =
		new EffectorWithExplicitImplementation<GemfireCluster, Void>(
			"addRegions", 
			Void.TYPE,
			Arrays.<ParameterType<?>>asList(
				new BasicParameterType<Collection>("servers", Collection.class, "Servers to add the regions to", Collections.emptyList()),
				new BasicParameterType<Collection>("regions", Collection.class, "Regions to be added", Collections.emptyList())
			),
			"Add regions to this cluster- will replicate and stay in sync if the region already exists elsewhere") {
				public Void invokeEffector(GemfireCluster entity, Map m) {
					entity.addRegions((Collection<GatewayConnectionDetails>) m.get("regions"));
					return null;
		}
	};

	public static final Effector<Void> REMOVE_REGIONS =
		new EffectorWithExplicitImplementation<GemfireCluster, Void>(
			"removeGateways", 
			Void.TYPE,
			Arrays.<ParameterType<?>>asList(
				new BasicParameterType<Collection>("servers", Collection.class,"Servers to add the regions to", Collections.emptyList()),
				new BasicParameterType<Collection>("regions", Collection.class,"Regions to be removed", Collections.emptyList())
			),
			"Locally destroy a region on this cluster- will continue to exist elsewhere") {
		public Void invokeEffector(GemfireCluster entity, Map m) {
			entity.removeRegions((Collection<GatewayConnectionDetails>) m.get("regions"));
			return null;
		}
	};

    private String abbreviatedName

    public GemfireCluster(Map flags=[:], Entity owner=null) {
        super(augmentedFlags(flags), owner)
        abbreviatedName = flags.abbreviatedName ?: LanguageUtils.newUid()
    }
    
    private static Map augmentedFlags(Map flags) {
        Map result = new LinkedHashMap(flags)
        if (!result.displayName) result.displayName = "Gemfire Cluster"
        if (!result.newEntity) result.newEntity = { Map properties ->
                    return new GemfireServer(properties)
                }
        return result
    }
    
    public Integer resize(Integer desiredSize) {
        super.resize(desiredSize)
        
        if (ownedChildren) {
            GemfireServer gemfireServer = ownedChildren.iterator().next()
            String host = gemfireServer.getAttribute(GemfireServer.HOSTNAME)
            int port = gemfireServer.getAttribute(GemfireServer.HUB_PORT)
            GatewayConnectionDetails gatewayConnectionDetails = new GatewayConnectionDetails(abbreviatedName, host, port)
            setAttribute(GATEWAY_CONNECTION_DETAILS, gatewayConnectionDetails)
        } else {
            setAttribute(GATEWAY_CONNECTION_DETAILS, null)
        }
    }
    
    public void addGateways(Collection<GatewayConnectionDetails> gateways) {
        Task<List<?>> task = Entities.invokeEffectorList(this, ownedChildren, GemfireServer.ADD_GATEWAYS, [gateways:gateways])
        
        try {
            task.get()
        } catch (ExecutionException ee) {
            throw ee.cause
        }
    }
	
	public void removeGateways(Collection<GatewayConnectionDetails> gateways) {
		Task<List<?>> task = Entities.invokeEffectorList(this, ownedChildren, GemfireServer.REMOVE_GATEWAYS, [gateways:gateways])
		
		try {
			task.get()
		} catch (ExecutionException ee) {
			throw ee.cause
		}
	}
	
	public void addRegions(Collection<GemfireServer> servers, Collection<String> regions) {
		Task<List<?>> task = Entities.invokeEffectorList(this, servers.intersect(ownedChildren), GemfireServer.ADD_REGIONS, [regions:regions])
		
		try {
			task.get()
		} catch (ExecutionException ee) {
			throw ee.cause
		}
	}
	
	public void removeRegions(Collection<GemfireServer> servers, Collection<String> regions) {
		Task<List<?>> task = Entities.invokeEffectorList(this, servers.intersect(ownedChildren), GemfireServer.REMOVE_REGIONS, [regions:regions])
		
		try {
			task.get()
		} catch (ExecutionException ee) {
			throw ee.cause
		}
	}
}
