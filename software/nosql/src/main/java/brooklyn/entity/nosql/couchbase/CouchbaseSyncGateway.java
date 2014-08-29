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
package brooklyn.entity.nosql.couchbase;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.event.basic.PortAttributeSensorAndConfigKey;
import brooklyn.event.basic.Sensors;
import brooklyn.util.flags.SetFromFlag;

@ImplementedBy(CouchbaseSyncGatewayImpl.class)
public interface CouchbaseSyncGateway extends SoftwareProcess {

    @SetFromFlag("version")
    ConfigKey<String> SUGGESTED_VERSION = ConfigKeys.newConfigKeyWithDefault(SoftwareProcess.SUGGESTED_VERSION,
            "1.0-beta3.1");

    @SetFromFlag("downloadUrl")
    BasicAttributeSensorAndConfigKey<String> DOWNLOAD_URL = new BasicAttributeSensorAndConfigKey<String>(
            SoftwareProcess.DOWNLOAD_URL, "http://packages.couchbase.com/releases/couchbase-sync-gateway/1.0-beta/couchbase-sync-gateway-community_${version}_${driver.osTag}");
    
    @SetFromFlag("couchbaseServer")
    ConfigKey<Entity> COUCHBASE_SERVER = ConfigKeys.newConfigKey(Entity.class, "couchbaseSyncGateway.couchbaseNode", 
            "Couchbase server node or cluster the sync gateway connects to");

    @SetFromFlag("serverPool")
    ConfigKey<String> COUCHBASE_SERVER_POOL = ConfigKeys.newStringConfigKey("couchbaseSyncGateway.serverPool", 
            "Couchbase Server pool name in which to find buckets", "default");
    
    @SetFromFlag("couchbaseServerBucket")
    ConfigKey<String> COUCHBASE_SERVER_BUCKET = ConfigKeys.newStringConfigKey("couchbaseSyncGateway.serverBucket", 
            "Name of the Couchbase bucket to use", "sync_gateway");

    @SetFromFlag("pretty")
    ConfigKey<Boolean> PRETTY = ConfigKeys.newBooleanConfigKey("couchbaseSyncGateway.pretty", 
            "Pretty-print JSON responses. This is useful for debugging, but reduces performance.", false);

    @SetFromFlag("verbose")
    ConfigKey<Boolean> VERBOSE = ConfigKeys.newBooleanConfigKey("couchbaseSyncGateway.verbose", 
            "Logs more information about requests.", false);

    AttributeSensor<String> COUCHBASE_SERVER_WEB_URL = Sensors.newStringSensor("couchbaseSyncGateway.serverWebUrl", 
            "The Url and web port of the couchbase server to connect to");
    
    AttributeSensor<String> MANAGEMENT_URL = Sensors.newStringSensor("coucbaseSyncGateway.managementUrl", 
            "Management URL for Couchbase Sycn Gateway");

    PortAttributeSensorAndConfigKey SYNC_REST_API_PORT = new PortAttributeSensorAndConfigKey("couchbaseSyncGateway.syncRestPort", 
            "Port the Sync REST API listens on", "4984");
    
    PortAttributeSensorAndConfigKey ADMIN_REST_API_PORT = new PortAttributeSensorAndConfigKey("couchbaseSyncGateway.adminRestPort", 
            "Port the Admin REST API listens on", "4985");

}