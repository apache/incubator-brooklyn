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
package org.apache.brooklyn.feed.windows;

import java.util.Map;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.location.MachineProvisioningLocation;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.core.entity.EntityAsserts;
import org.apache.brooklyn.core.sensor.Sensors;
import org.apache.brooklyn.core.test.BrooklynAppLiveTestSupport;
import org.apache.brooklyn.core.test.entity.TestEntity;
import org.apache.brooklyn.util.collections.MutableMap;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/**
 * WindowsPerformanceCounterFeed Live Test.
 * <p>
 * This test is currently disabled. To run, you must configure a location named {@code WindowsLiveTest}
 * or adapt the {@link #LOCATION_SPEC} below.
 * <p>
 * The location must provide Windows nodes that are running an SSH server on port 22. The login credentials must
 * be either be auto-detectable or configured in brooklyn.properties in the usual fashion.
 * <p>
 * Here is an example configuration from brooklyn.properties for a pre-configured Windows VM
 * running an SSH server with public key authentication:
 * <pre>
 * {@code brooklyn.location.named.WindowsLiveTest=byon:(hosts="ec2-xx-xxx-xxx-xx.eu-west-1.compute.amazonaws.com")
 * brooklyn.location.named.WindowsLiveTest.user=Administrator
 * brooklyn.location.named.WindowsLiveTest.password=pa55word
 * brooklyn.location.named.WindowsLiveTest.osFamily=windows
 * }</pre>
 * The location must by {@code byon} or another primitive type. Unfortunately, it's not possible to
 * use a jclouds location, as adding a dependency on brooklyn-locations-jclouds would cause a
 * cyclic dependency.
 */
public class WindowsPerformanceCounterFeedLiveTest extends BrooklynAppLiveTestSupport {

    final static AttributeSensor<Double> CPU_IDLE_TIME = Sensors.newDoubleSensor("cpu.idleTime", "");
    final static AttributeSensor<Integer> TELEPHONE_LINES = Sensors.newIntegerSensor("telephone.lines", "");

    private static final String LOCATION_SPEC = "named:WindowsLiveTest";

    private Location loc;
    private Entity entity;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        super.setUp();
        
        Map<String,?> allFlags = MutableMap.<String,Object>builder()
                .put("tags", ImmutableList.of(getClass().getName()))
                .build();
        MachineProvisioningLocation<?> provisioningLocation = (MachineProvisioningLocation<?>) 
                mgmt.getLocationRegistry().resolve(LOCATION_SPEC, allFlags);
        loc = provisioningLocation.obtain(ImmutableMap.of());

        entity = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        app.start(ImmutableList.of(loc));
    }

    @Test(groups={"Live","Disabled"})
    public void testRetrievesPerformanceCounters() throws Exception {
        // We can be pretty sure that a Windows instance in the cloud will have zero telephone lines...
        entity.sensors().set(TELEPHONE_LINES, 42);
        WindowsPerformanceCounterFeed feed = WindowsPerformanceCounterFeed.builder()
                .entity(entity)
                .addSensor("\\Processor(_total)\\% Idle Time", CPU_IDLE_TIME)
                .addSensor("\\Telephony\\Lines", TELEPHONE_LINES)
                .build();
        try {
            EntityAsserts.assertAttributeEqualsEventually(entity, TELEPHONE_LINES, 0);
        } finally {
            feed.stop();
        }
    }

}
