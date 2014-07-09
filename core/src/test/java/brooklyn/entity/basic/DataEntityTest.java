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
package brooklyn.entity.basic;

import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicReference;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.proxying.EntitySpec;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.Sensors;
import brooklyn.location.LocationSpec;
import brooklyn.location.basic.SimulatedLocation;
import brooklyn.management.ManagementContext;
import brooklyn.test.Asserts;
import brooklyn.test.EntityTestUtils;
import brooklyn.test.entity.LocalManagementContextForTests;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.collections.MutableMap;

import com.google.common.base.Predicates;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;

public class DataEntityTest {

    private ManagementContext managementContext;
    private SimulatedLocation loc;
    private TestApplication app;
    private DataEntity entity;
    private AttributeSensor<String>  stringSensor = Sensors.newStringSensor("string", "String sensor");
    private AttributeSensor<Long>  longSensor = Sensors.newLongSensor("long", "Long sensor");
    private AtomicReference<String> reference = new AtomicReference<String>();
    private Supplier<Long> currentTimeMillis = new Supplier<Long>() {
        @Override
        public Long get() {
            return System.currentTimeMillis();
        }
    };
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        managementContext = new LocalManagementContextForTests();
        loc = managementContext.getLocationManager().createLocation(LocationSpec.create(SimulatedLocation.class));
        app = ApplicationBuilder.newManagedApp(TestApplication.class, managementContext);
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (managementContext != null) Entities.destroyAllCatching(managementContext);
    }

    @Test
    public void testSupplierSetsAttribute() throws Exception {
        entity = app.createAndManageChild(EntitySpec.create(DataEntity.class)
                .configure(DataEntity.POLL_PERIOD, 50L)
                .configure(DataEntity.SENSOR_SUPPLIER_MAP, MutableMap.<AttributeSensor<?>, Supplier<?>>of(stringSensor, new Supplier<String>() {
                    @Override
                    public String get() {
                        return reference.get();
                    }
                })));
        app.start(ImmutableList.of(loc));

        reference.set("new");

        EntityTestUtils.assertAttributeEqualsEventually(entity, stringSensor, "new");
    }

    @Test
    public void testSupplierIsPolled() throws Exception {
        entity = app.createAndManageChild(EntitySpec.create(DataEntity.class)
                .configure(DataEntity.POLL_PERIOD, 50L)
                .configure(DataEntity.SENSOR_SUPPLIER_MAP, MutableMap.<AttributeSensor<?>, Supplier<?>>of(longSensor, currentTimeMillis)));
        app.start(ImmutableList.of(loc));

        Asserts.eventually(Entities.attributeSupplier(entity, longSensor), Predicates.notNull());
        final Long first = entity.getAttribute(longSensor);
        assertNotNull(first);

        Asserts.succeedsEventually(new Runnable() {
            @Override
            public void run() {
                Long second = entity.getAttribute(longSensor);
                assertNotNull(second);
                assertTrue(second.longValue() > first.longValue());
            }
        });
    }

    @Test
    public void testWithMultipleSuppliers() throws Exception {
        entity = app.createAndManageChild(EntitySpec.create(DataEntity.class)
                .configure(DataEntity.POLL_PERIOD, 50L)
                .configure(DataEntity.SENSOR_SUPPLIER_MAP, MutableMap.<AttributeSensor<?>, Supplier<?>>builder()
                        .put(longSensor, currentTimeMillis)
                        .put(stringSensor, new Supplier<String>() {
                                @Override
                                public String get() {
                                    return reference.get();
                                }
                            })
                        .build()));
        app.start(ImmutableList.of(loc));

        reference.set("value");

        Asserts.succeedsEventually(new Runnable() {
            @Override
            public void run() {
                Long first = entity.getAttribute(longSensor);
                String second = entity.getAttribute(stringSensor);

                assertNotNull(first);
                assertNotNull(second);
            }
        });
    }
}
