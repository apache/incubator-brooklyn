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
package brooklyn.entity.rebind.transformer;

import static org.testng.Assert.assertEquals;

import java.util.concurrent.Callable;

import org.testng.annotations.Test;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.entity.rebind.RebindTestFixtureWithApp;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.Sensors;

import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;
import com.google.common.reflect.TypeToken;

public class EntityTransformerTest extends RebindTestFixtureWithApp {
    @Test
    public void testRebindTransformer() throws Exception {
        Entity oldE = origApp.createAndManageChild(EntitySpec.create(TransformEntity.class));
        assertEquals(oldE.getAttribute(TransformEntity.GENERATOR).call(), Math.PI);

        //global transformer loading is not test-friendly
        // 1. GlobalTestTransformer will rename the entity being used, simulating library upgrade (can't use catalog items here)
        // 2. TransformEntityTransformer will try to upgrade the entity on the fly
        newApp = rebind();
        Entity newE = Iterables.find(newApp.getChildren(), Predicates.instanceOf(TransformEntity.class));
        assertEquals(newE.getAttribute(TransformEntity.GENERATOR).call(), "static");
    }

    @Test
    public void testPartialRebindTransformer() throws Exception {
        Entity entity = origApp.createAndManageChild(EntitySpec.create(TransformEntity.class));
        assertEquals(entity.getAttribute(TransformEntity.GENERATOR).call(), Math.PI);

        doPartialRebindOfIds(entity.getId());
        assertEquals(entity.getAttribute(TransformEntity.GENERATOR).call(), "static");
    }

    @ImplementedBy(TransformEntityImplV1.class)
    public interface TransformEntity extends Entity {
        @SuppressWarnings("serial")
        public static final AttributeSensor<Callable<Object>> GENERATOR = Sensors.newSensor(new TypeToken<Callable<Object>>() {}, "identity", "identity");
    }

    public static class TransformEntityImplV1 extends AbstractEntity implements TransformEntity {

        public static class MemoryGenerator implements Callable<Object> {
            Object value;

            public MemoryGenerator(Object value) {
                this.value = value;
            }

            @Override
            public Object call() throws Exception {
                return value;
            }

        }

        @Override
        public void init() {
            super.init();
            setAttribute(GENERATOR, new MemoryGenerator(Math.PI));
        }

    }

    @TransformedBy(TransformEntityTransformer.class)
    public static class TransformEntityImplV2 extends AbstractEntity implements TransformEntity {

        public static class StaticGenerator implements Callable<Object> {

            @Override
            public Object call() throws Exception {
                return "static";
            }

        }

        @Override
        public void init() {
            super.init();
            setAttribute(GENERATOR, new StaticGenerator());
        }

    }
}
