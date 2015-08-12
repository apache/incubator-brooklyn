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

import brooklyn.enricher.basic.YamlRollingTimeWindowMeanEnricher.ConfidenceQualifiedNumber;
import brooklyn.entity.basic.AbstractApplication;
import brooklyn.entity.basic.BasicEntity;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.basic.BasicSensorEvent;
import brooklyn.util.time.Duration;

public class YamlRollingTimeWindowMeanEnricherTest {
    
    AbstractApplication app;
    
    BasicEntity producer;

    AttributeSensor<Integer> intSensor;
    AttributeSensor<Double> avgSensor, deltaSensor;
    
    Duration timePeriod = Duration.ONE_SECOND;
    
    YamlTimeWeightedDeltaEnricher<Double> delta;
    YamlRollingTimeWindowMeanEnricher<Double> averager;
    
    ConfidenceQualifiedNumber average;
    SubscriptionContext subscription;
    
    @SuppressWarnings("unchecked")
    @BeforeMethod
    public void before() {
        app = new AbstractApplication() {};
        Entities.startManagement(app);
        producer = app.addChild(EntitySpec.create(BasicEntity.class));

        intSensor = new BasicAttributeSensor<Integer>(Integer.class, "int sensor");
        deltaSensor = new BasicAttributeSensor<Double>(Double.class, "delta sensor");
        avgSensor = new BasicAttributeSensor<Double>(Double.class, "avg sensor");
            
        delta = producer.addEnricher(EnricherSpec.create(YamlTimeWeightedDeltaEnricher.class)
                .configure(YamlTimeWeightedDeltaEnricher.PRODUCER, producer)
                .configure(YamlTimeWeightedDeltaEnricher.SOURCE_SENSOR, intSensor)
                .configure(YamlTimeWeightedDeltaEnricher.TARGET_SENSOR, deltaSensor));

        averager = producer.addEnricher(EnricherSpec.create(YamlRollingTimeWindowMeanEnricher.class)
                .configure(YamlRollingTimeWindowMeanEnricher.PRODUCER, producer)
                .configure(YamlRollingTimeWindowMeanEnricher.SOURCE_SENSOR, deltaSensor)
                .configure(YamlRollingTimeWindowMeanEnricher.TARGET_SENSOR, avgSensor)
                .configure(YamlRollingTimeWindowMeanEnricher.WINDOW_DURATION, timePeriod));
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }
        
    @Test
    public void testDefaultAverageWhenEmpty() {
        ConfidenceQualifiedNumber average = averager.getAverage(0, 0);
        assertEquals(average.value, 0d);
        assertEquals(average.confidence, 0.0d);
    }
    
    protected BasicSensorEvent<Integer> newIntSensorEvent(int value, long timestamp) {
        return new BasicSensorEvent<Integer>(intSensor, producer, value, timestamp);
    }
    protected BasicSensorEvent<Double> newDeltaSensorEvent(double value, long timestamp) {
        return new BasicSensorEvent<Double>(deltaSensor, producer, value, timestamp);
    }

    @Test
    public void testNoRecentValuesAverage() {
        averager.onEvent(newDeltaSensorEvent(10, 0));
        average = averager.getAverage(timePeriod.toMilliseconds()+1000, 0);
        assertEquals(average.value, 10d);
        assertEquals(average.confidence, 0d);
    }

    @Test
    public void testNoRecentValuesUsesLastForAverage() {
        averager.onEvent(newDeltaSensorEvent(10, 0));
        averager.onEvent(newDeltaSensorEvent(20, 10));
        average = averager.getAverage(timePeriod.toMilliseconds()+1000, 0);
        assertEquals(average.value, 20d);
        assertEquals(average.confidence, 0d);
    }

    @Test
    public void testSingleValueTimeAverage() {
        averager.onEvent(newDeltaSensorEvent(10, 1000));
        average = averager.getAverage(1000, 0);
        assertEquals(average.confidence, 0d);
    }

    @Test
    public void testTwoValueAverageForPeriod() {
        averager.onEvent(newDeltaSensorEvent(10, 1000));
        averager.onEvent(newDeltaSensorEvent(10, 2000));
        average = averager.getAverage(2000, 0);
        assertEquals(average.value, 10 /1d);
        assertEquals(average.confidence, 1d);
    }

    @Test
    public void testMonospacedAverage() {
        averager.onEvent(newDeltaSensorEvent(10, 1000));
        averager.onEvent(newDeltaSensorEvent(20, 1250));
        averager.onEvent(newDeltaSensorEvent(30, 1500));
        averager.onEvent(newDeltaSensorEvent(40, 1750));
        averager.onEvent(newDeltaSensorEvent(50, 2000));
        average = averager.getAverage(2000, 0);
        assertEquals(average.value, (20+30+40+50)/4d);
        assertEquals(average.confidence, 1d);
    }

    @Test
    public void testWeightedAverage() {
        averager.onEvent(newDeltaSensorEvent(10, 1000));
        averager.onEvent(newDeltaSensorEvent(20, 1100));
        averager.onEvent(newDeltaSensorEvent(30, 1300));
        averager.onEvent(newDeltaSensorEvent(40, 1600));
        averager.onEvent(newDeltaSensorEvent(50, 2000));
        
        average = averager.getAverage(2000, 0);
        assertEquals(average.value, (20*0.1d)+(30*0.2d)+(40*0.3d)+(50*0.4d));
        assertEquals(average.confidence, 1d);
    }

    @Test
    public void testConfidenceDecay() {
        averager.onEvent(newDeltaSensorEvent(10, 1000));
        averager.onEvent(newDeltaSensorEvent(20, 1250));
        averager.onEvent(newDeltaSensorEvent(30, 1500));
        averager.onEvent(newDeltaSensorEvent(40, 1750));
        averager.onEvent(newDeltaSensorEvent(50, 2000));

        average = averager.getAverage(2250, 0);
        assertEquals(average.value, (30+40+50)/3d);
        assertEquals(average.confidence, 0.75d);
        average = averager.getAverage(2500, 0);
        assertEquals(average.value, (40+50)/2d);
        assertEquals(average.confidence, 0.5d);
        average = averager.getAverage(2750, 0);
        assertEquals(average.value, 50d);
        assertEquals(average.confidence, 0.25d);
        average = averager.getAverage(3000, 0);
        assertEquals(average.value, 50d);
        assertEquals(average.confidence, 0d);
    }

}
