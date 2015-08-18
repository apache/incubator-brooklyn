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

import static org.testng.Assert.assertEquals;

import java.util.concurrent.TimeUnit;

import org.apache.brooklyn.api.entity.basic.EntityLocal;
import org.apache.brooklyn.api.entity.proxying.EntitySpec;
import org.apache.brooklyn.api.event.AttributeSensor;
import org.apache.brooklyn.api.event.Sensor;
import org.apache.brooklyn.api.management.SubscriptionContext;
import org.apache.brooklyn.core.event.basic.BasicSensorEvent;
import org.apache.brooklyn.core.event.basic.Sensors;
import org.apache.brooklyn.test.entity.TestApplication;
import org.apache.brooklyn.test.entity.TestEntity;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;

public class TimeFractionDeltaEnricherTest {

    private static final double PRECISION = 0.000001;
    
    private TestApplication app;
    private EntityLocal producer;

    Sensor<Integer> intSensor;
    AttributeSensor<Double> fractionSensor;
    SubscriptionContext subscription;
    
    @BeforeMethod(alwaysRun=true)
    public void before() {
        app = TestApplication.Factory.newManagedInstanceForTests();
        producer = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        
        intSensor = Sensors.newIntegerSensor("int sensor");
        fractionSensor = Sensors.newDoubleSensor("fraction sensor");
    }

    @AfterMethod(alwaysRun=true)
    public void after() {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }

    @Test
    public void testCalculatesFractions() {
        TimeFractionDeltaEnricher<Integer> enricher = new TimeFractionDeltaEnricher<Integer>(producer, intSensor, fractionSensor, TimeUnit.MILLISECONDS);
        producer.addEnricher(enricher);
        
        enricher.onEvent(new BasicSensorEvent<Integer>(intSensor, producer, 0, 1000000L));
        enricher.onEvent(new BasicSensorEvent<Integer>(intSensor, producer, 0, 1001000L));
        assertEquals(producer.getAttribute(fractionSensor), 0d);
        
        enricher.onEvent(new BasicSensorEvent<Integer>(intSensor, producer, 100, 1002000L));
        assertEquals(producer.getAttribute(fractionSensor), 0.1d, PRECISION);
        
        enricher.onEvent(new BasicSensorEvent<Integer>(intSensor, producer, 300, 1003000L));
        assertEquals(producer.getAttribute(fractionSensor), 0.2d, PRECISION);
        
        enricher.onEvent(new BasicSensorEvent<Integer>(intSensor, producer, 2300, 1004000L));
        assertEquals(producer.getAttribute(fractionSensor), 2d, PRECISION);
    }
    
    @Test
    public void testConvertsTimeUnits() {
        TimeFractionDeltaEnricher<Integer> enricher = new TimeFractionDeltaEnricher<Integer>(producer, intSensor, fractionSensor, TimeUnit.MICROSECONDS);
        producer.addEnricher(enricher);
        
        enricher.onEvent(new BasicSensorEvent<Integer>(intSensor, producer, 0, 1000000L));
        enricher.onEvent(new BasicSensorEvent<Integer>(intSensor, producer, 1000000, 1001000L));
        assertEquals(producer.getAttribute(fractionSensor), 1d);
    }
    
    @Test
    public void testConverts100NanosTimeBlocks() {
        TimeFractionDeltaEnricher<Integer> enricher = new TimeFractionDeltaEnricher<Integer>(producer, intSensor, fractionSensor, 100);
        producer.addEnricher(enricher);
        
        enricher.onEvent(new BasicSensorEvent<Integer>(intSensor, producer, 0, 1000000L));
        enricher.onEvent(new BasicSensorEvent<Integer>(intSensor, producer, 10000000, 1001000L));
        assertEquals(producer.getAttribute(fractionSensor), 1d);
    }
}
