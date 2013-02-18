package brooklyn.demo;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.nosql.cassandra.CassandraCluster;
import brooklyn.entity.proxying.BasicEntitySpec;
import brooklyn.entity.webapp.tomcat.TomcatServer;

/** CumulusRDF application with Cassandra cluster. */
public class CumulusRDFApplication extends ApplicationBuilder {

    /** Create entities. */
    protected void doBuild() {
        createChild(BasicEntitySpec.newInstance(TomcatServer.class));
        createChild(BasicEntitySpec.newInstance(CassandraCluster.class)
                .configure("initialSize", "2")
                .configure("clusterName", "CumulusRDF")
                .configure("jmxPort", "11099+")
                .configure("rmiServerPort", "9001+")
                .configure("thriftPort", "9160+"));
    }

}
