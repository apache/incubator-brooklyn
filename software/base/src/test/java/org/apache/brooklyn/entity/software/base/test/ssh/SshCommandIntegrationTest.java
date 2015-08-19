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
package org.apache.brooklyn.entity.software.base.test.ssh;

import static org.testng.Assert.assertTrue;

import java.io.File;

import org.apache.brooklyn.api.effector.Effector;
import org.apache.brooklyn.api.entity.EntityLocal;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.core.effector.Effectors;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.sensor.Sensors;
import org.apache.brooklyn.core.test.entity.TestApplication;
import org.apache.brooklyn.core.test.entity.TestEntity;
import org.apache.brooklyn.sensor.ssh.SshCommandEffector;
import org.apache.brooklyn.sensor.ssh.SshCommandSensor;
import org.apache.brooklyn.test.EntityTestUtils;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.core.config.ConfigBag;
import org.apache.brooklyn.util.time.Duration;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.apache.brooklyn.location.ssh.SshMachineLocation;

import com.google.common.collect.ImmutableList;

public class SshCommandIntegrationTest {

    final static AttributeSensor<String> SENSOR_STRING = Sensors.newStringSensor("aString", "");
    final static AttributeSensor<Integer> SENSOR_INT = Sensors.newIntegerSensor("aLong", "");
    final static Effector<String> EFFECTOR_SAY_HI = Effectors.effector(String.class, "sayHi").buildAbstract();

    private TestApplication app;
    private SshMachineLocation machine;
    private EntityLocal entity;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        app = TestApplication.Factory.newManagedInstanceForTests();
        machine = app.newLocalhostProvisioningLocation().obtain();
        entity = app.createAndManageChild(EntitySpec.create(TestEntity.class).location(machine));
        app.start(ImmutableList.<Location>of());
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }
    
    @Test(groups="Integration")
    public void testSshSensor() throws Exception {
        File tempFile = File.createTempFile("testSshCommand", "txt");
        tempFile.deleteOnExit();
        new SshCommandSensor<String>(ConfigBag.newInstance()
                .configure(SshCommandSensor.SENSOR_PERIOD, Duration.millis(100))
                .configure(SshCommandSensor.SENSOR_NAME, SENSOR_STRING.getName())
                .configure(SshCommandSensor.SENSOR_COMMAND, "echo foo > "+tempFile.getAbsolutePath()+"\n"
                    + "wc "+tempFile.getAbsolutePath()))
            .apply(entity);
        entity.setAttribute(Attributes.SERVICE_UP, true);

        String val = EntityTestUtils.assertAttributeEventuallyNonNull(entity, SENSOR_STRING);
        assertTrue(val.contains("1"), "val="+val);
        String[] counts = val.trim().split("\\s+");
        Assert.assertEquals(counts.length, 4, "val="+val);
        Assert.assertEquals(counts[0], "1", "val="+val);
    }

    @Test(groups="Integration")
    public void testSshEffector() throws Exception {
        File tempFile = File.createTempFile("testSshCommand", "txt");
        tempFile.deleteOnExit();
        new SshCommandEffector(ConfigBag.newInstance()
                .configure(SshCommandEffector.EFFECTOR_NAME, "sayHi")
                .configure(SshCommandEffector.EFFECTOR_COMMAND, "echo hi"))
            .apply(entity);
        
        String val = entity.invoke(EFFECTOR_SAY_HI, MutableMap.<String,String>of()).get();
        Assert.assertEquals(val.trim(), "hi", "val="+val);
    }

    @Test(groups="Integration")
    public void testSshEffectorWithParameters() throws Exception {
        File tempFile = File.createTempFile("testSshCommand", "txt");
        tempFile.deleteOnExit();
        new SshCommandEffector(ConfigBag.newInstance()
                .configure(SshCommandEffector.EFFECTOR_NAME, "sayHi")
                .configure(SshCommandEffector.EFFECTOR_COMMAND, "echo $foo")
                .configure(SshCommandEffector.EFFECTOR_PARAMETER_DEFS, 
                    MutableMap.<String,Object>of("foo", MutableMap.of("defaultValue", "hi"))))
            .apply(entity);
        
        String val;
        // explicit value
        val = entity.invoke(EFFECTOR_SAY_HI, MutableMap.<String,String>of("foo", "bar")).get();
        Assert.assertEquals(val.trim(), "bar", "val="+val);
        
        // default value
        val = entity.invoke(EFFECTOR_SAY_HI, MutableMap.<String,String>of()).get();
        Assert.assertEquals(val.trim(), "hi", "val="+val);
    }

}
