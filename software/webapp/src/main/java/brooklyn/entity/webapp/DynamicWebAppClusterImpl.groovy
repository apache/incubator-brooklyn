package brooklyn.entity.webapp

import brooklyn.enricher.CustomAggregatingEnricher
import brooklyn.entity.Entity
import brooklyn.entity.basic.Lifecycle;
import brooklyn.entity.group.DynamicClusterImpl
import brooklyn.event.SensorEvent
import brooklyn.event.SensorEventListener
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
            [REQUEST_COUNT, REQUEST_COUNT, REQUEST_COUNT_PER_NODE],
            [ERROR_COUNT, ERROR_COUNT, ERROR_COUNT_PER_NODE],
            [REQUESTS_PER_SECOND_LAST, REQUESTS_PER_SECOND_LAST, REQUESTS_PER_SECOND_LAST_PER_NODE],
            [REQUESTS_PER_SECOND_IN_WINDOW, REQUESTS_PER_SECOND_IN_WINDOW, REQUESTS_PER_SECOND_IN_WINDOW_PER_NODE],
            [TOTAL_PROCESSING_TIME, TOTAL_PROCESSING_TIME, TOTAL_PROCESSING_TIME_PER_NODE]
        ]
        
        for (List es : enricherSetup) {
            def (t, total, average) = es
            def totaller = CustomAggregatingEnricher.<Integer>newSummingEnricher(t, total, allMembers:true);
            def averager = CustomAggregatingEnricher.<Double>newAveragingEnricher(t, average, allMembers:true);
            addEnricher(totaller)
            addEnricher(averager)
        }
        
        subscribeToMembers(this, SERVICE_UP, new SensorEventListener<Boolean>() {
            @Override public void onEvent(SensorEvent<Boolean> event) {
                setAttribute(SERVICE_UP, calculateServiceUp());
            }
        });
    }

        
    public synchronized boolean addMember(Entity member) {
        boolean result = super.addMember(member)
        setAttribute(SERVICE_UP, calculateServiceUp());
        return result;
    }
    
    @Override
    public synchronized boolean removeMember(Entity member) {
        boolean result = super.removeMember(member)
        setAttribute(SERVICE_UP, calculateServiceUp());
        return result;
    }

    @Override    
    protected boolean calculateServiceUp() {
        boolean up = false;
        for (Entity member : getMembers()) {
            if (Boolean.TRUE.equals(member.getAttribute(SERVICE_UP))) up = true;
        }
        return up;
    }
}
