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
public class ApplicationUsage {
    
    public static class ApplicationEvent {
        private final Date date;
        private final Lifecycle state;
        private final String user;

        public ApplicationEvent(Lifecycle state, String user) {
            this(new Date(), state, user);
        }

        public ApplicationEvent(Date date, Lifecycle state) {
            this(date,state, null);
        }

        public ApplicationEvent(Date date, Lifecycle state, String user) {
            this.date = checkNotNull(date, "date");
            this.state = checkNotNull(state, "state");
            this.user = user;
        }

        public Date getDate() {
            return date;
        }

        public Lifecycle getState() {
            return state;
        }

        public String getUser() {
            return user;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof ApplicationEvent)) return false;
            ApplicationEvent o = (ApplicationEvent) other;
            return Objects.equal(date, o.date) && Objects.equal(state, o.state) && Objects.equal(user, o.user);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(date, state, user);
        }
        
        @Override
        public String toString() {
            return Objects.toStringHelper(this).add("date", date).add("state", state).add("entitlementContext", user).toString();
        }
    }
    
    private final String applicationId;
    private final String applicationName;
    private final String entityType;
    private final Map<String, String> metadata;
    private final List<ApplicationEvent> events = Collections.synchronizedList(Lists.<ApplicationEvent>newArrayList());

    public ApplicationUsage(String applicationId, String applicationName, String entityType, Map<String, String> metadata) {
        this.applicationId = checkNotNull(applicationId, "applicationId");
        // allow name to be null, happens in certain failed rebind cases
        this.applicationName = applicationName;
        this.entityType = checkNotNull(entityType, "entityType");
        this.metadata = checkNotNull(metadata, "metadata");
    }

    public String getApplicationId() {
        return applicationId;
    }

    public String getApplicationName() {
        return applicationName;
    }

    public String getEntityType() {
        return entityType;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }
    
    public List<ApplicationEvent> getEvents() {
        synchronized (events) {
            return ImmutableList.copyOf(events);
        }
    }

    public void addEvent(ApplicationEvent event) {
        events.add(checkNotNull(event, "event"));
    }
}
