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
package brooklyn.entity.zookeeper;

import java.util.List;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.event.basic.Sensors;
import brooklyn.util.flags.SetFromFlag;

import com.google.common.reflect.TypeToken;

@ImplementedBy(ZooKeeperEnsembleImpl.class)
public interface ZooKeeperEnsemble extends DynamicCluster {

    @SetFromFlag("clusterName")
    BasicAttributeSensorAndConfigKey<String> CLUSTER_NAME = new BasicAttributeSensorAndConfigKey<String>(String
            .class, "zookeeper.cluster.name", "Name of the Zookeeper cluster", "BrooklynZookeeperCluster");

    @SetFromFlag("initialSize")
    public static final ConfigKey<Integer> INITIAL_SIZE = ConfigKeys.newConfigKeyWithDefault(DynamicCluster.INITIAL_SIZE, 3);

    @SuppressWarnings("serial")
    AttributeSensor<List<String>> ZOOKEEPER_SERVERS = Sensors.newSensor(new TypeToken<List<String>>() { },
            "zookeeper.servers", "Hostnames to connect to cluster with");

    String getClusterName();
}
