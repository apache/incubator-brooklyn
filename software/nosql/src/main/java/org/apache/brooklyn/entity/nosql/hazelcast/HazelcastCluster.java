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

import java.util.List;

import com.google.common.reflect.TypeToken;

import org.apache.brooklyn.api.catalog.Catalog;
import org.apache.brooklyn.api.entity.ImplementedBy;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.util.core.flags.SetFromFlag;

import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.sensor.BasicAttributeSensorAndConfigKey;
import org.apache.brooklyn.core.sensor.Sensors;
import org.apache.brooklyn.entity.group.DynamicCluster;

/**
 * A cluster of {@link HazelcastNode}s based on {@link DynamicCluster}.
 */
@Catalog(name="Hazelcast Cluster", description="Hazelcast is a clustering and highly scalable data distribution platform for Java.")

@ImplementedBy(HazelcastClusterImpl.class)
public interface HazelcastCluster extends DynamicCluster {

    @SetFromFlag("clusterName")
    BasicAttributeSensorAndConfigKey<String> CLUSTER_NAME = new BasicAttributeSensorAndConfigKey<String>(String.class, 
            "hazelcast.cluster.name", "Name of the Hazelcast cluster", "HazelcastCluster");
    
    @SetFromFlag("clusterPassword")
    ConfigKey<String> CLUSTER_PASSWORD =
            ConfigKeys.newStringConfigKey("hazelcast.cluster.password", "Hazelcast cluster password.");
    
    @SuppressWarnings("serial")
    AttributeSensor<List<String>> PUBLIC_CLUSTER_NODES = Sensors.newSensor(new TypeToken<List<String>>() {},
        "hazelcast.cluster.public.nodes", "List of public addresses of all nodes in the cluster");
    
    String getClusterName();
    
    String getClusterPassword();
}
