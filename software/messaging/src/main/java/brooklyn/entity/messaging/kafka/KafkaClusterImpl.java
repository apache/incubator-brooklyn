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

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.enricher.basic.SensorPropagatingEnricher;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.basic.ConfigurableEntityFactory;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.proxying.BasicEntitySpec;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.proxying.WrappingEntitySpec;
import brooklyn.entity.trait.Startable;
import brooklyn.event.feed.ConfigToAttributes;
import brooklyn.location.Location;
import brooklyn.util.MutableList;
import brooklyn.util.MutableMap;
import brooklyn.util.exceptions.Exceptions;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

/**
 * Implementation of a Kafka cluster containing a {@link KafkaZookeeper} node and a group of {@link KafkaBroker}s.
 */
public class KafkaClusterImpl extends AbstractEntity implements KafkaCluster {

    public static final Logger log = LoggerFactory.getLogger(KafkaClusterImpl.class);

    public KafkaClusterImpl() {
        this(MutableMap.of(), null);
    }
    public KafkaClusterImpl(Map<?, ?> flags) {
        this(flags, null);
    }
    public KafkaClusterImpl(Entity parent) {
        this(MutableMap.of(), parent);
    }
    public KafkaClusterImpl(Map<?, ?> flags, Entity parent) {
        super(flags, parent);
        setAttribute(SERVICE_UP, false);
    }

    @Override
    public void postConstruct() {
        ConfigToAttributes.apply(this, BROKER_FACTORY);
        ConfigToAttributes.apply(this, BROKER_SPEC);
        ConfigToAttributes.apply(this, ZOOKEEPER);
        ConfigToAttributes.apply(this, ZOOKEEPER_SPEC);

        log.debug("creating zookeeper child for {}", this);
        KafkaZookeeper zookeeper = getAttribute(ZOOKEEPER);
        if (zookeeper == null) {
            EntitySpec<KafkaZookeeper> zookeeperSpec = getAttribute(ZOOKEEPER_SPEC);
            if (zookeeperSpec == null) {
                log.debug("creating zookeeper using default spec for {}", this);
                zookeeperSpec = BasicEntitySpec.newInstance(KafkaZookeeper.class);
                setAttribute(ZOOKEEPER_SPEC, zookeeperSpec);
            } else {
                log.debug("creating zookeeper using custom spec for {}", this);
            }
            zookeeper = getEntityManager().createEntity(WrappingEntitySpec.newInstance(zookeeperSpec).parent(this));
            if (Entities.isManaged(this)) Entities.manage(zookeeper);
            setAttribute(ZOOKEEPER, zookeeper);
        }

        log.debug("creating cluster child for {}", this);
        ConfigurableEntityFactory<KafkaBroker> brokerFactory = getAttribute(BROKER_FACTORY);
        EntitySpec<KafkaBroker> brokerSpec = getAttribute(BROKER_SPEC);
        if (brokerFactory == null && brokerSpec == null) {
            log.debug("creating default broker spec for {}", this);
            brokerSpec = BasicEntitySpec.newInstance(KafkaBroker.class);
            setAttribute(BROKER_SPEC, brokerSpec);
        }
        // Note relies on initial_size being inherited by DynamicCluster, because key id is identical
        // We add the zookeeper configuration to the KafkaBroker specification or factory here
        Map<String,Object> flags;
        if (brokerSpec != null) {
            flags = MutableMap.<String, Object>of("memberSpec", WrappingEntitySpec.newInstance(brokerSpec).configure(KafkaBroker.ZOOKEEPER, zookeeper));
        } else {
            brokerFactory.configure(KafkaBroker.ZOOKEEPER, zookeeper);
            flags = MutableMap.<String, Object>of("factory", brokerFactory);
        }
        DynamicCluster cluster = getEntityManager().createEntity(BasicEntitySpec.newInstance(DynamicCluster.class)
                .parent(this)
                .configure(flags));
        if (Entities.isManaged(this)) Entities.manage(cluster);
        setAttribute(CLUSTER, cluster);
    }

    @Override
    public KafkaZookeeper getZookeeper() {
        return getAttribute(ZOOKEEPER);
    }

    @Override
    public synchronized ConfigurableEntityFactory<KafkaBroker> getBrokerFactory() {
        return (ConfigurableEntityFactory<KafkaBroker>) getAttribute(BROKER_FACTORY);
    }

    @Override
    public synchronized DynamicCluster getCluster() {
        return getAttribute(CLUSTER);
    }

    @Override
    public void start(Collection<? extends Location> locations) {
        if (isLegacyConstruction()) {
            postConstruct();
        }

        if (locations.isEmpty()) locations = this.getLocations();
        Iterables.getOnlyElement(locations); //assert just one
        addLocations(locations);

        List<Entity> childrenToStart = MutableList.<Entity>of(getCluster());
        // Set the KafkaZookeeper entity as child of cluster, if it does not already have a parent
        if (getZookeeper().getParent() == null) {
            addChild(getZookeeper());
        }
        // And only start zookeeper if we are parent
        if (this.equals(getZookeeper().getParent())) childrenToStart.add(getZookeeper());
        try {
            Entities.invokeEffectorList(this, childrenToStart, Startable.START, ImmutableMap.of("locations", locations)).get();
        } catch (InterruptedException e) {
            throw Exceptions.propagate(e);
        } catch (ExecutionException e) {
            throw Exceptions.propagate(e);
        }

        connectSensors();
    }

    @Override
    public void stop() {
        if (this.equals(getZookeeper().getParent())) {
            getZookeeper().stop();
        }
        getCluster().stop();

        super.getLocations().clear();
        setAttribute(SERVICE_UP, false);
    }

    @Override
    public void restart() {
        // TODO prod the entities themselves to restart, instead?
        Collection<Location> locations = Lists.newArrayList(getLocations());

        stop();
        start(locations);
    }

    void connectSensors() {
        SensorPropagatingEnricher.newInstanceListeningToAllSensorsBut(getCluster(), SERVICE_UP)
                .addToEntityAndEmitAll(this);
        SensorPropagatingEnricher.newInstanceListeningTo(getZookeeper(), SERVICE_UP)
                .addToEntityAndEmitAll(this);
    }

    @Override
    public Integer resize(Integer desiredSize) {
        return getCluster().resize(desiredSize);
    }

    /** @return the current size of the group. */
    public Integer getCurrentSize() {
        return getCluster().getCurrentSize();
    }

}
