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
package org.apache.brooklyn.entity.nosql.couchbase;

import java.net.URI;

import org.apache.brooklyn.api.entity.proxying.ImplementedBy;
import org.apache.brooklyn.api.event.AttributeSensor;
import org.apache.brooklyn.catalog.Catalog;

import brooklyn.config.ConfigKey;
import brooklyn.config.render.RendererHints;
import brooklyn.entity.annotation.Effector;
import brooklyn.entity.annotation.EffectorParam;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.MethodEffector;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.effector.Effectors;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.event.basic.PortAttributeSensorAndConfigKey;
import brooklyn.event.basic.Sensors;
import brooklyn.util.flags.SetFromFlag;
import brooklyn.util.text.ByteSizeStrings;

@Catalog(name="CouchBase Node", description="Couchbase Server is an open source, distributed (shared-nothing architecture) "
        + "NoSQL document-oriented database that is optimized for interactive applications.")
@ImplementedBy(CouchbaseNodeImpl.class)
public interface CouchbaseNode extends SoftwareProcess {

    @SetFromFlag("adminUsername")
    ConfigKey<String> COUCHBASE_ADMIN_USERNAME = ConfigKeys.newStringConfigKey("couchbase.adminUsername", "Username for the admin user on the node", "Administrator");

    @SetFromFlag("adminPassword")
    ConfigKey<String> COUCHBASE_ADMIN_PASSWORD = ConfigKeys.newStringConfigKey("couchbase.adminPassword", "Password for the admin user on the node", "Password");

    @SetFromFlag("version")
    ConfigKey<String> SUGGESTED_VERSION = ConfigKeys.newConfigKeyWithDefault(SoftwareProcess.SUGGESTED_VERSION,
            "3.0.0");

    @SetFromFlag("enterprise")
    ConfigKey<Boolean> USE_ENTERPRISE = ConfigKeys.newBooleanConfigKey("couchbase.enterprise.enabled",
        "Whether to use Couchbase Enterprise; if false uses the community version. Defaults to true.", true);

    @SetFromFlag("downloadUrl")
    BasicAttributeSensorAndConfigKey<String> DOWNLOAD_URL = new BasicAttributeSensorAndConfigKey<String>(
            SoftwareProcess.DOWNLOAD_URL, "http://packages.couchbase.com/releases/${version}/"
                + "couchbase-server-${driver.communityOrEnterprise}${driver.downloadLinkPreVersionSeparator}${version}${driver.downloadLinkOsTagWithPrefix}");

    @SetFromFlag("clusterInitRamSize")
    BasicAttributeSensorAndConfigKey<Integer> COUCHBASE_CLUSTER_INIT_RAM_SIZE = new BasicAttributeSensorAndConfigKey<Integer>(
            Integer.class, "couchbase.clusterInitRamSize", "initial ram size of the cluster", 300);

