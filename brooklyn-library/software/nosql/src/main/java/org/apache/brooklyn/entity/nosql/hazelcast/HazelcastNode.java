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
package org.apache.brooklyn.entity.nosql.hazelcast;

import org.apache.brooklyn.api.catalog.Catalog;
import org.apache.brooklyn.api.entity.ImplementedBy;
import org.apache.brooklyn.util.core.flags.SetFromFlag;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.location.PortRanges;
import org.apache.brooklyn.core.sensor.BasicAttributeSensorAndConfigKey;
import org.apache.brooklyn.core.sensor.PortAttributeSensorAndConfigKey;
import org.apache.brooklyn.core.sensor.BasicAttributeSensorAndConfigKey.StringAttributeSensorAndConfigKey;
import org.apache.brooklyn.entity.software.base.SoftwareProcess;
import org.apache.brooklyn.entity.java.UsesJava;
import org.apache.brooklyn.entity.java.UsesJmx;
import org.apache.brooklyn.util.javalang.JavaClassNames;

/**
 * An {@link brooklyn.entity.Entity} that represents an Hazelcast node
 */
@Catalog(name="Hazelcast Node", description="Hazelcast is a clustering and highly scalable data distribution platform for Java.")

@ImplementedBy(HazelcastNodeImpl.class)
public interface HazelcastNode extends SoftwareProcess, UsesJava, UsesJmx {
    @SetFromFlag("version")
    ConfigKey<String> SUGGESTED_VERSION = ConfigKeys.newConfigKeyWithDefault(SoftwareProcess.SUGGESTED_VERSION, "3.5.4");
    
    @SetFromFlag("downloadUrl")
    BasicAttributeSensorAndConfigKey<String> DOWNLOAD_URL = new BasicAttributeSensorAndConfigKey<String>(
            SoftwareProcess.DOWNLOAD_URL, "https://repo1.maven.org/maven2/com/hazelcast/hazelcast/${version}/hazelcast-${version}.jar");
    
    @SetFromFlag("configTemplateUrl")
    ConfigKey<String> CONFIG_TEMPLATE_URL = ConfigKeys.newStringConfigKey(
            "hazelcast.node.config.templateUrl", "Template file (in freemarker format) for the Hazelcat config file", 
            JavaClassNames.resolveClasspathUrl(HazelcastNode.class, "hazelcast-brooklyn.xml"));
    
    @SetFromFlag("configFileName")
    ConfigKey<String> CONFIG_FILE_NAME = ConfigKeys.newStringConfigKey(
            "hazelcast.node.config.fileName", "Name of the Hazelcast config file", "hazelcast.xml");
    
    @SetFromFlag("nodeName")
    StringAttributeSensorAndConfigKey NODE_NAME = new StringAttributeSensorAndConfigKey("hazelcast.node.name", 
            "Node name (or randomly selected if not set", null);

    @SetFromFlag("nodeHeapMemorySize")
    ConfigKey<String> NODE_HEAP_MEMORY_SIZE = ConfigKeys.newStringConfigKey(
            "hazelcast.node.heap.memory.size", "Node's heap memory size (-Xmx and -Xms) in megabytes. Default: 256m", "256m");
    
    @SetFromFlag("nodePort")
    PortAttributeSensorAndConfigKey NODE_PORT = new PortAttributeSensorAndConfigKey("hazelcast.node.port", "Hazelcast communication port", PortRanges.fromString("5701+"));

    @SetFromFlag("nodeClusterName")
    BasicAttributeSensorAndConfigKey<String> NODE_CLUSTER_NAME = new BasicAttributeSensorAndConfigKey<String>(String.class, 
            "hazelcast.node.cluster.name", "Name of the Hazelcast cluster which node is part of", "");

    /**
     * Specifies the group name in the configuration file. Each Hazelcast cluster has a separate group.
     */ 
    @SetFromFlag("groupName")
    ConfigKey<String> GROUP_NAME = ConfigKeys.newStringConfigKey("hazelcast.group.name", 
            "Group name", "brooklyn");
  
    @SetFromFlag("groupPassword")
    ConfigKey<String> GROUP_PASSWORD = ConfigKeys.newStringConfigKey("hazelcast.group.password", 
            "Group password", "brooklyn");
    
    String getNodeName();
    
    Integer getNodePort();
    
    String getGroupName();
    
    String getGroupPassword();
    
    String getHostname();
    
    String getHostAddress();

    String getPrivateIpAddress();

    String getListenAddress();
    
    String getHeapMemorySize();
}
