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
package brooklyn.location.basic

import org.testng.Assert
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.basic.Entities
import brooklyn.entity.proxying.EntitySpec
import brooklyn.event.basic.PortAttributeSensorAndConfigKey
import brooklyn.event.feed.ConfigToAttributes
import brooklyn.test.entity.TestApplication
import brooklyn.test.entity.TestEntity

public class TestPortSupplierLocation {

    SimulatedLocation l;
    PortAttributeSensorAndConfigKey ps;
    TestApplication app;
    TestEntity e;
    
    @BeforeMethod
    public void setup() {
        l = new SimulatedLocation();
        app = TestApplication.Factory.newManagedInstanceForTests();
        e = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        app.start([l]);
        
        ps = new PortAttributeSensorAndConfigKey("some.port", "for testing", "1234+");
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }

    @Test
    public void testObtainsPort() {
        ConfigToAttributes.apply(e, ps);
        
        int p = e.getAttribute(ps);
        Assert.assertEquals(p, 1234);
        
        //sensor access should keep the same value
        p = e.getAttribute(ps);
        Assert.assertEquals(p, 1234);
    }
    
    @Test
    public void testRepeatedConvertAccessIncrements() {
        int p = ps.getAsSensorValue(e);
        Assert.assertEquals(p, 1234);

        //but direct access should see it as being reserved (not required behaviour, but it is the current behaviour)
        int p2 = ps.getAsSensorValue(e);
        Assert.assertEquals(p2, 1235);
    }

    @Test
    public void testNullBeforeSetting() {
        // currently getting the attribute before explicitly setting return null; i.e. no "auto-set" -- 
        // but this behaviour may be changed
        Integer p = e.getAttribute(ps);
        Assert.assertEquals(p, null);
    }

    @Test
    public void testSimulatedRestrictedPermitted() {
        l.setPermittedPorts(PortRanges.fromString("1240+"));
        
        ConfigToAttributes.apply(e, ps);
        int p = e.getAttribute(ps);
        Assert.assertEquals((int)p, 1240);
    }

}
