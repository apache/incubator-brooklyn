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
package brooklyn.entity.drivers;

import static org.testng.Assert.assertTrue;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.drivers.ReflectiveEntityDriverFactoryTest.MyDriver;
import brooklyn.entity.drivers.ReflectiveEntityDriverFactoryTest.MyDriverDependentEntity;
import brooklyn.entity.drivers.ReflectiveEntityDriverFactoryTest.MySshDriver;
import brooklyn.entity.drivers.RegistryEntityDriverFactoryTest.MyOtherSshDriver;
import brooklyn.location.basic.SimulatedLocation;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.collections.MutableMap;

public class BasicEntityDriverManagerTest {

    private BasicEntityDriverManager manager;
    private SshMachineLocation sshLocation;
    private SimulatedLocation simulatedLocation;

    @BeforeMethod
    public void setUp() throws Exception {
        manager = new BasicEntityDriverManager();
        sshLocation = new SshMachineLocation(MutableMap.of("address", "localhost"));
        simulatedLocation = new SimulatedLocation();
    }

    @AfterMethod
    public void tearDown(){
        // nothing to tear down; no management context created
    }
    
    @Test
    public void testPrefersRegisteredDriver() throws Exception {
        DriverDependentEntity<MyDriver> entity = new MyDriverDependentEntity<MyDriver>(MyDriver.class);
        manager.registerDriver(MyDriver.class, SshMachineLocation.class, MyOtherSshDriver.class);
        assertTrue(manager.build(entity, sshLocation) instanceof MyOtherSshDriver);
    }
    
    @Test
    public void testFallsBackToReflectiveDriver() throws Exception {
        DriverDependentEntity<MyDriver> entity = new MyDriverDependentEntity<MyDriver>(MyDriver.class);
        assertTrue(manager.build(entity, sshLocation) instanceof MySshDriver);
    }
    
    @Test
    public void testRespectsLocationWhenDecidingOnDriver() throws Exception {
        DriverDependentEntity<MyDriver> entity = new MyDriverDependentEntity<MyDriver>(MyDriver.class);
        manager.registerDriver(MyDriver.class, SimulatedLocation.class, MyOtherSshDriver.class);
        assertTrue(manager.build(entity, simulatedLocation) instanceof MyOtherSshDriver);
        assertTrue(manager.build(entity, sshLocation) instanceof MySshDriver);
    }
}