    PortAttributeSensorAndConfigKey COUCHBASE_WEB_ADMIN_PORT = new PortAttributeSensorAndConfigKey("couchbase.webAdminPort", "Web Administration Port", "8091+");
    PortAttributeSensorAndConfigKey COUCHBASE_API_PORT = new PortAttributeSensorAndConfigKey("couchbase.apiPort", "Couchbase API Port", "8092+");
    PortAttributeSensorAndConfigKey COUCHBASE_INTERNAL_BUCKET_PORT = new PortAttributeSensorAndConfigKey("couchbase.internalBucketPort", "Internal Bucket Port", "11209");
    PortAttributeSensorAndConfigKey COUCHBASE_INTERNAL_EXTERNAL_BUCKET_PORT = new PortAttributeSensorAndConfigKey("couchbase.internalExternalBucketPort", "Internal/External Bucket Port", "11210");
    PortAttributeSensorAndConfigKey COUCHBASE_CLIENT_INTERFACE_PROXY = new PortAttributeSensorAndConfigKey("couchbase.clientInterfaceProxy", "Client interface (proxy)", "11211");
    PortAttributeSensorAndConfigKey COUCHBASE_INCOMING_SSL_PROXY = new PortAttributeSensorAndConfigKey("couchbase.incomingSslProxy", "Incoming SSL Proxy", "11214");
    PortAttributeSensorAndConfigKey COUCHBASE_INTERNAL_OUTGOING_SSL_PROXY = new PortAttributeSensorAndConfigKey("couchbase.internalOutgoingSslProxy", "Internal Outgoing SSL Proxy", "11215");
    PortAttributeSensorAndConfigKey COUCHBASE_REST_HTTPS_FOR_SSL = new PortAttributeSensorAndConfigKey("couchbase.internalRestHttpsForSsl", "Internal REST HTTPS for SSL", "18091");
    PortAttributeSensorAndConfigKey COUCHBASE_CAPI_HTTPS_FOR_SSL = new PortAttributeSensorAndConfigKey("couchbase.internalCapiHttpsForSsl", "Internal CAPI HTTPS for SSL", "18092");
    PortAttributeSensorAndConfigKey ERLANG_PORT_MAPPER = new PortAttributeSensorAndConfigKey("couchbase.erlangPortMapper", "Erlang Port Mapper Daemon Listener Port (epmd)", "4369");
    PortAttributeSensorAndConfigKey NODE_DATA_EXCHANGE_PORT_RANGE_START = new PortAttributeSensorAndConfigKey("couchbase.nodeDataExchangePortRangeStart", "Node data exchange Port Range Start", "21100+");
    PortAttributeSensorAndConfigKey NODE_DATA_EXCHANGE_PORT_RANGE_END = new PortAttributeSensorAndConfigKey("couchbase.nodeDataExchangePortRangeEnd", "Node data exchange Port Range End", "21199+");

    AttributeSensor<Boolean> IS_PRIMARY_NODE = Sensors.newBooleanSensor("couchbase.isPrimaryNode", "flag to determine if the current couchbase node is the primary node for the cluster");
    AttributeSensor<Boolean> IS_IN_CLUSTER = Sensors.newBooleanSensor("couchbase.isInCluster", "flag to determine if the current couchbase node has been added to a cluster, "
        + "including being the first / primary node");
    AttributeSensor<URI> COUCHBASE_WEB_ADMIN_URL = Attributes.MAIN_URI;
    
    // Interesting stats
    AttributeSensor<Double> OPS = Sensors.newDoubleSensor("couchbase.stats.ops", 
            "Retrieved from pools/nodes/<current node>/interestingStats/ops");
    AttributeSensor<Long> COUCH_DOCS_DATA_SIZE = Sensors.newLongSensor("couchbase.stats.couch.docs.data.size", 
            "Retrieved from pools/nodes/<current node>/interestingStats/couch_docs_data_size");
    AttributeSensor<Long> COUCH_DOCS_ACTUAL_DISK_SIZE = Sensors.newLongSensor("couchbase.stats.couch.docs.actual.disk.size", 
            "Retrieved from pools/nodes/<current node>/interestingStats/couch_docs_actual_disk_size");
    AttributeSensor<Long> EP_BG_FETCHED = Sensors.newLongSensor("couchbase.stats.ep.bg.fetched", 
            "Retrieved from pools/nodes/<current node>/interestingStats/ep_bg_fetched");
    AttributeSensor<Long> MEM_USED = Sensors.newLongSensor("couchbase.stats.mem.used", 
            "Retrieved from pools/nodes/<current node>/interestingStats/mem_used");
    AttributeSensor<Long> COUCH_VIEWS_ACTUAL_DISK_SIZE = Sensors.newLongSensor("couchbase.stats.couch.views.actual.disk.size", 
            "Retrieved from pools/nodes/<current node>/interestingStats/couch_views_actual_disk_size");
    AttributeSensor<Long> CURR_ITEMS = Sensors.newLongSensor("couchbase.stats.curr.items", 
            "Retrieved from pools/nodes/<current node>/interestingStats/curr_items");
    AttributeSensor<Long> VB_REPLICA_CURR_ITEMS = Sensors.newLongSensor("couchbase.stats.vb.replica.curr.items", 
            "Retrieved from pools/nodes/<current node>/interestingStats/vb_replica_curr_items");
    AttributeSensor<Long> COUCH_VIEWS_DATA_SIZE = Sensors.newLongSensor("couchbase.stats.couch.views.data.size", 
            "Retrieved from pools/nodes/<current node>/interestingStats/couch_views_data_size");
    AttributeSensor<Long> GET_HITS = Sensors.newLongSensor("couchbase.stats.get.hits", 
            "Retrieved from pools/nodes/<current node>/interestingStats/get_hits");
    AttributeSensor<Double> CMD_GET = Sensors.newDoubleSensor("couchbase.stats.cmd.get", 
            "Retrieved from pools/nodes/<current node>/interestingStats/cmd_get");
    AttributeSensor<Long> CURR_ITEMS_TOT = Sensors.newLongSensor("couchbase.stats.curr.items.tot", 
            "Retrieved from pools/nodes/<current node>/interestingStats/curr_items_tot");
    AttributeSensor<String> REBALANCE_STATUS = Sensors.newStringSensor("couchbase.rebalance.status", 
            "Displays the current rebalance status from pools/nodes/rebalanceStatus");
    
