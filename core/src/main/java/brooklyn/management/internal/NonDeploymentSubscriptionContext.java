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
package brooklyn.management.internal;

import java.util.Map;
import java.util.Set;

import brooklyn.entity.Entity;
import brooklyn.entity.Group;
import brooklyn.event.Sensor;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.management.SubscriptionContext;
import brooklyn.management.SubscriptionHandle;
import brooklyn.management.SubscriptionManager;
import brooklyn.management.internal.NonDeploymentManagementContext.NonDeploymentManagementContextMode;

import com.google.common.collect.ImmutableSet;

/**
 * A {@link SubscriptionContext} to be used when management has benn
 * {@link NonDeploymentManagementContextMode#MANAGEMENT_STOPPED stopped}.
 */
public class NonDeploymentSubscriptionContext implements SubscriptionContext {

    private final Object subscriber;

    public NonDeploymentSubscriptionContext(Object subscriber) {
        this.subscriber = subscriber;
    }

    @Override
    public <T> SubscriptionHandle subscribe(Entity producer, Sensor<T> sensor, SensorEventListener<? super T> listener) {
        throw nonDeploymentContextError();
    }

    @Override
    public <T> SubscriptionHandle subscribe(Map<String, Object> newFlags, Entity producer, Sensor<T> sensor, SensorEventListener<? super T> listener) {
        throw nonDeploymentContextError();
    }

    @Override
    public <T> SubscriptionHandle subscribeToChildren(Entity parent, Sensor<T> sensor, SensorEventListener<? super T> listener) {
        throw nonDeploymentContextError();
    }

    @Override
    public <T> SubscriptionHandle subscribeToChildren(Map<String, Object> newFlags, Entity parent, Sensor<T> sensor, SensorEventListener<? super T> listener) {
        throw nonDeploymentContextError();
    }

    @Override
    public <T> SubscriptionHandle subscribeToMembers(Group parent, Sensor<T> sensor, SensorEventListener<? super T> listener) {
        throw nonDeploymentContextError();
    }

    @Override
    public <T> SubscriptionHandle subscribeToMembers(Map<String, Object> newFlags, Group parent, Sensor<T> sensor, SensorEventListener<? super T> listener) {
        throw nonDeploymentContextError();
    }

    private IllegalStateException nonDeploymentContextError() {
        return new IllegalStateException(String.format("Non-deployment subscription context for %s; Management stopped", subscriber));
    }

    @Override
    public boolean unsubscribe(SubscriptionHandle subscriptionId) {
        return false;
    }

    /** @see SubscriptionManager#publish(SensorEvent) */
    @Override
    public <T> void publish(SensorEvent<T> event) {
        throw nonDeploymentContextError();
    }

    /** Return the subscriptions associated with this context */
    @Override
    public Set<SubscriptionHandle> getSubscriptions() {
        return ImmutableSet.<SubscriptionHandle>of();
    }

    @Override
    public int unsubscribeAll() {
        return 0;
    }

}
