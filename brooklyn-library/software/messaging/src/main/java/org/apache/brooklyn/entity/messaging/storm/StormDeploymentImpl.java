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
package org.apache.brooklyn.entity.messaging.storm;

import static org.apache.brooklyn.entity.messaging.storm.Storm.ROLE;
import static org.apache.brooklyn.entity.messaging.storm.Storm.Role.NIMBUS;
import static org.apache.brooklyn.entity.messaging.storm.Storm.Role.SUPERVISOR;
import static org.apache.brooklyn.entity.messaging.storm.Storm.Role.UI;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.enricher.stock.Enrichers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.brooklyn.entity.group.DynamicCluster;
import org.apache.brooklyn.entity.stock.BasicStartableImpl;
import org.apache.brooklyn.entity.zookeeper.ZooKeeperEnsemble;
import org.apache.brooklyn.util.core.ResourceUtils;

public class StormDeploymentImpl extends BasicStartableImpl implements StormDeployment {

    @SuppressWarnings("unused")
    private static final Logger log = LoggerFactory.getLogger(StormDeploymentImpl.class);

    @Override
    public void init() {
        super.init();
        new ResourceUtils(this).checkUrlExists(Storm.STORM_CONFIG_TEMPLATE_URL.getDefaultValue());
        
        setDefaultDisplayName("Storm Deployment");
        
        ZooKeeperEnsemble zooKeeperEnsemble = addChild(EntitySpec.create(
            ZooKeeperEnsemble.class).configure(
                ZooKeeperEnsemble.INITIAL_SIZE, getConfig(ZOOKEEPERS_COUNT)));
        
        config().set(Storm.ZOOKEEPER_ENSEMBLE, zooKeeperEnsemble);
        
        Storm nimbus = addChild(EntitySpec.create(Storm.class).configure(ROLE, NIMBUS));
        
        config().set(Storm.NIMBUS_ENTITY, nimbus);
        config().set(Storm.START_MUTEX, new Object());
        
        addChild(EntitySpec.create(DynamicCluster.class)
            .configure(DynamicCluster.MEMBER_SPEC, 
                EntitySpec.create(Storm.class).configure(ROLE, SUPERVISOR))
            .configure(DynamicCluster.INITIAL_SIZE, getConfig(SUPERVISORS_COUNT))
            .displayName("Storm Supervisor Cluster"));
        
        Storm ui = addChild(EntitySpec.create(Storm.class).configure(ROLE, UI));
        
        enrichers().add(Enrichers.builder()
                .propagating(Storm.STORM_UI_URL)
                .from(ui)
                .build());
        enrichers().add(Enrichers.builder()
                .propagating(Attributes.HOSTNAME)
                .from(nimbus)
                .build());
    }
}
