package brooklyn.entity.webapp;

import brooklyn.entity.group.DynamicFabric;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensor;

/**
 * DynamicWebAppFabric provide a fabric of clusters, aggregating the entity attributes.  Currently totals and averages:
 * <ul>
 *   <li>Entity request counts</li>
 *   <li>Entity error counts</li>
 *   <li>Requests per second</li>
 *   <li>Entity processing time</li>
 * </ul>
 */
@ImplementedBy(DynamicWebAppFabricImpl.class)
public interface DynamicWebAppFabric extends DynamicFabric, WebAppService {

    public static final AttributeSensor<Double> REQUEST_COUNT_PER_NODE = new BasicAttributeSensor<Double>(
            Double.class, "webapp.reqs.total.perNode", "Fabric entity request average");

    public static final AttributeSensor<Integer> ERROR_COUNT_PER_NODE = new BasicAttributeSensor<Integer>(
            Integer.class, "webapp.reqs.errors.perNode", "Fabric entity request error average");

    public static final AttributeSensor<Double> REQUESTS_PER_SECOND_LAST_PER_NODE = DynamicWebAppCluster.REQUESTS_PER_SECOND_LAST_PER_NODE;
    public static final AttributeSensor<Double> REQUESTS_PER_SECOND_IN_WINDOW_PER_NODE = DynamicWebAppCluster.REQUESTS_PER_SECOND_IN_WINDOW_PER_NODE;
    public static final AttributeSensor<Integer> TOTAL_PROCESSING_TIME_PER_NODE = DynamicWebAppCluster.TOTAL_PROCESSING_TIME_PER_NODE;

}
