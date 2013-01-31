package brooklyn.entity.webapp

import brooklyn.enricher.CustomAggregatingEnricher
import brooklyn.entity.Entity
import brooklyn.entity.group.DynamicClusterImpl
import brooklyn.event.basic.BasicAttributeSensor

/**
 * DynamicWebAppClusters provide cluster-wide aggregates of entity attributes.  Currently totals and averages:
 * <ul>
 *   <li>Entity request counts</li>
 *   <li>Entity error counts</li>
 *   <li>Requests per second</li>
 *   <li>Entity processing time</li>
 * </ul>
 */
public class DynamicWebAppClusterImpl extends DynamicClusterImpl implements DynamicWebAppCluster {

    /**
     * Instantiate a new DynamicWebAppCluster.  Parameters as per {@link DynamicCluster#DynamicCluster()}
     */
    public DynamicWebAppClusterImpl(Map properties=[:], Entity parent=null) {
        super(properties, parent)
    }
    
    @Override
    public void onManagementBecomingMaster() {
        // Enricher attribute setup.  A way of automatically discovering these (but avoiding
        // averaging things like HTTP port and response codes) would be neat.
        List<List<BasicAttributeSensor>> enricherSetup = [
            [WebAppService.REQUEST_COUNT, TOTAL_REQUEST_COUNT, AVERAGE_REQUEST_COUNT],
            [WebAppService.ERROR_COUNT, TOTAL_ERROR_COUNT, AVERAGE_ERROR_COUNT],
            [WebAppService.AVG_REQUESTS_PER_SECOND, TOTAL_REQUESTS_PER_SECOND, AVERAGE_REQUESTS_PER_SECOND],
            [WebAppService.TOTAL_PROCESSING_TIME, TOTAL_PROCESSING_TIME, AVERAGE_PROCESSING_TIME]
        ]
        
        for (List es : enricherSetup) {
            def (t, total, average) = es
            def totaller = CustomAggregatingEnricher.<Integer>newSummingEnricher(t, total, allMembers:true);
            def averager = CustomAggregatingEnricher.<Double>newAveragingEnricher(t, average, allMembers:true);
            addEnricher(totaller)
            addEnricher(averager)
        }
    }
    
    public synchronized boolean addMember(Entity member) {
        return super.addMember(member)
    }
    
    @Override
    public synchronized boolean removeMember(Entity member) {
        return super.removeMember(member)
    }
}
