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

import java.util.Map;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.Sensors;
import brooklyn.location.Location;
import brooklyn.location.MachineLocation;
import brooklyn.location.MachineProvisioningLocation;
import brooklyn.management.ManagementContext;
import brooklyn.test.EntityTestUtils;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.collections.MutableMap;

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
 * brooklyn.location.named.WindowsLiveTest.privateKeyFile = ~/.ssh/id_rsa
 * brooklyn.location.named.WindowsLiveTest.publicKeyFile = ~/.ssh/id_rsa.pub
 * }</pre>
 * The location must by {@code byon} or another primitive type. Unfortunately, it's not possible to
 * use a jclouds location, as adding a dependency on brooklyn-locations-jclouds would cause a
 * cyclic dependency.
 */
public class WindowsPerformanceCounterFeedLiveTest {

    final static AttributeSensor<Double> CPU_IDLE_TIME =
            Sensors.newDoubleSensor("cpu.idleTime", "");
    final static AttributeSensor<Integer> TELEPHONE_LINES =
            Sensors.newIntegerSensor("telephone.lines", "");

    private static final String LOCATION_SPEC = "named:WindowsLiveTest";

    private ManagementContext mgmt;
    private TestApplication app;
    private Location loc;
    private EntityLocal entity;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        if (mgmt!=null) {
            app = ApplicationBuilder.newManagedApp(TestApplication.class, mgmt);
        } else {
            app = ApplicationBuilder.newManagedApp(TestApplication.class);
            mgmt = ((EntityInternal)app).getManagementContext();
        }

        Map<String,?> allFlags = MutableMap.<String,Object>builder()
                .put("tags", ImmutableList.of(getClass().getName()))
                .build();
        MachineProvisioningLocation<? extends MachineLocation> provisioningLocation =
                (MachineProvisioningLocation<? extends MachineLocation>)
                        mgmt.getLocationRegistry().resolve(LOCATION_SPEC, allFlags);
        loc = provisioningLocation.obtain(ImmutableMap.of());

        entity = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        app.start(ImmutableList.of(loc));
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (mgmt != null) Entities.destroyAllCatching(mgmt);
        mgmt = null;
    }

    @Test(groups={"Live","Disabled"}, enabled=false)
    public void testRetrievesPerformanceCounters() throws Exception {
        // We can be pretty sure that a Windows instance in the cloud will have zero telephone lines...
        entity.setAttribute(TELEPHONE_LINES, 42);
        WindowsPerformanceCounterFeed feed = WindowsPerformanceCounterFeed.builder()
                .entity(entity)
                .addSensor("\\Processor(_total)\\% Idle Time", CPU_IDLE_TIME)
                .addSensor("\\Telephony\\Lines", TELEPHONE_LINES)
                .build();
        try {
            EntityTestUtils.assertAttributeEqualsEventually(entity, TELEPHONE_LINES, 0);
        } finally {
            feed.stop();
        }
    }

}
