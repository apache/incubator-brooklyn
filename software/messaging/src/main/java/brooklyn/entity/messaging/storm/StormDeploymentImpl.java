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
package brooklyn.entity.messaging.storm;

import static brooklyn.entity.messaging.storm.Storm.ROLE;
import static brooklyn.entity.messaging.storm.Storm.Role.NIMBUS;
import static brooklyn.entity.messaging.storm.Storm.Role.SUPERVISOR;
import static brooklyn.entity.messaging.storm.Storm.Role.UI;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.enricher.Enrichers;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.BasicStartableImpl;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.zookeeper.ZooKeeperEnsemble;
import brooklyn.util.ResourceUtils;

public class StormDeploymentImpl extends BasicStartableImpl implements StormDeployment {

    @SuppressWarnings("unused")
    private static final Logger log = LoggerFactory.getLogger(StormDeploymentImpl.class);

    @Override
    public void init() {
        new ResourceUtils(this).checkUrlExists(Storm.STORM_CONFIG_TEMPLATE_URL.getDefaultValue());
        
        setDefaultDisplayName("Storm Deployment");
        
        ZooKeeperEnsemble zooKeeperEnsemble = addChild(EntitySpec.create(
            ZooKeeperEnsemble.class).configure(
                ZooKeeperEnsemble.INITIAL_SIZE, getConfig(ZOOKEEPERS_COUNT)));
        
        setConfig(Storm.ZOOKEEPER_ENSEMBLE, zooKeeperEnsemble);
        
        Storm nimbus = addChild(EntitySpec.create(Storm.class).configure(ROLE, NIMBUS));
        
        setConfig(Storm.NIMBUS_ENTITY, nimbus);
        setConfig(Storm.START_MUTEX, new Object());
        
        addChild(EntitySpec.create(DynamicCluster.class)
            .configure(DynamicCluster.MEMBER_SPEC, 
                EntitySpec.create(Storm.class).configure(ROLE, SUPERVISOR))
            .configure(DynamicCluster.INITIAL_SIZE, getConfig(SUPERVISORS_COUNT))
            .displayName("Storm Supervisor Cluster"));
        
        Storm ui = addChild(EntitySpec.create(Storm.class).configure(ROLE, UI));
        
        addEnricher(Enrichers.builder()
                .propagating(Storm.STORM_UI_URL)
                .from(ui)
                .build());
        addEnricher(Enrichers.builder()
                .propagating(Attributes.HOSTNAME)
                .from(nimbus)
                .build());
    }
}
