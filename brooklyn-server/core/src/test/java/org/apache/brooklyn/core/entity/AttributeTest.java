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
package org.apache.brooklyn.core.entity;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;

import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.core.sensor.BasicAttributeSensor;
import org.apache.brooklyn.core.test.entity.TestEntityImpl;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

public class AttributeTest {
    static AttributeSensor<String> COLOR = new BasicAttributeSensor<String>(String.class, "my.color");

    TestEntityImpl e;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        e = new TestEntityImpl();
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown(){
        // nothing to tear down; entity was not managed (i.e. had no management context)
    }

    @Test
    public void canGetAndSetAttribute() {
        e.sensors().set(COLOR, "red");
        assertEquals(e.getAttribute(COLOR), "red");
    }
    
    @Test
    public void missingAttributeIsNull() {
        assertEquals(e.getAttribute(COLOR), null);
    }
    
    @Test
    public void canGetAttributeByNameParts() {
        // Initially null
        assertNull(e.getAttributeByNameParts(COLOR.getNameParts()));
        
        // Once set, returns val
        e.sensors().set(COLOR, "red");
        assertEquals(e.getAttributeByNameParts(COLOR.getNameParts()), "red");
    }
}
