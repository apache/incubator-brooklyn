/*
 * Copyright 2012-2013 by Cloudsoft Corp.
 */
package brooklyn.entity.nosql.cassandra;

import java.util.Map;
import java.util.Set;

import brooklyn.catalog.Catalog;
import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.group.DynamicFabric;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.Sensors;

import com.google.common.collect.Multimap;

/**
 * A fabric of {@link CassandraNode}s, which forms a cluster spanning multiple locations.
 * 
 * Each "CassandraCluster" child instance is actually just a part of the whole cluster. It consists of the
 * nodes in that single location (which normally corresponds to a "datacenter" in Cassandra terminology).
 */
@Catalog(name="Apache Cassandra Database Fabric", description="Cassandra is a highly scalable, eventually consistent, distributed, structured key-value store which provides a ColumnFamily-based data model richer than typical key/value systems", iconUrl="classpath:///cassandra-logo.jpeg")
@ImplementedBy(CassandraFabricImpl.class)
public interface CassandraFabric extends DynamicFabric {
    
    ConfigKey<Integer> INITIAL_QUORUM_SIZE = ConfigKeys.newIntegerConfigKey(
            "fabric.initial.quorumSize",
            "Initial fabric quorum size - number of initial nodes that must have been successfully started to report success (if < 0, then use value of INITIAL_SIZE)", 
            -1);

    @SuppressWarnings({ "unchecked", "rawtypes" })
    AttributeSensor<Multimap<String,Entity>> DATACENTER_USAGE = (AttributeSensor)Sensors.newSensor(Map.class, "cassandra.cluster.datacenters", "Current set of datacenters in use, with nodes in each");

    @SuppressWarnings({ "unchecked", "rawtypes" })
    AttributeSensor<Set<String>> DATACENTERS = (AttributeSensor)Sensors.newSensor(Set.class, "cassandra.cluster.datacenters", "Current set of datacenters in use");

    @SuppressWarnings({ "unchecked", "rawtypes" })
    AttributeSensor<Set<Entity>> CURRENT_SEEDS = (AttributeSensor)Sensors.newSensor(Set.class, "cassandra.cluster.seeds.current", "Current set of seeds to use to bootstrap the cluster");
    
    AttributeSensor<String> HOSTNAME = Sensors.newStringSensor("cassandra.cluster.hostname", "Hostname to connect to cluster with");

    AttributeSensor<Integer> THRIFT_PORT = Sensors.newIntegerSensor("cassandra.cluster.thrift.port", "Cassandra Thrift RPC port to connect to cluster with");

    void update();
}
