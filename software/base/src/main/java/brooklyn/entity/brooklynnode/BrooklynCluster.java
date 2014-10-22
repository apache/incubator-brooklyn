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

import brooklyn.config.ConfigKey;
import brooklyn.entity.Effector;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.effector.Effectors;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensor;

@ImplementedBy(BrooklynClusterImpl.class)
public interface BrooklynCluster extends DynamicCluster {
    public static final AttributeSensor<BrooklynNode> MASTER_NODE = new BasicAttributeSensor<BrooklynNode>(
            BrooklynNode.class, "brooklyncluster.master", "Pointer to the child node with MASTER state in the cluster");

    public interface SelectMasterEffector {
        ConfigKey<String> NEW_MASTER_ID = ConfigKeys.newStringConfigKey(
                "brooklyncluster.new_master_id", "The ID of the node to become master", null);
        Effector<Void> SELECT_MASTER = Effectors.effector(Void.class, "selectMaster")
                .description("Select a new master in the cluster")
                .parameter(NEW_MASTER_ID)
                .buildAbstract();
    }

    public static final Effector<Void> SELECT_MASTER = SelectMasterEffector.SELECT_MASTER;

    public interface UpgradeClusterEffector {
        Effector<Void> UPGRADE_CLUSTER = Effectors.effector(Void.class, "upgradeCluster")
                .description("Upgrade the cluster with new distribution version")
                .parameter(SoftwareProcess.DOWNLOAD_URL.getConfigKey())
                .buildAbstract();
    }

    public static final Effector<Void> UPGRADE_CLUSTER = UpgradeClusterEffector.UPGRADE_CLUSTER;

}
