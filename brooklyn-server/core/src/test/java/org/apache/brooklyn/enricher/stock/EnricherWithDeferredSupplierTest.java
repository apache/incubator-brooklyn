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

import java.util.Collection;
import java.util.NoSuchElementException;
import java.util.concurrent.Callable;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.mgmt.Task;
import org.apache.brooklyn.api.mgmt.TaskFactory;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.effector.EffectorTasks;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.EntityInternal;
import org.apache.brooklyn.core.entity.EntityPredicates;
import org.apache.brooklyn.core.location.SimulatedLocation;
import org.apache.brooklyn.core.mgmt.BrooklynTaskTags;
import org.apache.brooklyn.core.sensor.BasicAttributeSensor;
import org.apache.brooklyn.core.test.BrooklynAppUnitTestSupport;
import org.apache.brooklyn.core.test.entity.TestEntity;
import org.apache.brooklyn.test.EntityTestUtils;
import org.apache.brooklyn.util.core.task.DeferredSupplier;
import org.apache.brooklyn.util.core.task.TaskBuilder;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

public class EnricherWithDeferredSupplierTest extends BrooklynAppUnitTestSupport {

    public static final Logger log = LoggerFactory.getLogger(EnricherWithDeferredSupplierTest.class);
    
    protected static final ConfigKey<String> TAG = ConfigKeys.newStringConfigKey("mytag");
    
    TestEntity producer;
    TestEntity target;
    AttributeSensor<Integer> sensor;

    @Test
    public void testProducerUsingDeferredSupplier() throws Exception {
        producer = app.createAndManageChild(EntitySpec.create(TestEntity.class)
                .configure(TAG, "myproducer"));
        target = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        sensor = new BasicAttributeSensor<Integer>(Integer.class, "int.sensor.a");
        
        app.start(ImmutableList.of(new SimulatedLocation()));
        
        producer.sensors().set(sensor, 3);

        target.enrichers().add(Enrichers.builder()
                .propagating(sensor)
                .from(new EntityDeferredSupplier("myproducer").newTask())
                .build());

        EntityTestUtils.assertAttributeEqualsEventually(target, sensor, 3);
    }
    
    // TODO This is a cut-down version of DslComponent, from the camp project
    public static class EntityDeferredSupplier implements DeferredSupplier<Entity>, TaskFactory<Task<Entity>> {

        private static final Logger log = LoggerFactory.getLogger(EntityDeferredSupplier.class);
        
        private final String tag;
        
        EntityDeferredSupplier(String tag) {
            this.tag = tag;
        }
        
        protected final static EntityInternal entity() {
            // rely on implicit ThreadLocal for now
            return (EntityInternal) EffectorTasks.findEntity();
        }

        @Override
        public final synchronized Entity get() {
            try {
                if (log.isDebugEnabled())
                    log.debug("Queuing task to resolve child "+tag);
                Entity result = Entities.submit(entity(), newTask()).get();
                if (log.isDebugEnabled())
                    log.debug("Resolved "+result+" from child "+tag);
                return result;
            } catch (Exception e) {
                throw Exceptions.propagate(e);
            }
        }
        
        @Override
        public Task<Entity> newTask() {
            return TaskBuilder.<Entity>builder()
                    .displayName(toString())
                    .tag(BrooklynTaskTags.TRANSIENT_TASK_TAG)
                    .body(new Callable<Entity>() {
                        public Entity call() {
                            EntityInternal entity = entity();
                            Collection<Entity> entitiesToSearch = entity.getManagementContext().getEntityManager().getEntities();
                            Optional<Entity> result = Iterables.tryFind(entitiesToSearch, EntityPredicates.configEqualTo(TAG, tag));
                            
                            if (result.isPresent()) {
                                return result.get();
                            } else {
                                throw new NoSuchElementException("No entity matching id " + tag+" in "+entitiesToSearch);
                            }
                        }})
                    .build();
        }
    }
}
