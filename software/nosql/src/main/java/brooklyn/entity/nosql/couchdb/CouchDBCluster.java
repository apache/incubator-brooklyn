package brooklyn.entity.nosql.couchdb;

import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.event.basic.Sensors;
import brooklyn.util.flags.SetFromFlag;

/**
 * A cluster of {@link CouchDBNode}s based on {@link DynamicCluster} which can be resized by a policy if required.
 *
 * TODO add sensors with aggregated CouchDB statistics from cluster
 */
@ImplementedBy(CouchDBClusterImpl.class)
public interface CouchDBCluster extends DynamicCluster {

    @SetFromFlag("clusterName")
    BasicAttributeSensorAndConfigKey<String> CLUSTER_NAME = new BasicAttributeSensorAndConfigKey<String>(String.class, "couchdb.cluster.name", "Name of the CouchDB cluster", "BrooklynCluster");

    AttributeSensor<String> HOSTNAME = Sensors.newStringSensor("couchdb.cluster.hostname", "Hostname to connect to cluster with");

    AttributeSensor<Integer> HTTP_PORT = Sensors.newIntegerSensor("couchdb.cluster.http.port", "CouchDB HTTP port to connect to cluster with");

    /**
     * The name of the cluster.
     */
    String getClusterName();

}
