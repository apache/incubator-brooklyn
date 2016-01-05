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

import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;
import org.apache.brooklyn.api.entity.Entity;

import org.apache.brooklyn.entity.group.AbstractMembershipTrackingPolicy;
import org.apache.brooklyn.entity.group.DynamicClusterImpl;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.policy.PolicySpec;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.entity.EntityInternal;
import org.apache.brooklyn.util.text.Strings;

public class HazelcastClusterImpl extends DynamicClusterImpl implements HazelcastCluster {
    private static final Logger LOG = LoggerFactory.getLogger(HazelcastClusterImpl.class);
    
    private static final AtomicInteger nextMemberId = new AtomicInteger(0);
    
    @Override
    protected EntitySpec<?> getMemberSpec() {
        EntitySpec<?> spec = EntitySpec.create(config().get(HazelcastCluster.MEMBER_SPEC));
        
        spec.configure(HazelcastNode.NODE_CLUSTER_NAME, config().get(HazelcastCluster.CLUSTER_NAME));
        spec.configure(HazelcastNode.GROUP_NAME, config().get(HazelcastCluster.CLUSTER_NAME));
        
        if (LOG.isInfoEnabled()) {
            LOG.info("Cluster name : {} : used as a group name", getConfig(HazelcastNode.GROUP_NAME));
        }
        
        spec.configure(HazelcastNode.GROUP_PASSWORD, getClusterPassword());
        
        return spec;
    }
  
    @Override
    public void init() {
        super.init();

        String clusterPassword = getClusterPassword();
        
        if (Strings.isBlank(clusterPassword)) {
            if (LOG.isInfoEnabled()) {
                LOG.info(this + " cluster password not provided for " + CLUSTER_PASSWORD.getName() + " : generating random password");
            }
            config().set(CLUSTER_PASSWORD, Strings.makeRandomId(12));
        }
        
        policies().add(PolicySpec.create(MemberTrackingPolicy.class)
                .displayName("Hazelcast members tracker")
                .configure("group", this));
    }
    
    public static class MemberTrackingPolicy extends AbstractMembershipTrackingPolicy {
        @Override
        protected void onEntityChange(Entity member) {
        }

        @Override
        protected void onEntityAdded(Entity member) {
            if (member.getAttribute(HazelcastNode.NODE_NAME) == null) {
                ((EntityInternal) member).sensors().set(HazelcastNode.NODE_NAME, "hazelcast-" + nextMemberId.incrementAndGet());
                if (LOG.isInfoEnabled()) {
                    LOG.info("Node {} added to the cluster", member);
                }
            }
        }

        @Override
        protected void onEntityRemoved(Entity member) {
        }
    };
    
    @Override
    public String getClusterName() {
        return getConfig(CLUSTER_NAME);
    }

    @Override
    public String getClusterPassword() {
        return getConfig(CLUSTER_PASSWORD);
    }

    @Override
    protected void initEnrichers() {
        super.initEnrichers();
        
    }
    
    @Override
    public void start(Collection<? extends Location> locations) {
        super.start(locations);
        
        List<String> clusterNodes = Lists.newArrayList();
        for (Entity member : getMembers()) {
            clusterNodes.add(member.getAttribute(Attributes.ADDRESS));
        }
        sensors().set(PUBLIC_CLUSTER_NODES, clusterNodes);
    }
}
