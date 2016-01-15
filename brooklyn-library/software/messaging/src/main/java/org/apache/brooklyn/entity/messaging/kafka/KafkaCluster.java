/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.brooklyn.entity.messaging.kafka;

import org.apache.brooklyn.api.catalog.Catalog;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.entity.Group;
import org.apache.brooklyn.api.entity.ImplementedBy;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.entity.BrooklynConfigKeys;
import org.apache.brooklyn.core.entity.trait.Resizable;
import org.apache.brooklyn.core.entity.trait.Startable;
import org.apache.brooklyn.core.sensor.BasicAttributeSensor;
import org.apache.brooklyn.core.sensor.BasicAttributeSensorAndConfigKey;
import org.apache.brooklyn.entity.group.Cluster;
import org.apache.brooklyn.entity.group.DynamicCluster;
import org.apache.brooklyn.entity.zookeeper.ZooKeeperNode;
import org.apache.brooklyn.util.core.flags.SetFromFlag;
import org.apache.brooklyn.util.time.Duration;

/**
 * Provides Kafka cluster functionality through a group of {@link KafkaBroker brokers} controlled
 * by a single {@link KafkaZookeeper zookeeper} entity.
 * <p>
 * You can customise the Kafka zookeeper and brokers by supplying {@link EntitySpec entity specifications}
 * to be used when creating them. An existing {@link Zookeeper} entity may also be provided instead of the
 * Kafka zookeeper.
 * <p>
 * The contents of this entity are:
 * <ul>
 * <li>a {@link org.apache.brooklyn.entity.group.DynamicCluster} of {@link KafkaBroker}s
 * <li>a {@link KafkaZookeeper} or {@link Zookeeper}
 * <li>a {@link org.apache.brooklyn.api.policy.Policy} to resize the broker cluster
 * </ul>
 * The {@link Group group} and {@link Resizable} interface methods are delegated to the broker cluster, so calling
 * {@link Resizable#resize(Integer) resize} will change the number of brokers.
 */
@SuppressWarnings({ "unchecked", "rawtypes" })
@Catalog(name="Kafka", description="Apache Kafka is a distributed publish-subscribe messaging system", iconUrl="classpath://org/apache/brooklyn/entity/messaging/kafka/kafka-google-doorway.jpg")
@ImplementedBy(KafkaClusterImpl.class)
public interface KafkaCluster extends Entity, Startable, Resizable, Group  {

    @SetFromFlag("startTimeout")
    ConfigKey<Duration> START_TIMEOUT = BrooklynConfigKeys.START_TIMEOUT;

    @SetFromFlag("initialSize")
    ConfigKey<Integer> INITIAL_SIZE = ConfigKeys.newConfigKeyWithDefault(Cluster.INITIAL_SIZE, 1);

    /** Zookeeper for the cluster. If null a default be will created. */
    @SetFromFlag("zookeeper")
    BasicAttributeSensorAndConfigKey<ZooKeeperNode> ZOOKEEPER = new BasicAttributeSensorAndConfigKey<ZooKeeperNode>(
            ZooKeeperNode.class, "kafka.cluster.zookeeper", "The zookeeper for the cluster; if null a default be will created");

    /** Spec for creating the default Kafka zookeeper entity. */
    @SetFromFlag("zookeeperSpec")
    BasicAttributeSensorAndConfigKey<EntitySpec<KafkaZooKeeper>> ZOOKEEPER_SPEC = new BasicAttributeSensorAndConfigKey(
            EntitySpec.class, "kafka.cluster.zookeeperSpec", "Spec for creating the kafka zookeeper");

    /** Spec for Kafka broker entities to be created. */
    @SetFromFlag("brokerSpec")
    BasicAttributeSensorAndConfigKey<EntitySpec<KafkaBroker>> BROKER_SPEC = new BasicAttributeSensorAndConfigKey(
            EntitySpec.class, "kafka.cluster.brokerSpec", "Spec for Kafka broker entiites to be created");

    /** Underlying Kafka broker cluster. */
    AttributeSensor<DynamicCluster> CLUSTER = new BasicAttributeSensor<DynamicCluster>(
            DynamicCluster.class, "kafka.cluster.brokerCluster", "Underlying Kafka broker cluster");

    ZooKeeperNode getZooKeeper();

    DynamicCluster getCluster();

}
