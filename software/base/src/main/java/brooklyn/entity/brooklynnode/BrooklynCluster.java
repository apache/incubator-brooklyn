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
package brooklyn.entity.brooklynnode;

import java.util.Map;

import org.apache.brooklyn.api.effector.Effector;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.entity.ImplementedBy;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.effector.core.Effectors;
import org.apache.brooklyn.entity.group.DynamicCluster;
import org.apache.brooklyn.sensor.core.Sensors;

import brooklyn.entity.brooklynnode.effector.BrooklynNodeUpgradeEffectorBody;

@ImplementedBy(BrooklynClusterImpl.class)
public interface BrooklynCluster extends DynamicCluster {
    
    ConfigKey<EntitySpec<?>> MEMBER_SPEC = ConfigKeys.newConfigKeyWithDefault(DynamicCluster.MEMBER_SPEC, 
            EntitySpec.create(BrooklynNode.class));
    
    AttributeSensor<BrooklynNode> MASTER_NODE = Sensors.newSensor(
            BrooklynNode.class, "brooklyncluster.master", "Pointer to the child node with MASTER state in the cluster");

    interface SelectMasterEffector {
        ConfigKey<String> NEW_MASTER_ID = ConfigKeys.newStringConfigKey(
                "brooklyncluster.new_master_id", "The ID of the node to become master", null);
        Effector<Void> SELECT_MASTER = Effectors.effector(Void.class, "selectMaster")
                .description("Select a new master in the cluster")
                .parameter(NEW_MASTER_ID)
                .buildAbstract();
    }

    Effector<Void> SELECT_MASTER = SelectMasterEffector.SELECT_MASTER;

    interface UpgradeClusterEffector {
        ConfigKey<String> DOWNLOAD_URL = BrooklynNode.DOWNLOAD_URL.getConfigKey();
        ConfigKey<Map<String,Object>> EXTRA_CONFIG = BrooklynNodeUpgradeEffectorBody.EXTRA_CONFIG;

        Effector<Void> UPGRADE_CLUSTER = Effectors.effector(Void.class, "upgradeCluster")
                .description("Upgrade the cluster with new distribution version, "
                    + "by provisioning new nodes with the new version, failing over, "
                    + "and then deprovisioning the original nodes")
                .parameter(BrooklynNode.SUGGESTED_VERSION)
                .parameter(DOWNLOAD_URL)
                .parameter(EXTRA_CONFIG)
                .buildAbstract();
    }

    Effector<Void> UPGRADE_CLUSTER = UpgradeClusterEffector.UPGRADE_CLUSTER;

}
