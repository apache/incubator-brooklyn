package brooklyn.entity.webapp

import groovy.lang.MetaClass

import java.util.Map

import brooklyn.entity.Entity
import brooklyn.entity.group.DynamicCluster
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.policy.CustomAggregatingEnricher

class DynamicWebAppCluster extends DynamicCluster {

    public static final BasicAttributeSensor REQUEST_COUNT =
            [ Integer, "cluster.reqs.total", "Cluster-wide entity request count" ]
    public static final BasicAttributeSensor REQUEST_AVERAGE =
            [ Double, "cluster.reqs.average", "Cluster-wide entity request average" ]
    
    private final CustomAggregatingEnricher requestCountEnricher = 
            CustomAggregatingEnricher.<Integer>getSummingEnricher([], JavaWebApp.REQUEST_COUNT, REQUEST_COUNT);
            
    private final CustomAggregatingEnricher averageRequestsEnricher =
            CustomAggregatingEnricher.<Double>getAveragingEnricher([], JavaWebApp.REQUEST_COUNT, REQUEST_AVERAGE);
        
            
    public DynamicWebAppCluster(Map properties=[:], Entity owner=null) {
        super(properties, owner)
        this.addPolicy(requestCountEnricher)
        this.addPolicy(averageRequestsEnricher)
    }
    
    @Override
    public synchronized Entity addMember(Entity member) {
        requestCountEnricher.addProducer(member) 
        averageRequestsEnricher.addProducer(member)
        super.addMember(member)
    }
    
    @Override
    public synchronized boolean removeMember(Entity member) {
        requestCountEnricher.removeProducer(member) 
        averageRequestsEnricher.removeProducer(member)
        super.removeMember(member)
    }
    
    
}
