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
package brooklyn.event.feed.windows;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.util.Collection;
import java.util.Iterator;

import org.apache.brooklyn.test.EntityTestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.BrooklynAppUnitTestSupport;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.Sensors;
import brooklyn.location.Location;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.text.Strings;
import io.cloudsoft.winrm4j.winrm.WinRmToolResponse;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

public class WindowsPerformanceCounterFeedTest extends BrooklynAppUnitTestSupport {

    private Location loc;
    private EntityLocal entity;

    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        loc = new LocalhostMachineProvisioningLocation();
        entity = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        app.start(ImmutableList.of(loc));
    }

    @AfterMethod(alwaysRun=true)
    @Override
    public void tearDown() throws Exception {
        super.tearDown();
    }

    private static final Logger log = LoggerFactory.getLogger(WindowsPerformanceCounterFeedTest.class);

    @Test
    public void testIteratorWithSingleValue() {
        Iterator<?> iterator = new WindowsPerformanceCounterFeed
                .PerfCounterValueIterator("\"10/14/2013 15:28:24.406\",\"0.000000\"");
        assertTrue(iterator.hasNext());
        assertEquals(iterator.next(), "0.000000");
        assertFalse(iterator.hasNext());
    }

    @Test
    public void testIteratorWithMultipleValues() {
        Iterator<?> iterator = new WindowsPerformanceCounterFeed
                .PerfCounterValueIterator("\"10/14/2013 15:35:50.582\",\"8803.000000\",\"405622.000000\"");
        assertTrue(iterator.hasNext());
        assertEquals(iterator.next(), "8803.000000");
        assertTrue(iterator.hasNext());
        assertEquals(iterator.next(), "405622.000000");
        assertFalse(iterator.hasNext());
    }

    @Test
    public void testSendPerfCountersToSensors() {
        AttributeSensor<String> stringSensor = Sensors.newStringSensor("foo.bar");
        AttributeSensor<Integer> integerSensor = Sensors.newIntegerSensor("bar.baz");
        AttributeSensor<Double> doubleSensor = Sensors.newDoubleSensor("baz.quux");

        Collection<WindowsPerformanceCounterPollConfig<?>> polls = ImmutableSet.<WindowsPerformanceCounterPollConfig<?>>of(
                new WindowsPerformanceCounterPollConfig(stringSensor).performanceCounterName("\\processor information(_total)\\% processor time"),
                new WindowsPerformanceCounterPollConfig(integerSensor).performanceCounterName("\\integer.sensor"),
                new WindowsPerformanceCounterPollConfig(doubleSensor).performanceCounterName("\\double\\sensor\\with\\multiple\\sub\\paths")
        );

        WindowsPerformanceCounterFeed.SendPerfCountersToSensors sendPerfCountersToSensors = new WindowsPerformanceCounterFeed.SendPerfCountersToSensors(entity, polls);

        assertNull(entity.getAttribute(stringSensor));

        StringBuilder responseBuilder = new StringBuilder();
        // NOTE: This builds the response in a different order to which they are passed to the SendPerfCountersToSensors constructor
        // this tests that the values are applied correctly even if the (possibly non-deterministic) order in which
        // they are returned by the Get-Counter scriptlet is different
        addMockResponse(responseBuilder, "\\\\machine.name\\double\\sensor\\with\\multiple\\sub\\paths", "3.1415926");
        addMockResponse(responseBuilder, "\\\\win-lge7uj2blau\\processor information(_total)\\% processor time", "99.9");
        addMockResponse(responseBuilder, "\\\\machine.name\\integer.sensor", "15");

        sendPerfCountersToSensors.onSuccess(new WinRmToolResponse(responseBuilder.toString(), "", 0));

        EntityTestUtils.assertAttributeEquals(entity, stringSensor, "99.9");
        EntityTestUtils.assertAttributeEquals(entity, integerSensor, 15);
        EntityTestUtils.assertAttributeEquals(entity, doubleSensor, 3.1415926);
    }

    private void addMockResponse(StringBuilder responseBuilder, String path, String value) {
        responseBuilder.append(path);
        responseBuilder.append(Strings.repeat(" ", 200 - (path.length() + value.length())));
        responseBuilder.append(value);
        responseBuilder.append("\r\n");
    }

}
