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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.enricher.basic.SensorPropagatingEnricher;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.proxying.EntitySpecs;
import brooklyn.entity.trait.Startable;
import brooklyn.entity.zookeeper.Zookeeper;
import brooklyn.event.feed.ConfigToAttributes;
import brooklyn.location.Location;
import brooklyn.util.collections.MutableList;
import brooklyn.util.exceptions.CompoundRuntimeException;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

/**
 * Implementation of a Kafka cluster containing a {@link KafkaZookeeper} node and a group of {@link KafkaBroker}s.
 */
public class KafkaClusterImpl extends AbstractEntity implements KafkaCluster {

    public static final Logger log = LoggerFactory.getLogger(KafkaClusterImpl.class);

    public KafkaClusterImpl() {
    }

    @Override
    public void init() {
        super.init();
        
        setAttribute(SERVICE_UP, false);
        ConfigToAttributes.apply(this, BROKER_SPEC);
        ConfigToAttributes.apply(this, ZOOKEEPER);
        ConfigToAttributes.apply(this, ZOOKEEPER_SPEC);

        log.debug("creating zookeeper child for {}", this);
        Zookeeper zookeeper = getAttribute(ZOOKEEPER);
        if (zookeeper == null) {
            EntitySpec<KafkaZookeeper> zookeeperSpec = getAttribute(ZOOKEEPER_SPEC);
            if (zookeeperSpec == null) {
                log.debug("creating zookeeper using default spec for {}", this);
                zookeeperSpec = EntitySpecs.spec(KafkaZookeeper.class);
                setAttribute(ZOOKEEPER_SPEC, zookeeperSpec);
            } else {
                log.debug("creating zookeeper using custom spec for {}", this);
            }
            zookeeper = addChild(zookeeperSpec);
            if (Entities.isManaged(this)) Entities.manage(zookeeper);
            setAttribute(ZOOKEEPER, zookeeper);
        }

        log.debug("creating cluster child for {}", this);
        EntitySpec<KafkaBroker> brokerSpec = getAttribute(BROKER_SPEC);
        if (brokerSpec == null) {
            log.debug("creating default broker spec for {}", this);
            brokerSpec = EntitySpecs.spec(KafkaBroker.class);
            setAttribute(BROKER_SPEC, brokerSpec);
        }
        // Relies on initialSize being inherited by DynamicCluster, because key id is identical
        // We add the zookeeper configuration to the KafkaBroker specification here
        DynamicCluster cluster = addChild(EntitySpecs.spec(DynamicCluster.class)
                .configure("memberSpec", EntitySpecs.wrapSpec(brokerSpec).configure(KafkaBroker.ZOOKEEPER, zookeeper)));
        if (Entities.isManaged(this)) Entities.manage(cluster);
        setAttribute(CLUSTER, cluster);
    }

    @Override
    public Zookeeper getZookeeper() {
        return getAttribute(ZOOKEEPER);
    }

    @Override
    public DynamicCluster getCluster() {
        return getAttribute(CLUSTER);
    }

    @Override
    public void start(Collection<? extends Location> locations) {
        if (isLegacyConstruction()) {
            init();
        }

        if (locations.isEmpty()) locations = getLocations();
        Iterables.getOnlyElement(locations); // Assert just one
        addLocations(locations);

        List<Entity> childrenToStart = MutableList.<Entity>of(getCluster());
        // Set the KafkaZookeeper entity as child of cluster, if it does not already have a parent
        if (getZookeeper().getParent() == null) {
            addChild(getZookeeper());
        } // And only start zookeeper if we are parent
        if (Objects.equal(this, getZookeeper().getParent())) childrenToStart.add(getZookeeper());
        Entities.invokeEffector(this, childrenToStart, Startable.START, ImmutableMap.of("locations", locations)).getUnchecked();

        connectSensors();
    }

    @Override
    public void stop() {
        List<Exception> errors = Lists.newArrayList();
        if (getZookeeper() != null && Objects.equal(this, getZookeeper().getParent())) {
            try {
                getZookeeper().stop();
            } catch (Exception e) {
                errors.add(e);
            }
        }
        if (getCurrentSize() > 0) {
            try {
                getCluster().stop();
            } catch (Exception e) {
                errors.add(e);
            }
        }

        clearLocations();
        setAttribute(SERVICE_UP, false);

        if (errors.size() != 0) {
            throw new CompoundRuntimeException("Error stopping Kafka cluster", errors);
        }
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

    /*
     * All Group and Resizable interface methods are delegated to the broker cluster.
     */

    /** {@inheritDoc} */
    @Override
    public Collection<Entity> getMembers() { return getCluster().getMembers(); }

    /** {@inheritDoc} */
    @Override
    public boolean hasMember(Entity member) { return getCluster().hasMember(member); }

    /** {@inheritDoc} */
    @Override
    public boolean addMember(Entity member) { return getCluster().addMember(member); }

    /** {@inheritDoc} */
    @Override
    public boolean removeMember(Entity member) { return getCluster().removeMember(member); }

    /** {@inheritDoc} */
    @Override
    public Integer getCurrentSize() { return getCluster().getCurrentSize(); }

    /** {@inheritDoc} */
    @Override
    public Integer resize(Integer desiredSize) { return getCluster().resize(desiredSize); }

}
