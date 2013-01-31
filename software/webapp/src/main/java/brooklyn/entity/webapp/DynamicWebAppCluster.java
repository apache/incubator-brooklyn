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

    //FIXME align with WebAppService data points
    //e.g. TOTAL_REQUEST_COUNT isn't needed; just use REQUEST_COUNT (and update that description)
    //for AVERAGE_REQUEST_COUNT (and others) rename to REQUEST_COUNT_PER_NODE 
    //and instead of cluster.reqs...
    //use (following WebAbbService) webapp.reqs.total.perNode
    
    public static final AttributeSensor<Integer> TOTAL_REQUEST_COUNT = new BasicAttributeSensor<Integer>(
            Integer.class, "cluster.reqs.count.total", "Cluster-wide entity request count");
    public static final AttributeSensor<Double> AVERAGE_REQUEST_COUNT = new BasicAttributeSensor<Double>(
            Double.class, "cluster.reqs.count.average", "Cluster entity request average");

    public static final AttributeSensor<Integer> TOTAL_ERROR_COUNT = new BasicAttributeSensor<Integer>(
            Integer.class, "cluster.reqs.errors.total", "Cluster-wide entity request error count");
    public static final AttributeSensor<Integer> AVERAGE_ERROR_COUNT = new BasicAttributeSensor<Integer>(
            Integer.class, "cluster.reqs.errors.average", "Cluster entity request error average");

    public static final AttributeSensor<Double> TOTAL_REQUESTS_PER_SECOND = new BasicAttributeSensor<Double>(
            Double.class, "cluster.reqs.persec.total", "Cluster-wide entity requests/sec");
    public static final AttributeSensor<Double> AVERAGE_REQUESTS_PER_SECOND = new BasicAttributeSensor<Double>(
            Double.class, "cluster.reqs.persec.average", "Cluster entity requests/sec average");

    public static final AttributeSensor<Integer> TOTAL_PROCESSING_TIME = new BasicAttributeSensor<Integer>(
            Integer.class, "cluster.reqs.processing-time.total", "Cluster-wide entity total processing time");
    public static final AttributeSensor<Integer> AVERAGE_PROCESSING_TIME = new BasicAttributeSensor<Integer>(
            Integer.class, "cluster.reqs.processing-time.average", "Cluster entity average total processing time");
}
