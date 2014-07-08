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

import java.util.concurrent.TimeUnit;

import brooklyn.enricher.RollingTimeWindowMeanEnricher;
import brooklyn.enricher.TimeFractionDeltaEnricher;
import brooklyn.enricher.TimeWeightedDeltaEnricher;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.util.time.Duration;

public class WebAppServiceMethods implements WebAppServiceConstants {
    
    public static final Duration DEFAULT_WINDOW_DURATION = Duration.TEN_SECONDS;
    
    public static void connectWebAppServerPolicies(EntityLocal entity) {
        connectWebAppServerPolicies(entity, DEFAULT_WINDOW_DURATION);
    }
    
    public static void connectWebAppServerPolicies(EntityLocal entity, Duration windowPeriod) {
        entity.addEnricher(TimeWeightedDeltaEnricher.<Integer>getPerSecondDeltaEnricher(entity, REQUEST_COUNT, REQUESTS_PER_SECOND_LAST));
        
        if (windowPeriod!=null) {
            entity.addEnricher(new RollingTimeWindowMeanEnricher<Double>(entity, REQUESTS_PER_SECOND_LAST, 
                    REQUESTS_PER_SECOND_IN_WINDOW, windowPeriod));
        }
        
        entity.addEnricher(new TimeFractionDeltaEnricher<Integer>(entity, TOTAL_PROCESSING_TIME, PROCESSING_TIME_FRACTION_LAST, TimeUnit.MILLISECONDS));
        
        if (windowPeriod!=null) {
            entity.addEnricher(new RollingTimeWindowMeanEnricher<Double>(entity, PROCESSING_TIME_FRACTION_LAST, 
                    PROCESSING_TIME_FRACTION_IN_WINDOW, windowPeriod));
        }

    }
}
