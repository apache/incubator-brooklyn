package brooklyn.demo;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.nosql.cassandra.CassandraCluster;
import brooklyn.entity.proxying.BasicEntitySpec;

/** Cassandra cluster. */
public class SimpleCassandraCluster extends ApplicationBuilder {

    /** Create entities. */
    protected void doBuild() {
        createChild(BasicEntitySpec.newInstance(CassandraCluster.class)
                .configure("initialSize", "2")
                .configure("clusterName", "Brooklyn")
                .configure("jmxPort", "11099+")
                .configure("rmiServerPort", "9001+")
                .configure("thriftPort", "9160+"));
    }

}
