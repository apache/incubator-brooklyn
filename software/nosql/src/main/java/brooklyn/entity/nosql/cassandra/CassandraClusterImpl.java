package brooklyn.entity.nosql.cassandra;

/**
 * @deprecated since 0.7.0; use {@link CassandraDatacenter} which is equivalent but has
 * a less ambiguous name; <em>Cluster</em> in Cassandra corresponds to what Brooklyn terms a <em>Fabric</em>.
 */
@Deprecated
public class CassandraClusterImpl extends CassandraDatacenterImpl implements CassandraCluster {
}
