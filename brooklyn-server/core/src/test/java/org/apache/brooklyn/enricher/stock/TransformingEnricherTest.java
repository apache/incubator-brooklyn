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

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.core.location.SimulatedLocation;
import org.apache.brooklyn.core.sensor.BasicAttributeSensor;
import org.apache.brooklyn.core.test.BrooklynAppUnitTestSupport;
import org.apache.brooklyn.core.test.entity.TestEntity;
import org.apache.brooklyn.enricher.stock.Enrichers;
import org.apache.brooklyn.test.EntityTestUtils;
import org.apache.brooklyn.util.math.MathFunctions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;

public class TransformingEnricherTest extends BrooklynAppUnitTestSupport {

    public static final Logger log = LoggerFactory.getLogger(TransformingEnricherTest.class);
            
    TestEntity producer;
    AttributeSensor<Integer> intSensorA;
    AttributeSensor<Long> target;

    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        producer = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        intSensorA = new BasicAttributeSensor<Integer>(Integer.class, "int.sensor.a");
        target = new BasicAttributeSensor<Long>(Long.class, "long.sensor.target");
        
        app.start(ImmutableList.of(new SimulatedLocation()));
    }
    
    @Test
    public void testTransformingEnricher() throws Exception {
        //ensure previous values get picked up
        producer.sensors().set(intSensorA, 3);

        producer.enrichers().add(Enrichers.builder()
                .transforming(intSensorA)
                //.computing(MathFunctions.times(2)) // TODO calling it before "publishing" means it doesn't check return type!
                .publishing(target)
                .computing((Function)MathFunctions.times(2)) // TODO doesn't match strongly typed int->long
                .build());

        EntityTestUtils.assertAttributeEqualsEventually(producer, target, 6L);
    }
}
