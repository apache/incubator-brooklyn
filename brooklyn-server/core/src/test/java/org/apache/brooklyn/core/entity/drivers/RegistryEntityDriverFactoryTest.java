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
package org.apache.brooklyn.core.entity.drivers;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntityLocal;
import org.apache.brooklyn.api.entity.drivers.DriverDependentEntity;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.core.entity.drivers.RegistryEntityDriverFactory;
import org.apache.brooklyn.core.entity.drivers.ReflectiveEntityDriverFactoryTest.MyDriver;
import org.apache.brooklyn.core.entity.drivers.ReflectiveEntityDriverFactoryTest.MyDriverDependentEntity;
import org.apache.brooklyn.core.location.SimulatedLocation;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.apache.brooklyn.util.collections.MutableMap;

public class RegistryEntityDriverFactoryTest {

    private RegistryEntityDriverFactory factory;
    private SshMachineLocation sshLocation;
    private SimulatedLocation simulatedLocation;

    @BeforeMethod
    public void setUp() throws Exception {
        factory = new RegistryEntityDriverFactory();
        sshLocation = new SshMachineLocation(MutableMap.of("address", "localhost"));
        simulatedLocation = new SimulatedLocation();
    }

    @AfterMethod
    public void tearDown(){
        // nothing to tear down; no management context created
    }

    @Test
    public void testHasDriver() throws Exception {
        DriverDependentEntity<MyDriver> entity = new MyDriverDependentEntity<MyDriver>(MyDriver.class);
        factory.registerDriver(MyDriver.class, SshMachineLocation.class, MyOtherSshDriver.class);
        assertTrue(factory.hasDriver(entity, sshLocation));
        assertFalse(factory.hasDriver(entity, simulatedLocation));
    }

    @Test
    public void testInstantiatesRegisteredDriver() throws Exception {
        DriverDependentEntity<MyDriver> entity = new MyDriverDependentEntity<MyDriver>(MyDriver.class);
        factory.registerDriver(MyDriver.class, SshMachineLocation.class, MyOtherSshDriver.class);
        MyDriver driver = factory.build(entity, sshLocation);
        assertTrue(driver instanceof MyOtherSshDriver);
    }

    public static class MyOtherSshDriver implements MyDriver {
        public MyOtherSshDriver(Entity entity, Location machine) {
        }

        @Override
        public EntityLocal getEntity() {
            throw new UnsupportedOperationException();
        }
        
        @Override
        public Location getLocation() {
            throw new UnsupportedOperationException();
        }
    }
}
