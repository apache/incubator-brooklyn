package brooklyn.demo;

import brooklyn.entity.basic.AbstractApplication;
import brooklyn.entity.nosql.cassandra.CassandraCluster;
import brooklyn.entity.proxying.BasicEntitySpec;
import brooklyn.entity.webapp.tomcat.TomcatServer;

/** CumulusRDF application with Cassandra cluster. */
public class CumulusRDFApplication extends AbstractApplication {

    /** Create entities. */
    public void postConstruct() {
        TomcatServer tomcat = getEntityManager().createEntity(BasicEntitySpec.newInstance(TomcatServer.class)
                .configure("war", "cumulusrdf.war"));

        CassandraCluster cassandra = getEntityManager().createEntity(BasicEntitySpec.newInstance(CassandraCluster.class)
                .configure("initialSize", "4")
                .configure("clusterName", "CumulusRDF")
                .configure("jmxPort", "11099+")
                .configure("rmiServerPort", "9001+")
                .configure("thriftPort", "9160+"));

        addChild(tomcat);
        addChild(cassandra);
    }
}
