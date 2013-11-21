package brooklyn.entity.webapp;

import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensor;

/**
 * DynamicWebAppClusters provide cluster-wide aggregates of entity attributes.  Currently totals and averages:
 * <ul>
 *   <li>Entity request counts</li>
 *   <li>Entity error counts</li>
 *   <li>Requests per second</li>
 *   <li>Entity processing time</li>
 * </ul>
 */
@ImplementedBy(DynamicWebAppClusterImpl.class)
public interface DynamicWebAppCluster extends DynamicCluster, WebAppService {

    public static final AttributeSensor<Double> REQUEST_COUNT_PER_NODE = new BasicAttributeSensor<Double>(
            Double.class, "webapp.reqs.total.perNode", "Cluster entity request average");

    public static final AttributeSensor<Integer> ERROR_COUNT_PER_NODE = new BasicAttributeSensor<Integer>(
            Integer.class, "webapp.reqs.errors.perNode", "Cluster entity request error average");

    public static final AttributeSensor<Double> REQUESTS_PER_SECOND_LAST_PER_NODE = new BasicAttributeSensor<Double>(
            Double.class, "webapp.reqs.perSec.last.perNode", "Reqs/sec (last datapoint) averaged over all nodes");

    public static final AttributeSensor<Double> REQUESTS_PER_SECOND_IN_WINDOW_PER_NODE = new BasicAttributeSensor<Double>(
            Double.class, "webapp.reqs.perSec.windowed.perNode", "Reqs/sec (over time window) averaged over all nodes");

    public static final AttributeSensor<Integer> TOTAL_PROCESSING_TIME_PER_NODE = new BasicAttributeSensor<Integer>(
            Integer.class, "webapp.reqs.processingTime.perNode", "Total processing time per node");

    public static final AttributeSensor<Double> PROCESSING_TIME_FRACTION_IN_WINDOW_PER_NODE = new BasicAttributeSensor<Double>(
            Double.class, "webapp.reqs.processingTime.fraction.windowed.perNode", "Fraction of time spent processing reported by webserver (percentage, over time window) averaged over all nodes");

}
