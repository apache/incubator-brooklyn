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
package brooklyn.entity.nosql.elasticsearch;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.database.DatastoreMixins;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.entity.webapp.WebAppServiceConstants;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey.StringAttributeSensorAndConfigKey;
import brooklyn.event.basic.PortAttributeSensorAndConfigKey;
import brooklyn.event.basic.Sensors;
import brooklyn.location.basic.PortRanges;
import brooklyn.util.flags.SetFromFlag;

/**
 * An {@link brooklyn.entity.Entity} that represents an ElasticSearch node
 */
@ImplementedBy(ElasticSearchNodeImpl.class)
public interface ElasticSearchNode extends SoftwareProcess, DatastoreMixins.HasDatastoreUrl {
    @SetFromFlag("version")
    ConfigKey<String> SUGGESTED_VERSION = ConfigKeys.newConfigKeyWithDefault(SoftwareProcess.SUGGESTED_VERSION, "1.2.1");
    
    @SetFromFlag("downloadUrl")
    BasicAttributeSensorAndConfigKey<String> DOWNLOAD_URL = new BasicAttributeSensorAndConfigKey<String>(
            SoftwareProcess.DOWNLOAD_URL, "https://download.elasticsearch.org/elasticsearch/elasticsearch/elasticsearch-${version}.tar.gz");
    
    @SetFromFlag("dataDir")
    ConfigKey<String> DATA_DIR = ConfigKeys.newStringConfigKey("elasticsearch.node.data.dir", "Directory for writing data files", null);
    
    @SetFromFlag("logDir")
    ConfigKey<String> LOG_DIR = ConfigKeys.newStringConfigKey("elasticsearch.node.log.dir", "Directory for writing log files", null);
    
    @SetFromFlag("configFileUrl")
    ConfigKey<String> TEMPLATE_CONFIGURATION_URL = ConfigKeys.newStringConfigKey(
            "elasticsearch.node.template.configuration.url", "URL where the elasticsearch configuration file (in freemarker format) can be found", null);
    
    @SetFromFlag("multicastEnabled")
    ConfigKey<Boolean> MULTICAST_ENABLED = ConfigKeys.newBooleanConfigKey("elasticsearch.node.multicast.enabled", 
            "Indicates whether zen discovery multicast should be enabled for a node", null);
    
    @SetFromFlag("multicastEnabled")
    ConfigKey<Boolean> UNICAST_ENABLED = ConfigKeys.newBooleanConfigKey("elasticsearch.node.UNicast.enabled", 
            "Indicates whether zen discovery unicast should be enabled for a node", null);
    
    @SetFromFlag("httpPort")
    PortAttributeSensorAndConfigKey HTTP_PORT = new PortAttributeSensorAndConfigKey(WebAppServiceConstants.HTTP_PORT, PortRanges.fromString("9200+"));
    
    @SetFromFlag("nodeName")
    StringAttributeSensorAndConfigKey NODE_NAME = new StringAttributeSensorAndConfigKey("elasticsearch.node.name", 
            "Node name (or randomly selected if not set", null);
    
    @SetFromFlag("clusterName")
    StringAttributeSensorAndConfigKey CLUSTER_NAME = new StringAttributeSensorAndConfigKey("elasticsearch.node.cluster.name", 
            "Cluster name (or elasticsearch selected if not set", null);
    
    AttributeSensor<String> NODE_ID = Sensors.newStringSensor("elasticsearch.node.id");
    AttributeSensor<Integer> DOCUMENT_COUNT = Sensors.newIntegerSensor("elasticsearch.node.docs.count");
    AttributeSensor<Integer> STORE_BYTES = Sensors.newIntegerSensor("elasticsearch.node.store.bytes");
    AttributeSensor<Integer> GET_TOTAL = Sensors.newIntegerSensor("elasticsearch.node.get.total");
    AttributeSensor<Integer> GET_TIME_IN_MILLIS = Sensors.newIntegerSensor("elasticsearch.node.get.time.in.millis");
    AttributeSensor<Integer> SEARCH_QUERY_TOTAL = Sensors.newIntegerSensor("elasticsearch.node.search.query.total");
    AttributeSensor<Integer> SEARCH_QUERY_TIME_IN_MILLIS = Sensors.newIntegerSensor("elasticsearch.node.search.query.time.in.millis");
    
}
