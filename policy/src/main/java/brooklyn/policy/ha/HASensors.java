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
package brooklyn.policy.ha;

import brooklyn.event.basic.BasicNotificationSensor;

import com.google.common.base.Objects;

public class HASensors {

    public static final BasicNotificationSensor<FailureDescriptor> ENTITY_FAILED = new BasicNotificationSensor<FailureDescriptor>(
            FailureDescriptor.class, "ha.entityFailed", "Indicates that an entity has failed");
    
    public static final BasicNotificationSensor<FailureDescriptor> ENTITY_RECOVERED = new BasicNotificationSensor<FailureDescriptor>(
            FailureDescriptor.class, "ha.entityRecovered", "Indicates that a previously failed entity has recovered");
    
    public static final BasicNotificationSensor<FailureDescriptor> CONNECTION_FAILED = new BasicNotificationSensor<FailureDescriptor>(
            FailureDescriptor.class, "ha.connectionFailed", "Indicates that a connection has failed");
    
    public static final BasicNotificationSensor<FailureDescriptor> CONNECTION_RECOVERED = new BasicNotificationSensor<FailureDescriptor>(
            FailureDescriptor.class, "ha.connectionRecovered", "Indicates that a previously failed connection has recovered");
    
    // TODO How to make this serializable with the entity reference
    public static class FailureDescriptor {
        private final Object component;
        private final String description;
        
        public FailureDescriptor(Object component, String description) {
            this.component = component;
            this.description = description;
        }
        
        public Object getComponent() {
            return component;
        }
        
        public String getDescription() {
            return description;
        }
        
        @Override
        public String toString() {
            return Objects.toStringHelper(this).add("component", component).add("description", description).toString();
        }
    }
}
