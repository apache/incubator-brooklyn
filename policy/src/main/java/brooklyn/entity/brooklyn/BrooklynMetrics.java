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
package brooklyn.entity.brooklyn;

import brooklyn.entity.Entity;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey;
import brooklyn.util.flags.SetFromFlag;

@ImplementedBy(BrooklynMetricsImpl.class)
public interface BrooklynMetrics extends Entity {

    @SetFromFlag("updatePeriod")
    public static final BasicAttributeSensorAndConfigKey<Long> UPDATE_PERIOD = new BasicAttributeSensorAndConfigKey<Long>(
            Long.class, "brooklyn.metrics.updatePeriod", "Time (in milliseconds) between refreshing the metrics", 1000L);

    public static final AttributeSensor<Long> TOTAL_EFFECTORS_INVOKED = new BasicAttributeSensor<Long>(
            Long.class, "brooklyn.metrics.totalEffectorsInvoked", "Total number of effector calls that have been made");
    
    public static final AttributeSensor<Long> TOTAL_TASKS_SUBMITTED = new BasicAttributeSensor<Long>(
            Long.class, "brooklyn.metrics.totalTasksSubmitted", "Total number of tasks that have been executed by brooklyn");
    
    public static final AttributeSensor<Long> NUM_INCOMPLETE_TASKS = new BasicAttributeSensor<Long>(
            Long.class, "brooklyn.metrics.numIncompleteTasks", "Number of tasks that have been submitted but that have not yet completed");
    
    public static final AttributeSensor<Long> NUM_ACTIVE_TASKS = new BasicAttributeSensor<Long>(
            Long.class, "brooklyn.metrics.numActiveTasks", "Number of currently active tasks being executed");

    public static final AttributeSensor<Long> TOTAL_EVENTS_PUBLISHED = new BasicAttributeSensor<Long>(
            Long.class, "brooklyn.metrics.totalEventsPublished", "Total number of events published");
    
    public static final AttributeSensor<Long> TOTAL_EVENTS_DELIVERED = new BasicAttributeSensor<Long>(
            Long.class, "brooklyn.metrics.totalEventsDelivered", "Total number of events delivered (counting an event multiple times if more than one subscriber)");
    
    public static final AttributeSensor<Long> NUM_SUBSCRIPTIONS = new BasicAttributeSensor<Long>(
            Long.class, "brooklyn.metrics.numSubscriptions", "Current number of event subscriptions");
}
