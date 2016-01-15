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
package org.apache.brooklyn.entity.nosql.mongodb.sharding;

import java.util.Collection;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.policy.PolicySpec;
import org.apache.brooklyn.api.sensor.SensorEvent;
import org.apache.brooklyn.api.sensor.SensorEventListener;
import org.apache.brooklyn.core.entity.EntityPredicates;
import org.apache.brooklyn.core.entity.trait.Startable;
import org.apache.brooklyn.entity.group.AbstractMembershipTrackingPolicy;
import org.apache.brooklyn.entity.group.DynamicClusterImpl;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

public class MongoDBRouterClusterImpl extends DynamicClusterImpl implements MongoDBRouterCluster {

    @Override
    public void init() {
        super.init();
        subscriptions().subscribeToChildren(this, MongoDBRouter.RUNNING, new SensorEventListener<Boolean>() {
            @Override public void onEvent(SensorEvent<Boolean> event) {
                setAnyRouter();
            }
        });
    }
    
    @Override
    public void start(Collection<? extends Location> locations) {
        super.start(locations);
        policies().add(PolicySpec.create(MemberTrackingPolicy.class)
                .displayName("Router cluster membership tracker")
                .configure("group", this));
    }
    
    public static class MemberTrackingPolicy extends AbstractMembershipTrackingPolicy {
        @Override protected void onEntityEvent(EventType type, Entity member) {
            ((MongoDBRouterClusterImpl)super.entity).setAnyRouter();
        }
        @Override protected void onEntityRemoved(Entity member) {
            ((MongoDBRouterClusterImpl)super.entity).setAnyRouter();
        }
        @Override protected void onEntityChange(Entity member) {
            ((MongoDBRouterClusterImpl)super.entity).setAnyRouter();
        }
    }
    
    protected void setAnyRouter() {
        sensors().set(MongoDBRouterCluster.ANY_ROUTER, Iterables.tryFind(getRouters(), 
                EntityPredicates.attributeEqualTo(Startable.SERVICE_UP, true)).orNull());

        sensors().set(
                MongoDBRouterCluster.ANY_RUNNING_ROUTER, 
                Iterables.tryFind(getRouters(), EntityPredicates.attributeEqualTo(MongoDBRouter.RUNNING, true))
                .orNull());
    }
    
    @Override
    public Collection<MongoDBRouter> getRouters() {
        return ImmutableList.copyOf(Iterables.filter(getMembers(), MongoDBRouter.class));
    }
    
    @Override
    protected EntitySpec<?> getMemberSpec() {
        if (super.getMemberSpec() != null)
            return super.getMemberSpec();
        return EntitySpec.create(MongoDBRouter.class);
    }

    @Override
    public MongoDBRouter getAnyRouter() {
        return getAttribute(MongoDBRouterCluster.ANY_ROUTER);
    }
    
    @Override
    public MongoDBRouter getAnyRunningRouter() {
        return getAttribute(MongoDBRouterCluster.ANY_RUNNING_ROUTER);
    }
 
}
