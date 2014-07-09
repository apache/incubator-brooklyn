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
package brooklyn.entity;

import static org.testng.Assert.assertEquals;

import java.util.Collection;
import java.util.List;

import org.testng.Assert;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.annotation.EffectorParam;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.basic.BasicParameterType;
import brooklyn.entity.basic.MethodEffector;
import brooklyn.entity.effector.Effectors;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.entity.trait.Startable;
import brooklyn.location.Location;
import brooklyn.management.internal.EffectorUtils;

import com.google.common.collect.ImmutableList;

/**
 * Test the operation of the {@link Effector} implementations.
 *
 * TODO clarify test purpose
 */
public class EffectorMetadataTest extends BrooklynAppUnitTestSupport {
    
    private MyAnnotatedEntity e1;
    private MyOverridingEntity e2;

    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        e1 = app.createAndManageChild(EntitySpec.create(MyAnnotatedEntity.class));
        e2 = app.createAndManageChild(EntitySpec.create(MyOverridingEntity.class));
    }

    @Test
    public void testEffectorMetaDataFromAnnotationsWithConstant() {
        Effector<?> effector = EffectorUtils.findEffectorDeclared(e1, "effWithNewAnnotation").get();
        Assert.assertTrue(Effectors.sameSignature(effector, MyAnnotatedEntity.EFF_WITH_NEW_ANNOTATION));
        assertEquals(effector.getName(), "effWithNewAnnotation");
        assertEquals(effector.getDescription(), "my effector description");
        assertEquals(effector.getReturnType(), String.class);
        assertParametersEqual(
                effector.getParameters(), 
                ImmutableList.<ParameterType<?>>of(
                        new BasicParameterType<String>("param1", String.class, "my param description", "my default val")));
    }

    @Test
    public void testEffectorMetaDataFromAnnotationsWithoutConstant() {
        Effector<?> effector = EffectorUtils.findEffectorDeclared(e1, "effWithAnnotationButNoConstant").get();
        assertEquals(effector.getName(), "effWithAnnotationButNoConstant");
        assertEquals(effector.getDescription(), "my effector description");
        assertEquals(effector.getReturnType(), String.class);
        assertParametersEqual(
                effector.getParameters(), 
                ImmutableList.<ParameterType<?>>of(
                        new BasicParameterType<String>("param1", String.class, "my param description", "my default val")));
    }

    @SuppressWarnings("rawtypes")
    @Test
    public void testEffectorMetaDataFromOverriddenMethod() {
        // Overridden with new annotations
        Effector<?> startEffector = EffectorUtils.findEffectorDeclared(e2, "start").get();
        assertEquals(startEffector.getName(), "start");
        assertEquals(startEffector.getDescription(), "My overridden start description");
        assertEquals(startEffector.getReturnType(), void.class);
        assertParametersEqual(
                startEffector.getParameters(), 
                ImmutableList.<ParameterType<?>>of(
                        new BasicParameterType<Collection>("locations", Collection.class, "my overridden param description", null)));
    }

    private void assertParametersEqual(List<ParameterType<?>> actuals, List<ParameterType<?>> expecteds) {
        assertEquals(actuals.size(), expecteds.size(), "actual="+actuals);
        for (int i = 0; i < actuals.size(); i++) {
            ParameterType<?> actual = actuals.get(i);
            ParameterType<?> expected = expecteds.get(i);
            assertParameterEqual(actual, expected);
        }
    }
    
    private void assertParameterEqual(ParameterType<?> actual, ParameterType<?> expected) {
        assertEquals(actual.getName(), expected.getName(), "actual="+actual);
        assertEquals(actual.getDescription(), expected.getDescription(), "actual="+actual);
        assertEquals(actual.getParameterClass(), expected.getParameterClass(), "actual="+actual);
        assertEquals(actual.getParameterClassName(), expected.getParameterClassName(), "actual="+actual);
    }

    @ImplementedBy(MyAnnotatedEntityImpl.class)
    public interface MyAnnotatedEntity extends Entity {
        static MethodEffector<String> EFF_WITH_NEW_ANNOTATION = new MethodEffector<String>(MyAnnotatedEntity.class, "effWithNewAnnotation");

        @brooklyn.entity.annotation.Effector(description="my effector description")
        public String effWithNewAnnotation(
                @EffectorParam(name="param1", defaultValue="my default val", description="my param description") String param1);
        
        @brooklyn.entity.annotation.Effector(description="my effector description")
        public String effWithAnnotationButNoConstant(
                @EffectorParam(name="param1", defaultValue="my default val", description="my param description") String param1);
    }
    
    public static class MyAnnotatedEntityImpl extends AbstractEntity implements MyAnnotatedEntity {
        @Override
        public String effWithNewAnnotation(String param1) {
            return param1;
        }

        @Override
        public String effWithAnnotationButNoConstant(String param1) {
            return param1;
        }
    }
    
    @ImplementedBy(MyOverridingEntityImpl.class)
    public interface MyOverridingEntity extends Entity, Startable {
        brooklyn.entity.Effector<Void> START = Effectors.effector(Startable.START)
            .description("My overridden start description")
            .parameter(Collection.class, "locations", "my overridden param description")
            .build();
    }

    public static class MyOverridingEntityImpl extends AbstractEntity implements MyOverridingEntity {

        @Override
        public void restart() {
        }

        @Override
        public void start(Collection<? extends Location> locations2) {
        }

        @Override
        public void stop() {
        }
    }
}
