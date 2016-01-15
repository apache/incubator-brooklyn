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
package org.apache.brooklyn.enricher.stock;

import java.util.Arrays;
import java.util.concurrent.Callable;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.location.SimulatedLocation;
import org.apache.brooklyn.core.sensor.BasicAttributeSensor;
import org.apache.brooklyn.core.test.entity.TestApplication;
import org.apache.brooklyn.core.test.entity.TestEntity;
import org.apache.brooklyn.test.Asserts;
import org.apache.brooklyn.util.collections.MutableMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.base.Function;

@SuppressWarnings("deprecation")
public class TransformingEnricherDeprecatedTest {

    public static final Logger log = LoggerFactory.getLogger(TransformingEnricherDeprecatedTest.class);
            
    private static final long TIMEOUT_MS = 10*1000;
//    private static final long SHORT_WAIT_MS = 250;
    
    TestApplication app;
    TestEntity producer;
    AttributeSensor<Integer> intSensorA;
    AttributeSensor<Long> target;

    @BeforeMethod()
    public void before() {
        app = TestApplication.Factory.newManagedInstanceForTests();
        producer = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        intSensorA = new BasicAttributeSensor<Integer>(Integer.class, "int.sensor.a");
        target = new BasicAttributeSensor<Long>(Long.class, "long.sensor.target");
        
        app.start(Arrays.asList(new SimulatedLocation()));
    }
    
    @AfterMethod(alwaysRun=true)
    public void after() {
        if (app!=null) Entities.destroyAll(app.getManagementContext());
    }
    
    @Test
    public void testTransformingEnricher() throws InterruptedException {
        final SensorTransformingEnricher<Integer, Long> e1 = new SensorTransformingEnricher<Integer,Long>(intSensorA, target, new DoubleFn());
        
        producer.sensors().set(intSensorA, 3);
        //ensure previous values get picked up
        producer.enrichers().add(e1);

        Asserts.succeedsEventually(MutableMap.of("timeout", TIMEOUT_MS), new Callable<Object>() { 
                @Override
                public Object call() {
                    Assert.assertEquals(producer.getAttribute(target), (Long)((long)6));
                    return null;
                }});
    }

    private static class DoubleFn implements Function<Integer, Long> {
        @Override
        public Long apply(Integer i) {
            return ((long)i)*2;
        }
    }
}