    class MainUri {
        public static final AttributeSensor<URI> MAIN_URI = Attributes.MAIN_URI;
        
        static {
            // ROOT_URL does not need init because it refers to something already initialized
            RendererHints.register(COUCHBASE_WEB_ADMIN_URL, RendererHints.namedActionWithUrl());

            RendererHints.register(COUCH_DOCS_DATA_SIZE, RendererHints.displayValue(ByteSizeStrings.metric()));
            RendererHints.register(COUCH_DOCS_ACTUAL_DISK_SIZE, RendererHints.displayValue(ByteSizeStrings.metric()));
            RendererHints.register(MEM_USED, RendererHints.displayValue(ByteSizeStrings.metric()));
            RendererHints.register(COUCH_VIEWS_ACTUAL_DISK_SIZE, RendererHints.displayValue(ByteSizeStrings.metric()));
            RendererHints.register(COUCH_VIEWS_DATA_SIZE, RendererHints.displayValue(ByteSizeStrings.metric()));
        }
    }
    
    // this long-winded reference is done just to trigger the initialization above
    AttributeSensor<URI> MAIN_URI = MainUri.MAIN_URI;

    MethodEffector<Void> SERVER_ADD = new MethodEffector<Void>(CouchbaseNode.class, "serverAdd");
    MethodEffector<Void> SERVER_ADD_AND_REBALANCE = new MethodEffector<Void>(CouchbaseNode.class, "serverAddAndRebalance");
    MethodEffector<Void> REBALANCE = new MethodEffector<Void>(CouchbaseNode.class, "rebalance");
    MethodEffector<Void> BUCKET_CREATE = new MethodEffector<Void>(CouchbaseNode.class, "bucketCreate");
    org.apache.brooklyn.api.entity.Effector<Void> ADD_REPLICATION_RULE = Effectors.effector(Void.class, "addReplicationRule")
        .description("Adds a replication rule from the indicated bucket on the cluster where this node is located "
            + "to the indicated cluster and optional destination bucket")
        .parameter(String.class, "fromBucket", "Bucket to be replicated")
        .parameter(Object.class, "toCluster", "Entity (or ID) of the cluster to which this should replicate")
        .parameter(String.class, "toBucket", "Destination bucket for replication in the toCluster, defaulting to the same as the fromBucket")
        .buildAbstract();

    @Effector(description = "add a server to a cluster")
    public void serverAdd(@EffectorParam(name = "serverHostname") String serverToAdd, @EffectorParam(name = "username") String username, @EffectorParam(name = "password") String password);
    
    @Effector(description = "add a server to a cluster, and immediately rebalances")
    public void serverAddAndRebalance(@EffectorParam(name = "serverHostname") String serverToAdd, @EffectorParam(name = "username") String username, @EffectorParam(name = "password") String password);

    @Effector(description = "rebalance the couchbase cluster")
    public void rebalance();
    
    @Effector(description = "create a new bucket")
    public void bucketCreate(@EffectorParam(name = "bucketName") String bucketName, @EffectorParam(name = "bucketType") String bucketType, 
            @EffectorParam(name = "bucketPort") Integer bucketPort, @EffectorParam(name = "bucketRamSize") Integer bucketRamSize, 
            @EffectorParam(name = "bucketReplica") Integer bucketReplica);

}
