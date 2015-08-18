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
package brooklyn.management.usage;

import java.util.List;

import org.apache.brooklyn.core.management.usage.ApplicationUsage.ApplicationEvent;
import org.apache.brooklyn.core.management.usage.LocationUsage.LocationEvent;
import org.apache.brooklyn.util.collections.MutableList;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

public class RecordingUsageListener implements org.apache.brooklyn.core.management.internal.UsageListener {

    private final List<List<?>> events = Lists.newCopyOnWriteArrayList();
    
    @Override
    public void onApplicationEvent(ApplicationMetadata app, ApplicationEvent event) {
        events.add(MutableList.of("application", app, event));
    }

    @Override
    public void onLocationEvent(LocationMetadata loc, LocationEvent event) {
        events.add(MutableList.of("location", loc, event));
    }
    
    public void clearEvents() {
        events.clear();
    }
    
    public List<List<?>> getEvents() {
        return ImmutableList.copyOf(events);
    }
    
    public List<List<?>> getLocationEvents() {
        List<List<?>> result = Lists.newArrayList();
        for (List<?> event : events) {
            if (event.get(0).equals("location")) result.add(event);
        }
        return ImmutableList.copyOf(result);
    }
    
    public List<List<?>> getApplicationEvents() {
        List<List<?>> result = Lists.newArrayList();
        for (List<?> event : events) {
            if (event.get(0).equals("application")) result.add(event);
        }
        return ImmutableList.copyOf(result);
    }
}
