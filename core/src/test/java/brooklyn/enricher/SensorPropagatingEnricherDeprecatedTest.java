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
package brooklyn.enricher;

import java.util.concurrent.atomic.AtomicReference;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.enricher.basic.SensorPropagatingEnricher;
import brooklyn.entity.BrooklynAppUnitTestSupport;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.event.AttributeSensor;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.event.basic.Sensors;
import brooklyn.test.Asserts;
import brooklyn.test.EntityTestUtils;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.javalang.AtomicReferences;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableMap;

public class SensorPropagatingEnricherDeprecatedTest extends BrooklynAppUnitTestSupport {

    private TestEntity entity;

    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        entity = app.createAndManageChild(EntitySpec.create(TestEntity.class));
    }
    
    @Test
    public void testPropagatesSpecificSensor() {
        app.addEnricher(SensorPropagatingEnricher.newInstanceListeningTo(entity, TestEntity.NAME));

        // name propagated
        entity.setAttribute(TestEntity.NAME, "foo");
        EntityTestUtils.assertAttributeEqualsEventually(app, TestEntity.NAME, "foo");
        
        // sequence not propagated
        entity.setAttribute(TestEntity.SEQUENCE, 2);
        EntityTestUtils.assertAttributeEqualsContinually(MutableMap.of("timeout", 100), app, TestEntity.SEQUENCE, null);
    }
    
    @Test
    public void testPropagatesAllSensors() {
        app.addEnricher(SensorPropagatingEnricher.newInstanceListeningToAllSensors(entity));

        // all attributes propagated
        entity.setAttribute(TestEntity.NAME, "foo");
        entity.setAttribute(TestEntity.SEQUENCE, 2);
        
        EntityTestUtils.assertAttributeEqualsEventually(app, TestEntity.NAME, "foo");
        EntityTestUtils.assertAttributeEqualsEventually(app, TestEntity.SEQUENCE, 2);
        
        // notification-sensor propagated
        final AtomicReference<Integer> notif = new AtomicReference<Integer>();
        app.subscribe(app, TestEntity.MY_NOTIF, new SensorEventListener<Integer>() {
                @Override public void onEvent(SensorEvent<Integer> event) {
                    notif.set(event.getValue());
                }});
        entity.emit(TestEntity.MY_NOTIF, 7);
        Asserts.eventually(AtomicReferences.supplier(notif), Predicates.equalTo(7));
    }
    
    @Test
    public void testPropagatesAllBut() {
        app.addEnricher(SensorPropagatingEnricher.newInstanceListeningToAllSensorsBut(entity, TestEntity.SEQUENCE)) ;

        // name propagated
        entity.setAttribute(TestEntity.NAME, "foo");
        EntityTestUtils.assertAttributeEqualsEventually(app, TestEntity.NAME, "foo");
        
        // sequence not propagated
        entity.setAttribute(TestEntity.SEQUENCE, 2);
        EntityTestUtils.assertAttributeEqualsContinually(MutableMap.of("timeout", 100), app, TestEntity.SEQUENCE, null);
    }
    
    @Test
    public void testPropagatingAsDifferentSensor() {
        final AttributeSensor<String> ANOTHER_ATTRIBUTE = Sensors.newStringSensor("another.attribute", "");
        app.addEnricher(SensorPropagatingEnricher.newInstanceRenaming(entity, ImmutableMap.of(TestEntity.NAME, ANOTHER_ATTRIBUTE)));

        // name propagated as different attribute
        entity.setAttribute(TestEntity.NAME, "foo");
        EntityTestUtils.assertAttributeEqualsEventually(app, ANOTHER_ATTRIBUTE, "foo");
    }
}
