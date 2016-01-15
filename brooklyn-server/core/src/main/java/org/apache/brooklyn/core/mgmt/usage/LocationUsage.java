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
package org.apache.brooklyn.core.mgmt.usage;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.brooklyn.core.entity.lifecycle.Lifecycle;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

/**
 */
public class LocationUsage {
    
    public static class LocationEvent {
        private final Date date;
        private final Lifecycle state;
        private final String entityId;
        private final String entityType;
        private final String applicationId;
        private final String user;

        public LocationEvent(Lifecycle state, String entityId, String entityType, String applicationId, String user) {
            this(new Date(), state, entityId, entityType, applicationId, user);
        }
        
        public LocationEvent(Date date, Lifecycle state, String entityId, String entityType, String applicationId, String user) {
            this.date = checkNotNull(date, "date");
            this.state = checkNotNull(state, "state");
            this.entityId = checkNotNull(entityId, "entityId");
            this.entityType = checkNotNull(entityType, "entityType");
            this.applicationId = checkNotNull(applicationId, "applicationId (entity "+entityId+")");
            this.user = user;
        }

        public Date getDate() {
            return date;
        }

        public Lifecycle getState() {
            return state;
        }

        public String getEntityId() {
            return entityId;
        }

        public String getEntityType() {
            return entityType;
        }

        public String getApplicationId() {
            return applicationId;
        }

        public String getUser() {
            return user;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof LocationEvent)) return false;
            LocationEvent o = (LocationEvent) other;
            return Objects.equal(date, o.date) && Objects.equal(state, o.state) 
                    && Objects.equal(entityId, o.entityId) && Objects.equal(entityType, o.entityType)
                    && Objects.equal(applicationId, o.applicationId) && Objects.equal(user, o.user);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(date, state, entityId, entityType, applicationId, user);
        }
        
        @Override
        public String toString() {
            return Objects.toStringHelper(this)
                    .add("date", date)
                    .add("state", state)
                    .add("entityId", entityId)
                    .add("appId", applicationId)
                    .add("user", user)
                    .toString();
        }
    }
    
    private final String locationId;
    private final Map<String, String> metadata;
    private final List<LocationEvent> events = Collections.synchronizedList(Lists.<LocationEvent>newArrayList());

    public LocationUsage(String locationId, Map<String, String> metadata) {
        this.locationId = checkNotNull(locationId, "locationId");
        this.metadata = checkNotNull(metadata, "metadata");
    }

    public String getLocationId() {
        return locationId;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }
    
    public List<LocationEvent> getEvents() {
        synchronized (events) {
            return ImmutableList.copyOf(events);
        }
    }

    public void addEvent(LocationEvent event) {
        events.add(checkNotNull(event, "event"));
    }
}
