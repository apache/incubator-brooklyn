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
package org.apache.brooklyn.policy.enricher;

import static org.testng.Assert.assertNotNull;

import java.net.URL;
import java.util.concurrent.TimeUnit;

import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.mgmt.rebind.RebindTestFixtureWithApp;
import org.apache.brooklyn.core.sensor.Sensors;
import org.apache.brooklyn.core.test.entity.TestApplication;
import org.apache.brooklyn.policy.enricher.DeltaEnricher;
import org.apache.brooklyn.policy.enricher.HttpLatencyDetector;
import org.apache.brooklyn.policy.enricher.RollingMeanEnricher;
import org.apache.brooklyn.policy.enricher.RollingTimeWindowMeanEnricher;
import org.apache.brooklyn.policy.enricher.TimeFractionDeltaEnricher;
import org.apache.brooklyn.policy.enricher.TimeWeightedDeltaEnricher;
import org.apache.brooklyn.test.Asserts;
import org.apache.brooklyn.test.EntityTestUtils;
import org.apache.brooklyn.util.core.http.BetterMockWebServer;
import org.apache.brooklyn.util.time.Duration;
import org.apache.brooklyn.util.time.Time;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import com.google.mockwebserver.MockResponse;

public class RebindEnricherTest extends RebindTestFixtureWithApp {

    public static AttributeSensor<String> METRIC1 = Sensors.newStringSensor("RebindEnricherTest.metric1");
    public static AttributeSensor<String> METRIC2 = Sensors.newStringSensor("RebindEnricherTest.metric2");
    public static AttributeSensor<Integer> INT_METRIC = Sensors.newIntegerSensor("RebindEnricherTest.int_metric");
    public static AttributeSensor<Integer> INT_METRIC2 = Sensors.newIntegerSensor("RebindEnricherTest.int_metric2");
    public static AttributeSensor<Double> DOUBLE_METRIC = Sensors.newDoubleSensor("RebindEnricherTest.double_metric");
    
    private BetterMockWebServer webServer;

    @AfterMethod(alwaysRun=true)
    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        if (webServer != null) webServer.shutdown();
    }
    
    @Test
    public void testDeltaEnricher() throws Exception {
        origApp.enrichers().add(new DeltaEnricher<Integer>(origApp, INT_METRIC, INT_METRIC2));
        
        TestApplication newApp = rebind();

        newApp.sensors().set(INT_METRIC, 1);
        newApp.sensors().set(INT_METRIC, 10);
        EntityTestUtils.assertAttributeEqualsEventually(newApp, INT_METRIC2, 9);
    }

    @Test
    public void testHttpLatencyDetectorEnricher() throws Exception {
        webServer = BetterMockWebServer.newInstanceLocalhost();
        for (int i = 0; i < 1000; i++) {
            webServer.enqueue(new MockResponse().setResponseCode(200).addHeader("content-type: application/json").setBody("{\"foo\":\"myfoo\"}"));
        }
        webServer.play();
        URL baseUrl = webServer.getUrl("/");

        origApp.enrichers().add(HttpLatencyDetector.builder()
                .rollup(Duration.of(50, TimeUnit.MILLISECONDS))
                .period(Duration.of(10, TimeUnit.MILLISECONDS))
                .url(baseUrl)
                .build());
        origApp.sensors().set(Attributes.SERVICE_UP, true);
        
        TestApplication newApp = rebind();

        newApp.sensors().set(HttpLatencyDetector.REQUEST_LATENCY_IN_SECONDS_MOST_RECENT, null);
        newApp.sensors().set(HttpLatencyDetector.REQUEST_LATENCY_IN_SECONDS_IN_WINDOW, null);

        EntityTestUtils.assertAttributeEventuallyNonNull(newApp, HttpLatencyDetector.REQUEST_LATENCY_IN_SECONDS_MOST_RECENT);
        EntityTestUtils.assertAttributeEventuallyNonNull(newApp, HttpLatencyDetector.REQUEST_LATENCY_IN_SECONDS_IN_WINDOW);
    }

    @Test
    public void testRollingMeanEnricher() throws Exception {
        origApp.enrichers().add(new RollingMeanEnricher<Integer>(origApp, INT_METRIC, DOUBLE_METRIC, 2));
        
        TestApplication newApp = rebind();

        newApp.sensors().set(INT_METRIC, 10);
        EntityTestUtils.assertAttributeEqualsEventually(newApp, DOUBLE_METRIC, 10d);
    }

    @Test
    public void testRollingTimeWindowMeanEnricher() throws Exception {
        origApp.enrichers().add(new RollingTimeWindowMeanEnricher<Integer>(origApp, INT_METRIC, DOUBLE_METRIC, Duration.of(10, TimeUnit.MILLISECONDS)));
        
        TestApplication newApp = rebind();

        newApp.sensors().set(INT_METRIC, 10);
        Time.sleep(Duration.millis(10));
        newApp.sensors().set(INT_METRIC, 10);
        EntityTestUtils.assertAttributeEqualsEventually(newApp, DOUBLE_METRIC, 10d);
    }
    
    @Test
    public void testTimeFractionDeltaEnricher() throws Exception {
        origApp.enrichers().add(new TimeFractionDeltaEnricher<Integer>(origApp, INT_METRIC, DOUBLE_METRIC, TimeUnit.MILLISECONDS));
        
        final TestApplication newApp = rebind();

        // TODO When doing two setAttributes in rapid succession, the test sometimes fails;
        // my hypothesis is that the two events had exactly the same timestamp.
        Asserts.succeedsEventually(new Runnable() {
            private int counter;
            public void run() {
                newApp.sensors().set(INT_METRIC, counter++);
                assertNotNull(newApp.getAttribute(DOUBLE_METRIC));
            }});
    }
    
    @Test
    public void testTimeWeightedDeltaEnricher() throws Exception {
        origApp.enrichers().add(new TimeWeightedDeltaEnricher<Integer>(origApp, INT_METRIC, DOUBLE_METRIC, 1000));
        
        final TestApplication newApp = rebind();

        // TODO When doing two setAttributes in rapid succession, the test sometimes fails;
        // my hypothesis is that the two events had exactly the same timestamp.
        Asserts.succeedsEventually(new Runnable() {
            private int counter;
            public void run() {
                newApp.sensors().set(INT_METRIC, counter++);
                assertNotNull(newApp.getAttribute(DOUBLE_METRIC));
            }});
    }
}
