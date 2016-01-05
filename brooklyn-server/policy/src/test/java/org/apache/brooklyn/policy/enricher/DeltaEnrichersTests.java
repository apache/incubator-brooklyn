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
package org.apache.brooklyn.policy.enricher;

import static org.testng.Assert.assertEquals;

import org.apache.brooklyn.api.entity.EntityLocal;
import org.apache.brooklyn.api.mgmt.SubscriptionContext;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.api.sensor.Sensor;
import org.apache.brooklyn.core.entity.AbstractApplication;
import org.apache.brooklyn.core.entity.AbstractEntity;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.sensor.BasicAttributeSensor;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.base.Function;

public class DeltaEnrichersTests {
    
    AbstractApplication app;
    
    EntityLocal producer;

    Sensor<Integer> intSensor;
    Sensor<Double> avgSensor;
    SubscriptionContext subscription;
    
    @BeforeMethod
    public void before() {
        app = new AbstractApplication() {};
        producer = new AbstractEntity(app) {};
        producer.setParent(app);
        Entities.startManagement(app);

        intSensor = new BasicAttributeSensor<Integer>(Integer.class, "int sensor");
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }

    @Test
    public void testDeltaEnricher() {
        AttributeSensor<Integer> deltaSensor = new BasicAttributeSensor<Integer>(Integer.class, "delta sensor");
        DeltaEnricher<Integer> delta = new DeltaEnricher<Integer>(producer, intSensor, deltaSensor);
        producer.enrichers().add(delta);
        
        delta.onEvent(intSensor.newEvent(producer, 0));
        delta.onEvent(intSensor.newEvent(producer, 0));
        assertEquals(producer.getAttribute(deltaSensor), (Integer)0);
        delta.onEvent(intSensor.newEvent(producer, 1));
        assertEquals(producer.getAttribute(deltaSensor), (Integer)1);
        delta.onEvent(intSensor.newEvent(producer, 3));
        assertEquals(producer.getAttribute(deltaSensor), (Integer)2);
        delta.onEvent(intSensor.newEvent(producer, 8));
        assertEquals(producer.getAttribute(deltaSensor), (Integer)5);
    }
    
    @Test
    public void testMonospaceTimeWeightedDeltaEnricher() {
        AttributeSensor<Double> deltaSensor = new BasicAttributeSensor<Double>(Double.class, "per second delta delta sensor");
        TimeWeightedDeltaEnricher<Integer> delta =
            TimeWeightedDeltaEnricher.<Integer>getPerSecondDeltaEnricher(producer, intSensor, deltaSensor);
        producer.enrichers().add(delta);
        
        delta.onEvent(intSensor.newEvent(producer, 0), 0);
        assertEquals(producer.getAttribute(deltaSensor), null);
        delta.onEvent(intSensor.newEvent(producer, 0), 1000);
        assertEquals(producer.getAttribute(deltaSensor), 0d);
        delta.onEvent(intSensor.newEvent(producer, 1), 2000);
        assertEquals(producer.getAttribute(deltaSensor), 1d);
        delta.onEvent(intSensor.newEvent(producer, 3), 3000);
        assertEquals(producer.getAttribute(deltaSensor), 2d);
        delta.onEvent(intSensor.newEvent(producer, 8), 4000);
        assertEquals(producer.getAttribute(deltaSensor), 5d);
    }
    
    @Test
    public void testVariableTimeWeightedDeltaEnricher() {
        AttributeSensor<Double> deltaSensor = new BasicAttributeSensor<Double>(Double.class, "per second delta delta sensor");
        TimeWeightedDeltaEnricher<Integer> delta =
            TimeWeightedDeltaEnricher.<Integer>getPerSecondDeltaEnricher(producer, intSensor, deltaSensor);
        producer.enrichers().add(delta);
        
        delta.onEvent(intSensor.newEvent(producer, 0), 0);
        delta.onEvent(intSensor.newEvent(producer, 0), 2000);
        assertEquals(producer.getAttribute(deltaSensor), 0d);
        delta.onEvent(intSensor.newEvent(producer, 3), 5000);
        assertEquals(producer.getAttribute(deltaSensor), 1d);
        delta.onEvent(intSensor.newEvent(producer, 7), 7000);
        assertEquals(producer.getAttribute(deltaSensor), 2d);
        delta.onEvent(intSensor.newEvent(producer, 12), 7500);
        assertEquals(producer.getAttribute(deltaSensor), 10d);
        delta.onEvent(intSensor.newEvent(producer, 15), 9500);
        assertEquals(producer.getAttribute(deltaSensor), 1.5d);
    }

    @Test
    public void testPostProcessorCalledForDeltaEnricher() {
        AttributeSensor<Double> deltaSensor = new BasicAttributeSensor<Double>(Double.class, "per second delta delta sensor");
        TimeWeightedDeltaEnricher<Integer> delta = new TimeWeightedDeltaEnricher<Integer>(producer, intSensor, deltaSensor, 1000, new AddConstant(123d));
        producer.enrichers().add(delta);
        
        delta.onEvent(intSensor.newEvent(producer, 0), 0);
        delta.onEvent(intSensor.newEvent(producer, 0), 1000);
        assertEquals(producer.getAttribute(deltaSensor), 123+0d);
        delta.onEvent(intSensor.newEvent(producer, 1), 2000);
        assertEquals(producer.getAttribute(deltaSensor), 123+1d);
    }

    private static class AddConstant implements Function<Double, Double> {
        private Double constant;

        public AddConstant(Double constant) {
            super();
            this.constant = constant;
        }

        @Override
        public Double apply(Double input) {
            return input + constant;
        }
    }
}
