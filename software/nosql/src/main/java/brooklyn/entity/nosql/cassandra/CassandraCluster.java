package brooklyn.entity.nosql.cassandra;

import brooklyn.entity.proxying.ImplementedBy;

/**
 * @deprecated since 0.7.0; use {@link CassandraDatacenter} which is equivalent but has
 * a less ambiguous name; <em>Cluster</em> in Cassandra corresponds to what Brooklyn terms a <em>Fabric</em>.
 */
@Deprecated
@ImplementedBy(CassandraClusterImpl.class)
public interface CassandraCluster extends CassandraDatacenter {
}
