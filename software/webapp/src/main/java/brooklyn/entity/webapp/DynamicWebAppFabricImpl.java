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

import javax.annotation.Nullable;

import brooklyn.enricher.Enrichers;
import brooklyn.entity.group.DynamicFabricImpl;
import brooklyn.event.AttributeSensor;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;

public class DynamicWebAppFabricImpl extends DynamicFabricImpl implements DynamicWebAppFabric {

    @Override
    public void onManagementBecomingMaster() {
        // Enricher attribute setup.  A way of automatically discovering these (but avoiding
        // averaging things like HTTP port and response codes) would be neat.
        List<? extends List<? extends AttributeSensor<? extends Number>>> summingEnricherSetup = ImmutableList.of(
        		ImmutableList.of(REQUEST_COUNT, REQUEST_COUNT),
        		ImmutableList.of(ERROR_COUNT, ERROR_COUNT),
        		ImmutableList.of(REQUESTS_PER_SECOND_LAST, REQUESTS_PER_SECOND_LAST),
        		ImmutableList.of(REQUESTS_PER_SECOND_IN_WINDOW, REQUESTS_PER_SECOND_IN_WINDOW),
        		ImmutableList.of(TOTAL_PROCESSING_TIME, TOTAL_PROCESSING_TIME)
        );
        
        List<? extends List<? extends AttributeSensor<? extends Number>>> averagingEnricherSetup = ImmutableList.of(
                ImmutableList.of(REQUEST_COUNT, REQUEST_COUNT_PER_NODE),
                ImmutableList.of(ERROR_COUNT, ERROR_COUNT_PER_NODE),
                ImmutableList.of(REQUESTS_PER_SECOND_LAST, REQUESTS_PER_SECOND_LAST_PER_NODE),
                ImmutableList.of(REQUESTS_PER_SECOND_IN_WINDOW, REQUESTS_PER_SECOND_IN_WINDOW_PER_NODE),
                ImmutableList.of(TOTAL_PROCESSING_TIME, TOTAL_PROCESSING_TIME_PER_NODE)
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
            
            // TODO This needs to respond to changes in FABRIC_SIZE as well, to recalculate
            addEnricher(Enrichers.builder()
                    .transforming(t)
                    .publishing(average)
                    .computing(new Function<Number, Double>() {
                            @Override public Double apply(@Nullable Number input) {
                                Integer size = getAttribute(DynamicWebAppFabric.FABRIC_SIZE);
                                return (size != null && input != null) ? (input.doubleValue() / size) : null;
                            }})
                    .build());
        }
    }
}
