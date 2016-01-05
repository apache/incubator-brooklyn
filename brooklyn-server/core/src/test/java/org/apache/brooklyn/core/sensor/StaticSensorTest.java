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
package org.apache.brooklyn.core.sensor;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.core.entity.EntityAsserts;
import org.apache.brooklyn.core.test.BrooklynAppUnitTestSupport;
import org.apache.brooklyn.entity.stock.BasicEntity;
import org.apache.brooklyn.util.core.config.ConfigBag;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableMap;

public class StaticSensorTest extends BrooklynAppUnitTestSupport {

    @Test
    public void testAddsStaticSensorOfTypeString() {
        BasicEntity entity = app.createAndManageChild(EntitySpec.create(BasicEntity.class)
                .addInitializer(new StaticSensor<String>(ConfigBag.newInstance(ImmutableMap.of(
                        StaticSensor.SENSOR_NAME, "myname",
                        StaticSensor.SENSOR_TYPE, String.class.getName(),
                        StaticSensor.STATIC_VALUE, "myval")))));
        
        EntityAsserts.assertAttributeEquals(entity, Sensors.newSensor(String.class, "myname"), "myval");
    }
    
    @Test
    public void testAddsStaticSensorOfTypeInteger() {
        BasicEntity entity = app.createAndManageChild(EntitySpec.create(BasicEntity.class)
                .addInitializer(new StaticSensor<Integer>(ConfigBag.newInstance(ImmutableMap.of(
                        StaticSensor.SENSOR_NAME, "myname",
                        StaticSensor.SENSOR_TYPE, Integer.class.getName(),
                        StaticSensor.STATIC_VALUE, "1")))));
        
        EntityAsserts.assertAttributeEquals(entity, Sensors.newSensor(Integer.class, "myname"), 1);
    }
}
