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

import static org.testng.Assert.assertTrue;

import org.apache.brooklyn.api.entity.drivers.DriverDependentEntity;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.drivers.ReflectiveEntityDriverFactoryTest.MyDriver;
import org.apache.brooklyn.core.entity.drivers.ReflectiveEntityDriverFactoryTest.MyDriverDependentEntity;
import org.apache.brooklyn.core.entity.drivers.RegistryEntityDriverFactoryTest.MyOtherSshDriver;
import org.apache.brooklyn.core.test.entity.LocalManagementContextForTests;
import org.apache.brooklyn.util.collections.MutableMap;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.apache.brooklyn.location.ssh.SshMachineLocation;

public class EntityDriverRegistryTest {

    private ManagementContext managementContext;
    private SshMachineLocation sshLocation;

    @BeforeMethod
    public void setUp() throws Exception {
        managementContext = new LocalManagementContextForTests();
        sshLocation = new SshMachineLocation(MutableMap.of("address", "localhost"));
    }

    @AfterMethod
    public void tearDown(){
        if (managementContext != null) Entities.destroyAll(managementContext);
    }

    @Test
    public void testInstantiatesRegisteredDriver() throws Exception {
        managementContext.getEntityDriverManager().registerDriver(MyDriver.class, SshMachineLocation.class, MyOtherSshDriver.class);
        DriverDependentEntity<MyDriver> entity = new MyDriverDependentEntity<MyDriver>(MyDriver.class);
        MyDriver driver = managementContext.getEntityDriverManager().build(entity, sshLocation);
        assertTrue(driver instanceof MyOtherSshDriver);
    }
}
