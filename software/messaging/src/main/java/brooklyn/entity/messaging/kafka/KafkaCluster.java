/*
 * Copyright 2013 by Cloudsoft Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package brooklyn.entity.messaging.kafka;

import brooklyn.catalog.Catalog;
import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.ConfigurableEntityFactory;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.group.Cluster;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.proxying.BasicEntitySpec;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.entity.trait.Resizable;
import brooklyn.entity.trait.Startable;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.util.flags.SetFromFlag;

/**
 * This entity contains the sub-groups and entities that go in to a single location (e.g. datacenter)
 * to provide Kafka cluster functionality.
 * <p>
 * You can customise the broker by customising the factory (by reference in calling code)
 * or supplying your own factory (as a config flag).
 * <p>
 * The contents of this group entity are:
 * <ul>
 * <li>a {@link brooklyn.entity.group.DynamicCluster} of {@link KafkaBroker}s
 * <li>a {@link KafkaZookeeper}
 * <li>a {@link brooklyn.policy.Policy} to resize the DynamicCluster
 * </ul>
 */
@SuppressWarnings({ "unchecked", "rawtypes" })
@Catalog(name="Kafka", description="Apache Kafka is a distributed publish-subscribe messaging system")
@ImplementedBy(KafkaClusterImpl.class)
public interface KafkaCluster extends Entity, Startable, Resizable  {

    class Spec<T extends KafkaCluster, S extends Spec<T,S>> extends BasicEntitySpec<T,S> {

        private static class ConcreteSpec extends Spec<KafkaCluster, ConcreteSpec> {
            ConcreteSpec() {
                super(KafkaCluster.class);
            }
        }

        public static Spec<KafkaCluster, ?> newInstance() {
            return new ConcreteSpec();
        }

        protected Spec(Class<T> type) {
            super(type);
        }

        public S initialSize(int val) {
            configure(INITIAL_SIZE, val);
            return self();
        }

        public S zookeeper(KafkaZookeeper val) {
            configure(ZOOKEEPER, val);
            return self();
        }

        public S brokerSpec(EntitySpec<KafkaBroker> val) {
            configure(BROKER_SPEC, val);
            return self();
        }

        public S brokerFactory(ConfigurableEntityFactory<KafkaBroker> val) {
            configure(BROKER_FACTORY, val);
            return self();
        }
    }

    @SetFromFlag("startTimeout")
    public static final ConfigKey<Integer> START_TIMEOUT = ConfigKeys.START_TIMEOUT;

    @SetFromFlag("initialSize")
    ConfigKey<Integer> INITIAL_SIZE = new BasicConfigKey<Integer>(Cluster.INITIAL_SIZE, 1);

    @SetFromFlag("zookeeper")
    BasicAttributeSensorAndConfigKey<KafkaZookeeper> ZOOKEEPER = new BasicAttributeSensorAndConfigKey<KafkaZookeeper>(
            KafkaZookeeper.class, "kafka.cluster.zookeeper", "Kafka zookeeper for the cluster; if null a default will created");

    @SetFromFlag("zookeeperSpec")
    BasicAttributeSensorAndConfigKey<EntitySpec<KafkaZookeeper>> ZOOKEEPER_SPEC = new BasicAttributeSensorAndConfigKey(
            EntitySpec.class, "kafka.cluster.zookeeperSpec", "Spec for creating the kafka zookeeper");

    /** Factory to create a Kafka broker, given flags */
    @SetFromFlag("brokerFactory")
    BasicAttributeSensorAndConfigKey<ConfigurableEntityFactory<KafkaBroker>> BROKER_FACTORY = new BasicAttributeSensorAndConfigKey(
            ConfigurableEntityFactory.class, "kafka.cluster.brokerFactory", "Factory to create a Kafka broker");

    /** Spec for Kafka broker entiites to be created */
    @SetFromFlag("brokerSpec")
    BasicAttributeSensorAndConfigKey<EntitySpec<KafkaBroker>> BROKER_SPEC = new BasicAttributeSensorAndConfigKey(
            EntitySpec.class, "kafka.cluster.brokerSpec", "Spec for Kafka broker entiites to be created");

    AttributeSensor<DynamicCluster> CLUSTER = new BasicAttributeSensor<DynamicCluster>(
            DynamicCluster.class, "kafka.cluster.brokerCluster", "Underlying Kafka broker cluster");

    AttributeSensor<String> HOSTNAME = Attributes.HOSTNAME;

    KafkaZookeeper getZookeeper();

    ConfigurableEntityFactory<KafkaBroker> getBrokerFactory();

    DynamicCluster getCluster();

}
