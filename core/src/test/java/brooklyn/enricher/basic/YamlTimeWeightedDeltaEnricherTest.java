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
package brooklyn.enricher.basic;

import static org.testng.Assert.assertEquals;

import org.apache.brooklyn.management.SubscriptionContext;
import org.apache.brooklyn.policy.EnricherSpec;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.AbstractApplication;
import brooklyn.entity.basic.BasicEntity;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.basic.BasicSensorEvent;

public class YamlTimeWeightedDeltaEnricherTest {
    
    AbstractApplication app;
    
    BasicEntity producer;

    AttributeSensor<Integer> intSensor;
    AttributeSensor<Double> avgSensor, deltaSensor;
    SubscriptionContext subscription;
    
    @BeforeMethod
    public void before() {
        app = new AbstractApplication() {};
        Entities.startManagement(app);
        producer = app.addChild(EntitySpec.create(BasicEntity.class));

        intSensor = new BasicAttributeSensor<Integer>(Integer.class, "int sensor");
        deltaSensor = new BasicAttributeSensor<Double>(Double.class, "delta sensor");
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }
    
    @Test
    public void testMonospaceTimeWeightedDeltaEnricher() {
        @SuppressWarnings("unchecked")
        YamlTimeWeightedDeltaEnricher<Integer> delta = producer.addEnricher(EnricherSpec.create(YamlTimeWeightedDeltaEnricher.class)
            .configure(YamlTimeWeightedDeltaEnricher.PRODUCER, producer)
            .configure(YamlTimeWeightedDeltaEnricher.SOURCE_SENSOR, intSensor)
            .configure(YamlTimeWeightedDeltaEnricher.TARGET_SENSOR, deltaSensor));
        
        delta.onEvent(newIntSensorEvent(0, 0));
        assertEquals(producer.getAttribute(deltaSensor), null);
        delta.onEvent(newIntSensorEvent(0, 1000));
        assertEquals(producer.getAttribute(deltaSensor), 0d);
        delta.onEvent(newIntSensorEvent(1, 2000));
        assertEquals(producer.getAttribute(deltaSensor), 1d);
        delta.onEvent(newIntSensorEvent(3, 3000));
        assertEquals(producer.getAttribute(deltaSensor), 2d);
        delta.onEvent(newIntSensorEvent(8, 4000));
        assertEquals(producer.getAttribute(deltaSensor), 5d);
    }
    
    protected BasicSensorEvent<Integer> newIntSensorEvent(int value, long timestamp) {
        return new BasicSensorEvent<Integer>(intSensor, producer, value, timestamp);
    }
    
    @Test
    public void testVariableTimeWeightedDeltaEnricher() {
        @SuppressWarnings("unchecked")
        YamlTimeWeightedDeltaEnricher<Integer> delta = producer.addEnricher(EnricherSpec.create(YamlTimeWeightedDeltaEnricher.class)
            .configure(YamlTimeWeightedDeltaEnricher.PRODUCER, producer)
            .configure(YamlTimeWeightedDeltaEnricher.SOURCE_SENSOR, intSensor)
            .configure(YamlTimeWeightedDeltaEnricher.TARGET_SENSOR, deltaSensor));
        
        delta.onEvent(newIntSensorEvent(0, 0));
        delta.onEvent(newIntSensorEvent(0, 2000));
        assertEquals(producer.getAttribute(deltaSensor), 0d);
        delta.onEvent(newIntSensorEvent(3, 5000));
        assertEquals(producer.getAttribute(deltaSensor), 1d);
        delta.onEvent(newIntSensorEvent(7, 7000));
        assertEquals(producer.getAttribute(deltaSensor), 2d);
        delta.onEvent(newIntSensorEvent(12, 7500));
        assertEquals(producer.getAttribute(deltaSensor), 10d);
        delta.onEvent(newIntSensorEvent(15, 9500));
        assertEquals(producer.getAttribute(deltaSensor), 1.5d);
    }

}
