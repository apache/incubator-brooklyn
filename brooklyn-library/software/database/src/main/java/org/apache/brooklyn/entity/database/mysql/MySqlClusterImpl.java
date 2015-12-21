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
package org.apache.brooklyn.entity.database.mysql;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nullable;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntityLocal;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.api.sensor.SensorEvent;
import org.apache.brooklyn.api.sensor.SensorEventListener;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.entity.lifecycle.ServiceStateLogic.ServiceNotUpLogic;
import org.apache.brooklyn.core.mgmt.BrooklynTaskTags;
import org.apache.brooklyn.core.sensor.Sensors;
import org.apache.brooklyn.enricher.stock.Enrichers;
import org.apache.brooklyn.entity.group.DynamicClusterImpl;
import org.apache.brooklyn.feed.function.FunctionFeed;
import org.apache.brooklyn.feed.function.FunctionPollConfig;
import org.apache.brooklyn.util.collections.CollectionFunctionals;
import org.apache.brooklyn.util.core.ResourceUtils;
import org.apache.brooklyn.util.core.task.DynamicTasks;
import org.apache.brooklyn.util.core.task.TaskBuilder;
import org.apache.brooklyn.util.guava.Functionals;
import org.apache.brooklyn.util.guava.IfFunctions;
import org.apache.brooklyn.util.text.Identifiers;
import org.apache.brooklyn.util.text.StringPredicates;
import org.apache.brooklyn.util.text.Strings;
import org.apache.brooklyn.util.time.Duration;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Predicates;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.reflect.TypeToken;

// https://dev.mysql.com/doc/refman/5.7/en/replication-howto.html

// TODO SSL connection between master and slave
// TODO Promote slave to master
public class MySqlClusterImpl extends DynamicClusterImpl implements MySqlCluster {
    private static final AttributeSensor<Boolean> NODE_REPLICATION_INITIALIZED = Sensors.newBooleanSensor("mysql.replication_initialized");

    private static final String MASTER_CONFIG_URL = "classpath:///org/apache/brooklyn/entity/database/mysql/mysql_master.conf";
    private static final String SLAVE_CONFIG_URL = "classpath:///org/apache/brooklyn/entity/database/mysql/mysql_slave.conf";
    protected static final int MASTER_SERVER_ID = 1;

    @SuppressWarnings("serial")
    private static final AttributeSensor<Supplier<Integer>> SLAVE_NEXT_SERVER_ID = Sensors.newSensor(new TypeToken<Supplier<Integer>>() {},
            "mysql.slave.next_server_id", "Returns the ID of the next slave server");
    @SuppressWarnings("serial")
    protected static final AttributeSensor<Map<String, String>> SLAVE_ID_ADDRESS_MAPPING = Sensors.newSensor(new TypeToken<Map<String, String>>() {},
            "mysql.slave.id_address_mapping", "Maps slave entity IDs to SUBNET_ADDRESS, so the address is known at member remove time.");

    @Override
    public void init() {
        super.init();
        // Set id supplier in attribute so it is serialized
        sensors().set(SLAVE_NEXT_SERVER_ID, new NextServerIdSupplier());
        sensors().set(SLAVE_ID_ADDRESS_MAPPING, new ConcurrentHashMap<String, String>());
        if (getConfig(SLAVE_PASSWORD) == null) {
            sensors().set(SLAVE_PASSWORD, Identifiers.makeRandomId(8));
        } else {
            sensors().set(SLAVE_PASSWORD, getConfig(SLAVE_PASSWORD));
        }
        initSubscriptions();
    }

    @Override
    public void rebind() {
        super.rebind();
        initSubscriptions();
    }

