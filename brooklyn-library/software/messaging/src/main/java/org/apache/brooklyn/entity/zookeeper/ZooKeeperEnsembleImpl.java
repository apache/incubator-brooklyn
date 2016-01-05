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
package org.apache.brooklyn.entity.zookeeper;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.policy.PolicySpec;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.entity.EntityInternal;
import org.apache.brooklyn.entity.group.AbstractMembershipTrackingPolicy;
import org.apache.brooklyn.entity.group.DynamicClusterImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;

public class ZooKeeperEnsembleImpl extends DynamicClusterImpl implements ZooKeeperEnsemble {

    private static final Logger log = LoggerFactory.getLogger(ZooKeeperEnsembleImpl.class);
    private static final AtomicInteger myId = new AtomicInteger();
    
    private MemberTrackingPolicy policy;

    public ZooKeeperEnsembleImpl() {}

    /**
     * Sets the default {@link #MEMBER_SPEC} to describe the ZooKeeper nodes.
     */
    @Override
    protected EntitySpec<?> getMemberSpec() {
        return getConfig(MEMBER_SPEC, EntitySpec.create(ZooKeeperNode.class));
    }

    @Override
    public String getClusterName() {
        return getAttribute(CLUSTER_NAME);
    }

    @Override
    public void init() {
        log.info("Initializing the ZooKeeper Ensemble");
        super.init();

        policy = policies().add(PolicySpec.create(MemberTrackingPolicy.class)
                .displayName("Members tracker")
                .configure("group", this));
    }

    public static class MemberTrackingPolicy extends AbstractMembershipTrackingPolicy {
        @Override
        protected void onEntityChange(Entity member) {
        }

        @Override
        protected void onEntityAdded(Entity member) {
            if (member.getAttribute(ZooKeeperNode.MY_ID) == null) {
                ((EntityInternal) member).sensors().set(ZooKeeperNode.MY_ID, myId.incrementAndGet());
            }
        }

        @Override
        protected void onEntityRemoved(Entity member) {
        }
    };

    @Override
    protected void initEnrichers() {
        super.initEnrichers();
        
    }
    
    @Override
    public void start(Collection<? extends Location> locations) {
        super.start(locations);
        
        List<String> zookeeperServers = Lists.newArrayList();
        for (Entity zookeeper : getMembers()) {
            zookeeperServers.add(zookeeper.getAttribute(Attributes.HOSTNAME));
        }
        sensors().set(ZOOKEEPER_SERVERS, zookeeperServers);
    }

}
