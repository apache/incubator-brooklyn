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
package brooklyn.entity.webapp;

import java.util.List;

import brooklyn.enricher.Enrichers;
import brooklyn.entity.Entity;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.group.DynamicClusterImpl;
import brooklyn.event.AttributeSensor;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;

import com.google.common.collect.ImmutableList;

/**
 * DynamicWebAppClusters provide cluster-wide aggregates of entity attributes.  Currently totals and averages:
 * <ul>
 *   <li>Entity request counts</li>
 *   <li>Entity error counts</li>
 *   <li>Requests per second</li>
 *   <li>Entity processing time</li>
 * </ul>
 */
public class DynamicWebAppClusterImpl extends DynamicClusterImpl implements DynamicWebAppCluster {

    /**
     * Instantiate a new DynamicWebAppCluster.  Parameters as per {@link DynamicCluster#DynamicCluster()}
     */
    public DynamicWebAppClusterImpl() {
        super();
    }
    
    @Override
    public void onManagementBecomingMaster() {
        // Enricher attribute setup.  A way of automatically discovering these (but avoiding
        // averaging things like HTTP port and response codes) would be neat.
        List<? extends List<? extends AttributeSensor<? extends Number>>> summingEnricherSetup = ImmutableList.of(
                ImmutableList.of(REQUEST_COUNT, REQUEST_COUNT),
                ImmutableList.of(ERROR_COUNT, ERROR_COUNT),
                ImmutableList.of(REQUESTS_PER_SECOND_LAST, REQUESTS_PER_SECOND_LAST),
                ImmutableList.of(REQUESTS_PER_SECOND_IN_WINDOW, REQUESTS_PER_SECOND_IN_WINDOW),
                ImmutableList.of(TOTAL_PROCESSING_TIME, TOTAL_PROCESSING_TIME),
                ImmutableList.of(PROCESSING_TIME_FRACTION_IN_WINDOW, PROCESSING_TIME_FRACTION_IN_WINDOW)
        );
        
        List<? extends List<? extends AttributeSensor<? extends Number>>> averagingEnricherSetup = ImmutableList.of(
                ImmutableList.of(REQUEST_COUNT, REQUEST_COUNT_PER_NODE),
                ImmutableList.of(ERROR_COUNT, ERROR_COUNT_PER_NODE),
                ImmutableList.of(REQUESTS_PER_SECOND_LAST, REQUESTS_PER_SECOND_LAST_PER_NODE),
                ImmutableList.of(REQUESTS_PER_SECOND_IN_WINDOW, REQUESTS_PER_SECOND_IN_WINDOW_PER_NODE),
                ImmutableList.of(TOTAL_PROCESSING_TIME, TOTAL_PROCESSING_TIME_PER_NODE),
                ImmutableList.of(PROCESSING_TIME_FRACTION_IN_WINDOW, PROCESSING_TIME_FRACTION_IN_WINDOW_PER_NODE)
        );
        
        for (List<? extends AttributeSensor<? extends Number>> es : summingEnricherSetup) {
            AttributeSensor<? extends Number> t = es.get(0);
            AttributeSensor<? extends Number> total = es.get(1);
            addEnricher(Enrichers.builder()
                    .aggregating(t)
                    .publishing(total)
                    .fromMembers()
                    .computingSum()
                    .build());
        }
        
        for (List<? extends AttributeSensor<? extends Number>> es : averagingEnricherSetup) {
            AttributeSensor<Number> t = (AttributeSensor<Number>) es.get(0);
            AttributeSensor<Double> average = (AttributeSensor<Double>) es.get(1);
            addEnricher(Enrichers.builder()
                    .aggregating(t)
                    .publishing(average)
                    .fromMembers()
                    .computingAverage()
                    .build());
        }

        subscribeToMembers(this, SERVICE_UP, new SensorEventListener<Boolean>() {
            @Override public void onEvent(SensorEvent<Boolean> event) {
                if (!isRebinding()) {
                    setAttribute(SERVICE_UP, calculateServiceUp());
                }
            }
        });
    }

    @Override
    public synchronized boolean addMember(Entity member) {
        boolean result = super.addMember(member);
        if (!isRebinding()) {
            setAttribute(SERVICE_UP, calculateServiceUp());
        }
        return result;
    }
    
    @Override
    public synchronized boolean removeMember(Entity member) {
        boolean result = super.removeMember(member);
        if (!isRebinding()) {
            setAttribute(SERVICE_UP, calculateServiceUp());
        }
        return result;
    }

    @Override    
    protected boolean calculateServiceUp() {
        boolean up = false;
        for (Entity member : getMembers()) {
            if (Boolean.TRUE.equals(member.getAttribute(SERVICE_UP))) up = true;
        }
        return up;
    }
}
