package brooklyn.entity.webapp

import groovy.lang.MetaClass

import java.util.Map

import brooklyn.entity.Entity
import brooklyn.entity.group.DynamicCluster
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.policy.CustomAggregatingEnricher
import brooklyn.policy.Policy

/**
 * DynamicWebAppClusters provide cluster-wide aggregates of entity attributes.  Currently totals and averages:
 * <ul>
 *   <li>Entity request counts</li>
 *   <li>Entity error counts</li>
 *   <li>Requests per second</li>
 *   <li>Entity processing time</li>
 * </ul>
 */
class DynamicWebAppCluster extends DynamicCluster {

    public static final BasicAttributeSensor TOTAL_REQUEST_COUNT =
            [ Integer, "cluster.reqs.total", "Cluster-wide entity request count" ]
    public static final BasicAttributeSensor AVERAGE_REQUEST_COUNT =
            [ Double, "cluster.reqs.average", "Cluster entity request average" ]

    public static final BasicAttributeSensor TOTAL_ERROR_COUNT = 
            [ Integer, "cluster.reqs.errors.total", "Cluster-wide entity request error count" ]
    public static final BasicAttributeSensor AVERAGE_ERROR_COUNT =
            [ Integer, "cluster.reqs.errors.average", "Cluster entity request error average" ]

    public static final BasicAttributeSensor<Double> TOTAL_REQUESTS_PER_SECOND = 
            [ Double, "cluster.reqs.persec.total", "Cluster-wide entity requests/sec" ]
    public static final BasicAttributeSensor<Double> AVERAGE_REQUESTS_PER_SECOND =
            [ Double, "cluster.reqs.persec.average", "Cluster entity requests/sec average" ]

    public static final BasicAttributeSensor<Integer> TOTAL_PROCESSING_TIME = 
            [ Integer, "cluster.reqs.processing-time.total", "Cluster-wide entity total processing time" ]
    public static final BasicAttributeSensor<Integer> AVERAGE_PROCESSING_TIME =
            [ Integer, "cluster.reqs.processing-time.average", "Cluster entity average total processing time" ]

    /**
     * Instantiate a new DynamicWebAppCluster.  Parameters as per {@link DynamicCluster#DynamicCluster()}
     */
    public DynamicWebAppCluster(Map properties=[:], Entity owner=null) {
        super(properties, owner)
        
        // Enricher attribute setup.  A way of automatically discovering these (but avoiding 
        // averaging things like HTTP port and response codes) would be neat.
        List<BasicAttributeSensor> enricherSetup = [
            [JavaWebApp.REQUEST_COUNT, TOTAL_REQUEST_COUNT, AVERAGE_REQUEST_COUNT],
            [JavaWebApp.ERROR_COUNT, TOTAL_ERROR_COUNT, AVERAGE_ERROR_COUNT],
            [JavaWebApp.REQUESTS_PER_SECOND, TOTAL_REQUESTS_PER_SECOND, AVERAGE_REQUESTS_PER_SECOND],
            [JavaWebApp.TOTAL_PROCESSING_TIME, TOTAL_PROCESSING_TIME, AVERAGE_PROCESSING_TIME]
        ]
        
        for (List es : enricherSetup) {
            def (t, total, average) = es
            def totaller = CustomAggregatingEnricher.<Integer>getSummingEnricher([], t, total);
            def averager = CustomAggregatingEnricher.<Double>getAveragingEnricher([], t, average);
            this.addPolicy(totaller)
            this.addPolicy(averager)
        }
    }
    
    @Override
    public synchronized Entity addMember(Entity member) {
        policies.each { if (it in CustomAggregatingEnricher) it.addProducer(member); }
        super.addMember(member)
    }
    
    @Override
    public synchronized boolean removeMember(Entity member) {
        policies.each { if (it in CustomAggregatingEnricher) it.removeProducer(member); }
        super.removeMember(member)
    }
    
    
}