    private void initSubscriptions() {
        subscriptions().subscribeToMembers(this, MySqlNode.SERVICE_PROCESS_IS_RUNNING, new NodeRunningListener(this));
        subscriptions().subscribe(this, MEMBER_REMOVED, new MemberRemovedListener());
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    protected void initEnrichers() {
        super.initEnrichers();
        propagateMasterAttribute(MySqlNode.HOSTNAME);
        propagateMasterAttribute(MySqlNode.ADDRESS);
        propagateMasterAttribute(MySqlNode.SUBNET_HOSTNAME);
        propagateMasterAttribute(MySqlNode.SUBNET_ADDRESS);
        propagateMasterAttribute(MySqlNode.MYSQL_PORT);
        propagateMasterAttribute(MySqlNode.DATASTORE_URL);

        enrichers().add(Enrichers.builder()
                .aggregating(MySqlNode.DATASTORE_URL)
                .publishing(SLAVE_DATASTORE_URL_LIST)
                .computing((Function<Collection<String>, List<String>>)(Function)Functions.identity())
                .entityFilter(Predicates.not(MySqlClusterUtils.IS_MASTER))
                .fromMembers()
                .build());

        enrichers().add(Enrichers.builder()
                .aggregating(MySqlNode.QUERIES_PER_SECOND_FROM_MYSQL)
                .publishing(QUERIES_PER_SECOND_FROM_MYSQL_PER_NODE)
                .fromMembers()
                .computingAverage()
                .defaultValueForUnreportedSensors(0d)
                .build());
    }

    private void propagateMasterAttribute(AttributeSensor<?> att) {
        enrichers().add(Enrichers.builder()
                .aggregating(att)
                .publishing(att)
                .computing(IfFunctions.ifPredicate(CollectionFunctionals.notEmpty())
                        .apply(CollectionFunctionals.firstElement())
                        .defaultValue(null))
                .entityFilter(MySqlClusterUtils.IS_MASTER)
                .build());
    }

    @Override
    protected EntitySpec<?> getFirstMemberSpec() {
        final EntitySpec<?> firstMemberSpec = super.getFirstMemberSpec();
        if (firstMemberSpec != null) {
            return applyDefaults(firstMemberSpec, Suppliers.ofInstance(MASTER_SERVER_ID), MASTER_CONFIG_URL);
        }

        final EntitySpec<?> memberSpec = super.getMemberSpec();
        if (memberSpec != null) {
            return applyDefaults(memberSpec, Suppliers.ofInstance(MASTER_SERVER_ID), MASTER_CONFIG_URL);
        }

        return EntitySpec.create(MySqlNode.class)
                .displayName("MySql Master")
                .configure(MySqlNode.MYSQL_SERVER_ID, MASTER_SERVER_ID)
                .configure(MySqlNode.TEMPLATE_CONFIGURATION_URL, MASTER_CONFIG_URL);
    }

    @Override
    protected EntitySpec<?> getMemberSpec() {
        Supplier<Integer> serverIdSupplier = getAttribute(SLAVE_NEXT_SERVER_ID);

        EntitySpec<?> spec = super.getMemberSpec();
        if (spec != null) {
            return applyDefaults(spec, serverIdSupplier, SLAVE_CONFIG_URL);
        }

        return EntitySpec.create(MySqlNode.class)
                .displayName("MySql Slave")
                // Slave server IDs will not be linear because getMemberSpec not always results in createNode (result discarded)
                .configure(MySqlNode.MYSQL_SERVER_ID, serverIdSupplier.get())
                .configure(MySqlNode.TEMPLATE_CONFIGURATION_URL, SLAVE_CONFIG_URL);
    }

    private EntitySpec<?> applyDefaults(EntitySpec<?> spec, Supplier<Integer> serverId, String configUrl) {
        boolean needsServerId = !isKeyConfigured(spec, MySqlNode.MYSQL_SERVER_ID);
        boolean needsConfigUrl = !isKeyConfigured(spec, MySqlNode.TEMPLATE_CONFIGURATION_URL.getConfigKey());
        if (needsServerId || needsConfigUrl) {
            EntitySpec<?> clonedSpec = EntitySpec.create(spec);
            if (needsServerId) {
                clonedSpec.configure(MySqlNode.MYSQL_SERVER_ID, serverId.get());
            }
            if (needsConfigUrl) {
                clonedSpec.configure(MySqlNode.TEMPLATE_CONFIGURATION_URL, configUrl);
            }
            return clonedSpec;
        } else {
            return spec;
        }
    }

    private boolean isKeyConfigured(EntitySpec<?> spec, ConfigKey<?> key) {
        return spec.getConfig().containsKey(key) || spec.getFlags().containsKey(key.getName());
    }

    @Override
    protected Entity createNode(Location loc, Map<?, ?> flags) {
        MySqlNode node = (MySqlNode) super.createNode(loc, flags);
        if (!MySqlClusterUtils.IS_MASTER.apply(node)) {
            EntityLocal localNode = (EntityLocal) node;
            ServiceNotUpLogic.updateNotUpIndicator(localNode, MySqlSlave.SLAVE_HEALTHY, "Replication not started");

            addFeed(FunctionFeed.builder()
                .entity(localNode)
                .period(Duration.FIVE_SECONDS)
                .poll(FunctionPollConfig.forSensor(MySqlSlave.SLAVE_HEALTHY)
                        .callable(new SlaveStateCallable(node))
                        .checkSuccess(StringPredicates.isNonBlank())
                        .onSuccess(new SlaveStateParser(node))
                        .setOnFailure(false)
                        .description("Polls SHOW SLAVE STATUS"))
                .build());

            node.enrichers().add(Enrichers.builder().updatingMap(Attributes.SERVICE_NOT_UP_INDICATORS)
                    .from(MySqlSlave.SLAVE_HEALTHY)
                    .computing(Functionals.ifNotEquals(true).value("Slave replication status is not healthy") )
                    .build());
        }
        return node;
    }

    public static class SlaveStateCallable implements Callable<String> {
        private MySqlNode slave;
        public SlaveStateCallable(MySqlNode slave) {
            this.slave = slave;
        }

        @Override
        public String call() throws Exception {
            if (Boolean.TRUE.equals(slave.getAttribute(MySqlNode.SERVICE_PROCESS_IS_RUNNING))) {
                return MySqlClusterUtils.executeSqlOnNode(slave, "SHOW SLAVE STATUS \\G");
            } else {
                return null;
            }
        }

    }

    public static class SlaveStateParser implements Function<String, Boolean> {
        private Entity slave;

        public SlaveStateParser(Entity slave) {
            this.slave = slave;
        }

        @Override
        public Boolean apply(String result) {
            Map<String, String> status = MySqlRowParser.parseSingle(result);
            String secondsBehindMaster = status.get("Seconds_Behind_Master");
            if (secondsBehindMaster != null && !"NULL".equals(secondsBehindMaster)) {
                slave.sensors().set(MySqlSlave.SLAVE_SECONDS_BEHIND_MASTER, new Integer(secondsBehindMaster));
            }
            return "Yes".equals(status.get("Slave_IO_Running")) && "Yes".equals(status.get("Slave_SQL_Running"));
        }

    }

    private static class NextServerIdSupplier implements Supplier<Integer> {
        private AtomicInteger nextId = new AtomicInteger(MASTER_SERVER_ID+1);

        @Override
        public Integer get() {
            return nextId.getAndIncrement();
        }
    }

    // ============= Member Init =============

    // The task is executed separately from the start effector, so failing here
    // will not fail the start effector as well, but it will eventually time out
    // because replication is not started.
    // Would be nice to be able to plug in to the entity lifecycle!

    private static final class NodeRunningListener implements SensorEventListener<Boolean> {
        private MySqlCluster cluster;
        private Semaphore lock = new Semaphore(1);

        public NodeRunningListener(MySqlCluster cluster) {
            this.cluster = cluster;
        }

        @Override
        public void onEvent(SensorEvent<Boolean> event) {
            final MySqlNode node = (MySqlNode) event.getSource();
            if (Boolean.TRUE.equals(event.getValue()) &&
                    // We are interested in SERVICE_PROCESS_IS_RUNNING only while haven't come online yet.
                    // Probably will get several updates while replication is initialized so an additional
                    // check is needed whether we have already seen this.
                    Boolean.FALSE.equals(node.getAttribute(MySqlNode.SERVICE_UP)) &&
                    !Boolean.TRUE.equals(node.getAttribute(NODE_REPLICATION_INITIALIZED))) {

                // Events executed sequentially so no need to synchronize here.
                node.sensors().set(NODE_REPLICATION_INITIALIZED, Boolean.TRUE);

                final Runnable nodeInitTaskBody;
                if (MySqlClusterUtils.IS_MASTER.apply(node)) {
                    nodeInitTaskBody = new InitMasterTaskBody(cluster, node);
                } else {
                    nodeInitTaskBody = new InitSlaveTaskBody(cluster, node, lock);
                }

                DynamicTasks.submitTopLevelTask(TaskBuilder.builder()
                        .displayName("setup master-slave replication")
                        .body(nodeInitTaskBody)
                        .tag(BrooklynTaskTags.tagForContextEntity(node))
                        .tag(BrooklynTaskTags.NON_TRANSIENT_TASK_TAG)
                        .build(),
                        node);
            }
        }

    }
    
    private static class InitMasterTaskBody implements Runnable {
        private MySqlNode master;
        private MySqlCluster cluster;
        public InitMasterTaskBody(MySqlCluster cluster, MySqlNode master) {
            this.cluster = cluster;
            this.master = master;
        }

        @Override
        public void run() {
            String binLogInfo = MySqlClusterUtils.executeSqlOnNode(master, "FLUSH TABLES WITH READ LOCK;SHOW MASTER STATUS \\G UNLOCK TABLES;");
            Map<String, String> status = MySqlRowParser.parseSingle(binLogInfo);
            String file = status.get("File");
            String position = status.get("Position");
            if (file != null && position != null) {
                cluster.sensors().set(MySqlCluster.REPLICATION_LAST_SLAVE_SNAPSHOT, new ReplicationSnapshot(null, null, file, Integer.parseInt(position)));
            }

            //NOTE: Will be executed on each start, analogously to the standard CREATION_SCRIPT config
            String creationScript = getDatabaseCreationScriptAsString(master);
            if (creationScript != null) {
                master.invoke(MySqlNode.EXECUTE_SCRIPT, ImmutableMap.of("commands", creationScript));
            }
        }

        @Nullable private static String getDatabaseCreationScriptAsString(Entity entity) {
            String url = entity.getConfig(MySqlMaster.MASTER_CREATION_SCRIPT_URL);
            if (!Strings.isBlank(url))
                return new ResourceUtils(entity).getResourceAsString(url);
            String contents = entity.getConfig(MySqlMaster.MASTER_CREATION_SCRIPT_CONTENTS);
            if (!Strings.isBlank(contents))
                return contents;
            return null;
        }
    }

    // ============= Member Remove =============

    public class MemberRemovedListener implements SensorEventListener<Entity> {
        @Override
        public void onEvent(SensorEvent<Entity> event) {
            MySqlCluster cluster = (MySqlCluster) event.getSource();
            Entity node = event.getValue();
            String slaveAddress = cluster.getAttribute(SLAVE_ID_ADDRESS_MAPPING).remove(node.getId());
            if (slaveAddress != null) {
                // Could already be gone if stopping the entire app - let it throw an exception
                MySqlNode master = (MySqlNode) Iterables.find(cluster.getMembers(), MySqlClusterUtils.IS_MASTER);
                String username = MySqlClusterUtils.validateSqlParam(cluster.getConfig(SLAVE_USERNAME));
                MySqlClusterUtils.executeSqlOnNodeAsync(master, String.format("DROP USER '%s'@'%s';", username, slaveAddress));
            }
        }
    }

}
