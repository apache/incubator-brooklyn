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
package org.apache.brooklyn.core.server.entity;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.core.entity.AbstractEntity;
import org.apache.brooklyn.core.mgmt.internal.LocalSubscriptionManager;
import org.apache.brooklyn.core.mgmt.internal.ManagementContextInternal;
import org.apache.brooklyn.util.core.task.BasicExecutionManager;

import com.google.common.util.concurrent.ThreadFactoryBuilder;

public class BrooklynMetricsImpl extends AbstractEntity implements BrooklynMetrics {

    private ScheduledExecutorService executor;
    
    public BrooklynMetricsImpl() {
    }

    @Override
    public void onManagementBecomingMaster() {
        // TODO Don't use own thread pool; use new "feeds" (see FunctionFeed, or variants there of)
        ThreadFactory threadFactory = new ThreadFactoryBuilder()
                .setNameFormat("brooklyn-brooklynmetrics-poller-%d")
                .build();
        executor = Executors.newSingleThreadScheduledExecutor(threadFactory);
        executor.scheduleWithFixedDelay(
                new Runnable() {
                    public void run() {
                        refreshSensors();
                    }}, 
                0, 
                getConfig(UPDATE_PERIOD), 
                TimeUnit.MILLISECONDS);
    }
    
    /**
     * Invoked by {@link ManagementContext} when this entity becomes mastered at a particular management node,
     * including the final management end and subsequent management node master-change for this entity.
     */
    @Override
    public void onManagementNoLongerMaster() {
        if (executor != null) executor.shutdownNow();
    }

    private void refreshSensors() {
        ManagementContext managementContext = getManagementContext();
        BasicExecutionManager execManager = (BasicExecutionManager) (managementContext != null ? managementContext.getExecutionManager() : null);
        LocalSubscriptionManager subsManager = (LocalSubscriptionManager) (managementContext != null ? managementContext.getSubscriptionManager() : null);
        
        if (managementContext != null) {
            sensors().set(TOTAL_EFFECTORS_INVOKED, ((ManagementContextInternal)managementContext).getTotalEffectorInvocations());
        }
        if (execManager != null) {
            sensors().set(TOTAL_TASKS_SUBMITTED, execManager.getTotalTasksSubmitted());
            sensors().set(NUM_INCOMPLETE_TASKS, execManager.getNumIncompleteTasks());
            sensors().set(NUM_ACTIVE_TASKS, execManager.getNumActiveTasks());
        }
        if (subsManager != null) {
            sensors().set(TOTAL_EVENTS_PUBLISHED, subsManager.getTotalEventsPublished());
            sensors().set(TOTAL_EVENTS_DELIVERED, subsManager.getTotalEventsDelivered());
            sensors().set(NUM_SUBSCRIPTIONS, subsManager.getNumSubscriptions());
        }
    }
}
