package brooklyn.demo

import brooklyn.entity.basic.ApplicationBuilder
import brooklyn.entity.nosql.cassandra.CassandraCluster
import brooklyn.entity.proxying.EntitySpecs

/** Cassandra Application */
public class MonitoredCassandraClusterExample extends ApplicationBuilder {

    /**
     * For overriding, to create and wire together entities.
     */
    protected void doBuild() {
        addChild(EntitySpecs.spec(CassandraCluster.class)
                .configure("initialSize", "2")
                .configure("clusterName", "CassandraDemo")
                .configure("jmxPort", "11099+")
                .configure("rmiServerPort", "9001+")
                .configure("thriftPort", "9160+"));
    }

}
