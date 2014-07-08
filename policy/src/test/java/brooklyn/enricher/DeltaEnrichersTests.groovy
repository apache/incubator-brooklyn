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
package brooklyn.enricher

import static org.testng.Assert.*

import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.basic.AbstractEntity
import brooklyn.entity.basic.Entities
import brooklyn.entity.basic.EntityLocal
import brooklyn.event.Sensor
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.management.SubscriptionContext
import brooklyn.management.internal.LocalManagementContext

class DeltaEnrichersTests {
    
    AbstractApplication app
    
    EntityLocal producer

    Sensor<Integer> intSensor, deltaSensor
    Sensor<Double> avgSensor
    SubscriptionContext subscription
    
    @BeforeMethod
    public void before() {
        app = new AbstractApplication() {}
        producer = new AbstractEntity(app) {}
        Entities.startManagement(app);

        intSensor = new BasicAttributeSensor<Integer>(Integer.class, "int sensor")
        deltaSensor = new BasicAttributeSensor<Double>(Double.class, "delta sensor")
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }

    @Test
    public void testDeltaEnricher() {
        DeltaEnricher delta = new DeltaEnricher<Integer>(producer, intSensor, deltaSensor)
        producer.addEnricher(delta)
        
        delta.onEvent(intSensor.newEvent(producer, 0))
        delta.onEvent(intSensor.newEvent(producer, 0))
        assertEquals(producer.getAttribute(deltaSensor), 0d)
        delta.onEvent(intSensor.newEvent(producer, 1))
        assertEquals(producer.getAttribute(deltaSensor), 1d)
        delta.onEvent(intSensor.newEvent(producer, 3))
        assertEquals(producer.getAttribute(deltaSensor), 2d)
        delta.onEvent(intSensor.newEvent(producer, 8))
        assertEquals(producer.getAttribute(deltaSensor), 5d)
    }
    
    @Test
    public void testMonospaceTimeWeightedDeltaEnricher() {
        TimeWeightedDeltaEnricher delta = 
            TimeWeightedDeltaEnricher.<Integer>getPerSecondDeltaEnricher(producer, intSensor, deltaSensor)
        producer.addEnricher(delta)
        
        delta.onEvent(intSensor.newEvent(producer, 0), 0)
        assertEquals(producer.getAttribute(deltaSensor), null)
        delta.onEvent(intSensor.newEvent(producer, 0), 1000)
        assertEquals(producer.getAttribute(deltaSensor), 0d)
        delta.onEvent(intSensor.newEvent(producer, 1), 2000)
        assertEquals(producer.getAttribute(deltaSensor), 1d)
        delta.onEvent(intSensor.newEvent(producer, 3), 3000)
        assertEquals(producer.getAttribute(deltaSensor), 2d)
        delta.onEvent(intSensor.newEvent(producer, 8), 4000)
        assertEquals(producer.getAttribute(deltaSensor), 5d)
    }
    
    @Test
    public void testVariableTimeWeightedDeltaEnricher() {
        TimeWeightedDeltaEnricher delta = 
            TimeWeightedDeltaEnricher.<Integer>getPerSecondDeltaEnricher(producer, intSensor, deltaSensor)
        producer.addEnricher(delta)
        
        delta.onEvent(intSensor.newEvent(producer, 0), 0)
        delta.onEvent(intSensor.newEvent(producer, 0), 2000)
        assertEquals(producer.getAttribute(deltaSensor), 0d)
        delta.onEvent(intSensor.newEvent(producer, 3), 5000)
        assertEquals(producer.getAttribute(deltaSensor), 1d)
        delta.onEvent(intSensor.newEvent(producer, 7), 7000)
        assertEquals(producer.getAttribute(deltaSensor), 2d)
        delta.onEvent(intSensor.newEvent(producer, 12), 7500)
        assertEquals(producer.getAttribute(deltaSensor), 10d)
        delta.onEvent(intSensor.newEvent(producer, 15), 9500)
        assertEquals(producer.getAttribute(deltaSensor), 1.5d)
    }

    @Test
    public void testPostProcessorCalledForDeltaEnricher() {
        TimeWeightedDeltaEnricher delta = new TimeWeightedDeltaEnricher(producer, intSensor, deltaSensor, 1000, {it+123})
        producer.addEnricher(delta)
        
        delta.onEvent(intSensor.newEvent(producer, 0), 0)
        delta.onEvent(intSensor.newEvent(producer, 0), 1000)
        assertEquals(producer.getAttribute(deltaSensor), 123+0d)
        delta.onEvent(intSensor.newEvent(producer, 1), 2000)
        assertEquals(producer.getAttribute(deltaSensor), 123+1d)
    }
}
