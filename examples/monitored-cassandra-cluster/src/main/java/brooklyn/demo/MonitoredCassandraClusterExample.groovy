package brooklyn.demo

import java.util.List

import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.nosql.cassandra.CassandraCluster
import brooklyn.location.Location

/** Cassandra Application */
public class MonitoredCassandraClusterExample extends AbstractApplication {

    CassandraCluster cluster = new CassandraCluster(this,
            clusterName:'CassandraDemo',
            initialSize:2,
            jmxPort:'11099+', rmiPort:'9001+', thriftPort:'9160+')

}
