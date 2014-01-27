package brooklyn.entity.nosql.cassandra;

import brooklyn.entity.proxying.ImplementedBy;

/** @deprecated since 0.7.0; use {@link CassandraDatacenter} which is equivalent -- 
 * but a less ambiguous name as "Cluster" in Cassandra corresponds to what Brooklyn terms a "Fabric".
 */
@Deprecated
@ImplementedBy(CassandraClusterImpl.class)
public interface CassandraCluster extends CassandraDatacenter {
}
