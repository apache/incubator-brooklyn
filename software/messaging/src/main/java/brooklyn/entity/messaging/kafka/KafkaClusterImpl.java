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
package brooklyn.entity.messaging.kafka;

import java.util.Collection;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.enricher.Enrichers;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.trait.Startable;
import brooklyn.entity.zookeeper.ZooKeeperNode;
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
        ZooKeeperNode zookeeper = getAttribute(ZOOKEEPER);
        if (zookeeper == null) {
            EntitySpec<KafkaZooKeeper> zookeeperSpec = getAttribute(ZOOKEEPER_SPEC);
            if (zookeeperSpec == null) {
                log.debug("creating zookeeper using default spec for {}", this);
                zookeeperSpec = EntitySpec.create(KafkaZooKeeper.class);
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
            brokerSpec = EntitySpec.create(KafkaBroker.class);
            setAttribute(BROKER_SPEC, brokerSpec);
        }
        // Relies on initialSize being inherited by DynamicCluster, because key id is identical
        // We add the zookeeper configuration to the KafkaBroker specification here
        DynamicCluster cluster = addChild(EntitySpec.create(DynamicCluster.class)
                .configure("memberSpec", EntitySpec.create(brokerSpec).configure(KafkaBroker.ZOOKEEPER, zookeeper)));
        if (Entities.isManaged(this)) Entities.manage(cluster);
        setAttribute(CLUSTER, cluster);
        
        connectSensors();
    }

    @Override
    public ZooKeeperNode getZooKeeper() {
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
        if (getZooKeeper().getParent() == null) {
            addChild(getZooKeeper());
        } // And only start zookeeper if we are parent
        if (Objects.equal(this, getZooKeeper().getParent())) childrenToStart.add(getZooKeeper());
        Entities.invokeEffector(this, childrenToStart, Startable.START, ImmutableMap.of("locations", locations)).getUnchecked();
    }

    @Override
    public void stop() {
        List<Exception> errors = Lists.newArrayList();
        if (getZooKeeper() != null && Objects.equal(this, getZooKeeper().getParent())) {
            try {
                getZooKeeper().stop();
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
        addEnricher(Enrichers.builder()
                .propagatingAllBut(SERVICE_UP)
                .from(getCluster())
                .build());
        addEnricher(Enrichers.builder()
                .propagating(SERVICE_UP)
                .from(getZooKeeper())
                .build());
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

    @Override
    public <T extends Entity> T addMemberChild(EntitySpec<T> spec) { return getCluster().addMemberChild(spec); }

    @Override
    public <T extends Entity> T addMemberChild(T child) { return getCluster().addMemberChild(child); }

}
