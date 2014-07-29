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

import brooklyn.config.render.RendererHints;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.basic.Sensors;
import brooklyn.util.text.ByteSizeStrings;
import brooklyn.util.time.Duration;

public interface WebAppServiceMetrics {
    
    public static final AttributeSensor<Integer> REQUEST_COUNT = Initializer.REQUEST_COUNT;
        
    public static final brooklyn.event.basic.BasicAttributeSensor<Integer> ERROR_COUNT =
            new brooklyn.event.basic.BasicAttributeSensor<Integer>(Integer.class, "webapp.reqs.errors", "Request errors");
    public static final AttributeSensor<Integer> TOTAL_PROCESSING_TIME = Sensors.newIntegerSensor(
            "webapp.reqs.processingTime.total", "Total processing time, reported by webserver (millis)");
    public static final AttributeSensor<Integer> MAX_PROCESSING_TIME =
            Sensors.newIntegerSensor("webapp.reqs.processingTime.max", "Max processing time for any single request, reported by webserver (millis)");

    /** the fraction of time represented by the most recent delta to TOTAL_PROCESSING_TIME, ie 0.4 if 800 millis were accumulated in last 2s;
     * easily configured with {@link WebAppServiceMethods#connectWebAppServerPolicies(brooklyn.entity.basic.EntityLocal, brooklyn.util.time.Duration)} */
    public static final AttributeSensor<Double> PROCESSING_TIME_FRACTION_LAST =
            Sensors.newDoubleSensor("webapp.reqs.processingTime.fraction.last", "Fraction of time spent processing, reported by webserver (percentage, last datapoint)");
    public static final AttributeSensor<Double> PROCESSING_TIME_FRACTION_IN_WINDOW =
            Sensors.newDoubleSensor("webapp.reqs.processingTime.fraction.windowed", "Fraction of time spent processing, reported by webserver (percentage, over time window)");

    public static final AttributeSensor<Long> BYTES_RECEIVED =
            new BasicAttributeSensor<Long>(Long.class, "webapp.reqs.bytes.received", "Total bytes received by the webserver");
    public static final AttributeSensor<Long> BYTES_SENT =
            new BasicAttributeSensor<Long>(Long.class, "webapp.reqs.bytes.sent", "Total bytes sent by the webserver");

    /** req/second computed from the delta of the last request count and an associated timestamp */
    public static final AttributeSensor<Double> REQUESTS_PER_SECOND_LAST =
            Sensors.newDoubleSensor("webapp.reqs.perSec.last", "Reqs/sec (last datapoint)");

    /** rolled-up req/second for a window, 
     * easily configured with {@link WebAppServiceMethods#connectWebAppServerPolicies(brooklyn.entity.basic.EntityLocal, brooklyn.util.time.Duration)} */
    public static final AttributeSensor<Double> REQUESTS_PER_SECOND_IN_WINDOW =
            Sensors.newDoubleSensor("webapp.reqs.perSec.windowed", "Reqs/sec (over time window)");

    // this class is added because the above need static initialization which unfortunately can't be added to an interface.
    static class Initializer {
        public static final AttributeSensor<Integer> REQUEST_COUNT =
            Sensors.newIntegerSensor("webapp.reqs.total", "Request count");

        static {
            RendererHints.register(WebAppServiceConstants.TOTAL_PROCESSING_TIME, RendererHints.displayValue(Duration.millisToStringRounded()));
            RendererHints.register(WebAppServiceConstants.MAX_PROCESSING_TIME, RendererHints.displayValue(Duration.millisToStringRounded()));
            RendererHints.register(WebAppServiceConstants.BYTES_RECEIVED, RendererHints.displayValue(ByteSizeStrings.metric()));
            RendererHints.register(WebAppServiceConstants.BYTES_SENT, RendererHints.displayValue(ByteSizeStrings.metric()));
        }
    }

}
