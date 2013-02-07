package brooklyn.demo

import java.util.List

import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.nosql.cassandra.CassandraCluster
import brooklyn.entity.proxying.BasicEntitySpec;
import brooklyn.entity.webapp.jboss.JBoss7Server;
import brooklyn.location.Location

/** Cassandra Application */
public class MonitoredCassandraClusterExample extends ApplicationBuilder {

    /**
     * For overriding, to create and wire together entities.
     */
    protected void doBuild() {
        createChild(BasicEntitySpec.newInstance(CassandraCluster.class)
                .configure("initialSize", "2")
                .configure("clusterName", "CassandraDemo")
                .configure("jmxPort", "11099+")
                .configure("rmiServerPort", "9001+")
                .configure("thriftPort", "9160+"));
    }

}
