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
package org.apache.brooklyn.location.basic;

import static org.testng.Assert.assertEquals;

import org.apache.brooklyn.api.entity.proxying.EntitySpec;
import org.apache.brooklyn.test.entity.TestEntity;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.BrooklynAppUnitTestSupport;
import brooklyn.event.basic.PortAttributeSensorAndConfigKey;
import brooklyn.event.feed.ConfigToAttributes;

import com.google.common.collect.ImmutableList;

public class TestPortSupplierLocation extends BrooklynAppUnitTestSupport {

    SimulatedLocation loc;
    PortAttributeSensorAndConfigKey ps;
    TestEntity entity;
    
    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        loc = app.newSimulatedLocation();
        entity = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        app.start(ImmutableList.of(loc));
        
        ps = new PortAttributeSensorAndConfigKey("some.port", "for testing", "1234+");
    }

    @Test
    public void testObtainsPort() throws Exception {
        ConfigToAttributes.apply(entity, ps);
        
        int p = entity.getAttribute(ps);
        assertEquals(p, 1234);
        
        //sensor access should keep the same value
        p = entity.getAttribute(ps);
        assertEquals(p, 1234);
    }
    
    @Test
    public void testRepeatedConvertAccessIncrements() throws Exception {
        int p = ps.getAsSensorValue(entity);
        assertEquals(p, 1234);

        //but direct access should see it as being reserved (not required behaviour, but it is the current behaviour)
        int p2 = ps.getAsSensorValue(entity);
        assertEquals(p2, 1235);
    }

    @Test
    public void testNullBeforeSetting() throws Exception {
        // currently getting the attribute before explicitly setting return null; i.e. no "auto-set" -- 
        // but this behaviour may be changed
        Integer p = entity.getAttribute(ps);
        assertEquals(p, null);
    }

    @Test
    public void testSimulatedRestrictedPermitted() throws Exception {
        loc.setPermittedPorts(PortRanges.fromString("1240+"));
        
        ConfigToAttributes.apply(entity, ps);
        int p = entity.getAttribute(ps);
        assertEquals((int)p, 1240);
    }

}
