package brooklyn.demo

import java.util.List

import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.nosql.cassandra.CassandraNode
import brooklyn.location.Location

/** Cassandra Application */
public class MonitoredCassandraClusterExample extends AbstractApplication {

    CassandraNode server = new CassandraNode(this,
            clusterName:'CassandraDemo',
            jmxPort:'11099', rmiPort:'9001', thriftPort:'9160', gossipPort:'7000')

}
