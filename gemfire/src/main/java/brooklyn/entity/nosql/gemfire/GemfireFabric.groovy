package brooklyn.entity.nosql.gemfire

import groovy.lang.MetaClass

import java.util.Collection;
import java.util.Map
import java.util.concurrent.ExecutionException

import brooklyn.entity.Entity
import brooklyn.entity.group.DynamicFabric
import brooklyn.location.Location
import brooklyn.management.Task
import brooklyn.util.task.ParallelTask

class GemfireFabric extends DynamicFabric {

    public GemfireFabric(Map flags=[:], Entity owner=null) {
        super(augmentedFlags(flags), owner)
    }
    
    private static Map augmentedFlags(Map flags) {
        Map result = new LinkedHashMap(flags)
        if (!result.displayName) result.displayName = "Gemfire Fabric"
        if (!result.newEntity) result.newEntity = { Map properties, Entity fabric ->
                    return new GemfireCluster(properties, fabric)
                }
        return result
    }
    
    @Override
    public void start(Collection<? extends Location> locations) {
        super.start(locations)
        
        Map<GemfireCluster,GatewayConnectionDetails> gateways = [:]
        ownedChildren.each {
            gateways.put(it, ((GemfireCluster)it).getAttribute(GemfireCluster.GATEWAY_CONNECTION_DETAILS))
        }
        
        Collection<Task> tasks = ownedChildren.collect {
            Map<GemfireCluster,GatewayConnectionDetails> otherGateways = new LinkedHashMap(gateways)
            otherGateways.remove(it)
            
            Task task = it.invoke(GemfireCluster.ADD_GATEWAYS, [gateways:otherGateways.values()])
            return task
        }
        
        Task invoke = new ParallelTask(tasks)
        executionContext.submit(invoke)
        
        if (invoke) {
            try {
                invoke.get()
            } catch (ExecutionException ee) {
                throw ee.cause
            }
        }
    }
}
