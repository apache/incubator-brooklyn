package brooklyn.entity.nosql.cassandra;

/** @deprecated since 0.7.0; use {@link CassandraDatacenter} which is equivalent -- 
 * but a less ambiguous name as "Cluster" in Cassandra corresponds to what Brooklyn terms a "Fabric".
 */
public class CassandraClusterImpl extends CassandraDatacenterImpl implements CassandraCluster {
}
