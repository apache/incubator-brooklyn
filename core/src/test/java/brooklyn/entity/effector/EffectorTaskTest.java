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
package brooklyn.entity.effector;

import java.util.concurrent.Callable;

import org.testng.Assert;
import org.testng.annotations.Test;

import brooklyn.entity.BrooklynAppUnitTestSupport;
import brooklyn.entity.Effector;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.basic.BrooklynTaskTags;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.effector.EffectorTasks.EffectorTaskFactory;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.trait.Startable;
import brooklyn.management.HasTaskChildren;
import brooklyn.management.Task;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.task.DynamicSequentialTask;
import brooklyn.util.task.DynamicTasks;
import brooklyn.util.task.TaskBuilder;
import brooklyn.util.task.Tasks;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;

public class EffectorTaskTest extends BrooklynAppUnitTestSupport {

    // ----------- syntax 1 -- effector with body in a class
    
    public static final Effector<Integer> DOUBLE_1 = Effectors.effector(Integer.class, "double")
            .description("doubles the given number")
            .parameter(Integer.class, "numberToDouble")
            .impl(new EffectorBody<Integer>() {
                public Integer call(ConfigBag parameters) {
                    // do a sanity check
                    Assert.assertNotNull(entity());
                    
                    // finally double the input
                    return 2*(Integer)parameters.getStringKey("numberToDouble");
                }
            })
            .build();

    public static class DoublingEntity extends AbstractEntity {
        public static final Effector<Integer> DOUBLE = EffectorTaskTest.DOUBLE_1;
    }

    @Test
    public void testSyntaxOneDouble1() throws Exception {
        // just use "dynamic" support of effector
        Assert.assertEquals(app.invoke(DOUBLE_1, MutableMap.of("numberToDouble", 3)).get(), (Integer)6);
    }
    
    @Test
    public void testSyntaxOneTaggedCorrectly() throws Exception {
        Task<Integer> t = app.invoke(DOUBLE_1, MutableMap.of("numberToDouble", 3));
        t.get();
        checkTags(t, app, DOUBLE_1, false);
    }
    
    @Test
    // also assert it works when the effector is defined on an entity
    public void testSimpleEffectorOnEntity() throws Exception {
        Entity doubler = app.createAndManageChild(EntitySpec.create(Entity.class, DoublingEntity.class));
        
        Assert.assertEquals(doubler.invoke(DOUBLE_1, MutableMap.of("numberToDouble", 3)).get(), (Integer)6);
    }

    @Test
    // also assert it works when an abstract effector name is passed in to the entity
    public void testSimpleEffectorNameMatching() throws Exception {
        Entity doubler = app.createAndManageChild(EntitySpec.create(Entity.class, DoublingEntity.class));
        
        Assert.assertEquals(doubler.invoke(Effectors.effector(Integer.class, "double").buildAbstract(), MutableMap.of("numberToDouble", 3)).get(), (Integer)6);
    }


    // ----------- syntax 2 -- effector with body built with fluent API
    
    public static EffectorTaskFactory<Integer> times(final EffectorTaskFactory<Integer> x, final int y) {
        return new EffectorTaskFactory<Integer>() {
            @Override
            public Task<Integer> newTask(final Entity entity, final Effector<Integer> effector, final ConfigBag parameters) {
                return TaskBuilder.<Integer>builder().name("times").body(new Callable<Integer>() { public Integer call() { 
                    return DynamicTasks.get( x.newTask(entity, effector, parameters) )*y; 
                } }).build();
            }
        };
    }

    public static final Effector<Integer> DOUBLE_2 = Effectors.effector(Integer.class, "double")
            .description("doubles the given number")
            .parameter(Integer.class, "numberToDouble")
            .impl(times(EffectorTasks.parameter(Integer.class, "numberToDouble"), 2))
            .build();

    @Test
    public void testSyntaxTwoDouble2() throws Exception {
        Assert.assertEquals(app.invoke(DOUBLE_2, MutableMap.of("numberToDouble", 3)).get(), (Integer)6);
    }

    @Test
    public void testEffectorImplTaggedCorrectly() throws Exception {
        Task<Integer> t = app.invoke(DOUBLE_2, MutableMap.of("numberToDouble", 3));
        t.get();
        checkTags(t, app, DOUBLE_2, true);
    }

    public static final Effector<Integer> DOUBLE_CALL_ABSTRACT = Effectors.effector(Integer.class, "double_call")
        .description("doubles the given number")
        .parameter(Integer.class, "numberToDouble")
        .buildAbstract();
    public static final Effector<Integer> DOUBLE_CALL = Effectors.effector(DOUBLE_CALL_ABSTRACT)
        .impl(new EffectorBody<Integer>() {
            @Override
            public Integer call(ConfigBag parameters) {
                final Entity parent = entity();
                final Entity child = Iterables.getOnlyElement(entity().getChildren());
                
                final Effector<Integer> DOUBLE_CHECK_ABSTRACT = Effectors.effector(Integer.class, "double_check")
                    .description("doubles the given number and checks tags, assuming double exists as an effector here")
                    .parameter(Integer.class, "numberToDouble")
                    .buildAbstract();
                Effector<Integer> DOUBLE_CHECK = Effectors.effector(DOUBLE_CHECK_ABSTRACT)
                    .impl(new EffectorBody<Integer>() {
                        @Override
                        public Integer call(ConfigBag parameters) {
                            Assert.assertTrue(BrooklynTaskTags.isInEffectorTask(Tasks.current(), child, null, false));
                            Assert.assertTrue(BrooklynTaskTags.isInEffectorTask(Tasks.current(), child, DOUBLE_CHECK_ABSTRACT, false));
                            Assert.assertFalse(BrooklynTaskTags.isInEffectorTask(Tasks.current(), child, DOUBLE_1, false));
                            Assert.assertTrue(BrooklynTaskTags.isInEffectorTask(Tasks.current(), parent, null, true));
                            Assert.assertFalse(BrooklynTaskTags.isInEffectorTask(Tasks.current(), parent, null, false));
                            Assert.assertTrue(BrooklynTaskTags.isInEffectorTask(Tasks.current(), parent, DOUBLE_CALL_ABSTRACT, true));
                            Assert.assertFalse(BrooklynTaskTags.isInEffectorTask(Tasks.current(), parent, DOUBLE_1, true));
                            
                            return entity().invoke(DOUBLE_1, parameters.getAllConfig()).getUnchecked();
                        }
                    }).build();

                return child.invoke(DOUBLE_CHECK, parameters.getAllConfig()).getUnchecked();
            }
        }).build();


    @Test
    // also assert it works when the effector is defined on an entity
    public void testNestedEffectorTag() throws Exception {
        app.createAndManageChild(EntitySpec.create(Entity.class, DoublingEntity.class));
        Assert.assertEquals(app.invoke(DOUBLE_CALL, MutableMap.of("numberToDouble", 3)).get(), (Integer)6);
    }


    private void checkTags(Task<Integer> t, Entity entity, Effector<?> eff, boolean shouldHaveChild) {
        Assert.assertEquals(BrooklynTaskTags.getContextEntity(t), app);
        Assert.assertTrue(t.getTags().contains(BrooklynTaskTags.EFFECTOR_TAG), "missing effector tag; had: "+t.getTags());
        Assert.assertTrue(t.getDescription().contains(eff.getName()), "description missing effector name: "+t.getDescription());
        Assert.assertTrue(BrooklynTaskTags.isInEffectorTask(t, entity, eff, false));
        Assert.assertTrue(BrooklynTaskTags.isInEffectorTask(t, null, null, false));
        Assert.assertFalse(BrooklynTaskTags.isInEffectorTask(t, entity, Startable.START, false));
        
        if (shouldHaveChild) {
            Task<?> subtask = ((HasTaskChildren)t).getChildren().iterator().next();
            Assert.assertTrue(BrooklynTaskTags.isInEffectorTask(subtask, entity, eff, false));
            Assert.assertTrue(BrooklynTaskTags.isInEffectorTask(subtask, null, null, false));
        }
    }

    // TEST parameter task missing
    
    // ----------------- syntax for more complex -- an effector using subtasks
    
    public static Task<Integer> add(final int x, final int y) {
        return TaskBuilder.<Integer>builder().name("add").body(new Callable<Integer>() { public Integer call() { return x+y; } }).build();
    }

    public static Task<Integer> add(final Task<Integer> x, final int y) {
        return TaskBuilder.<Integer>builder().name("add").body(new Callable<Integer>() { public Integer call() { return DynamicTasks.get(x)+y; } }).build();
    }

    public static Task<Integer> addBasic(final Task<Integer> x, final int y) {
        return TaskBuilder.<Integer>builder().name("add (not dynamic)").dynamic(false).body(new Callable<Integer>() { public Integer call() {
            Preconditions.checkState(x.isSubmitted()); 
            return x.getUnchecked()+y; 
        } }).build();
    }

    public static Task<Integer> times(final int x, final int y) {
        return TaskBuilder.<Integer>builder().name("times").body(new Callable<Integer>() { public Integer call() { return x*y; } }).build();
    }

    public static Task<Integer> times(final Task<Integer> x, final int y) {
        return TaskBuilder.<Integer>builder().name("times").body(new Callable<Integer>() { public Integer call() { return DynamicTasks.get(x)*y; } }).build();
    }
    
    public static final Effector<Integer> TWO_X_PLUS_ONE = Effectors.effector(Integer.class, "twoXPlusOne")
            .description("doubles the given number and adds one")
            .parameter(Integer.class, "numberToStartWith")
            .impl(new EffectorBody<Integer>() {
                public Integer call(ConfigBag parameters) {
                    int input = (Integer)parameters.getStringKey("numberToStartWith");
                    queue( add(times(input, 2), 1) );
                    return last(Integer.class);
                }
            })
            .build();

    public static final Effector<Integer> TWO_X_PLUS_ONE_BASIC = Effectors.effector(Integer.class, "twoXPlusOne_Basic")
            .description("doubles the given number and adds one, as a basic task")
            .parameter(Integer.class, "numberToStartWith")
            .impl(new EffectorBody<Integer>() {
                public Integer call(ConfigBag parameters) {
                    int input = (Integer)parameters.getStringKey("numberToStartWith");
                    // note the subtasks must be queued explicitly with a basic task
                    // (but with the DynamicSequentialTask they can be resolved by the task itself; see above)
                    Task<Integer> product = queue(times(input, 2));
                    queue( addBasic(product, 1) );
                    return last(Integer.class);
                }
            })
            .build();

    // TODO a chaining style approach
    
    public static class Txp1Entity extends AbstractEntity {
        public static final Effector<Integer> TWO_X_P_1 = EffectorTaskTest.TWO_X_PLUS_ONE;
    }

    /** the composed effector should allow us to inspect its children */
    @Test
    public void testComposedEffector() throws Exception {
        Entity txp1 = app.createAndManageChild(EntitySpec.create(Entity.class, Txp1Entity.class));
        
        Task<Integer> e = txp1.invoke(TWO_X_PLUS_ONE, MutableMap.of("numberToStartWith", 3));
        Assert.assertTrue(e instanceof DynamicSequentialTask);
        Assert.assertEquals(e.get(), (Integer)7);
        Assert.assertEquals( Iterables.size( ((HasTaskChildren)e).getChildren() ), 1);
        Task<?> child = ((HasTaskChildren)e).getChildren().iterator().next();
        Assert.assertEquals( Iterables.size( ((HasTaskChildren)child).getChildren() ), 1);
    }

    /** the composed effector should allow us to inspect its children */
    @Test
    public void testComposedEffectorBasic() throws Exception {
        Entity txp1 = app.createAndManageChild(EntitySpec.create(Entity.class, Txp1Entity.class));
        
        Task<Integer> e = txp1.invoke(TWO_X_PLUS_ONE_BASIC, MutableMap.of("numberToStartWith", 3));
        Assert.assertTrue(e instanceof DynamicSequentialTask);
        Assert.assertEquals(e.get(), (Integer)7);
        Assert.assertEquals( Iterables.size( ((HasTaskChildren)e).getChildren() ), 2);
    }

    // --------- defining 
    
    @Test
    public void testEffectorWithBodyWorksEvenIfNotOnEntity() throws Exception {
        Entity doubler = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        
        Assert.assertEquals(doubler.invoke(DOUBLE_1, MutableMap.of("numberToDouble", 3)).get(), (Integer)6);
    }

    public static final Effector<Integer> DOUBLE_BODYLESS = Effectors.effector(Integer.class, "double")
            .description("doubles the given number")
            .parameter(Integer.class, "numberToDouble")
            .buildAbstract();
    
    @Test
    public void testEffectorWithoutBodyFails() throws Exception {
        Entity doubler = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        
        boolean failed = false;
        try {
            doubler.invoke(DOUBLE_BODYLESS, MutableMap.of("numberToDouble", 3));
        } catch (Exception e) {
            failed = true;
        }
        if (!failed) Assert.fail("doubling should have failed because it had no body");
    }

    @Test
    public void testEffectorBodyAdded() throws Exception {
        EntityInternal doubler = (EntityInternal) app.createAndManageChild(EntitySpec.create(TestEntity.class));
        
        // not yet present
        Assert.assertNull( doubler.getEffector("double") );
        
        // add it
        doubler.getMutableEntityType().addEffector(DOUBLE_BODYLESS, new EffectorBody<Integer>() {
            @Override
            public Integer call(ConfigBag parameters) {
                int input = (Integer)parameters.getStringKey("numberToDouble");
                return queue(times(input, 2)).getUnchecked();            
            }
        });
        // now it is present
        Assert.assertNotNull( doubler.getEffector("double") );
        
        Assert.assertEquals(doubler.invoke(DOUBLE_BODYLESS, MutableMap.of("numberToDouble", 3)).get(), (Integer)6);
    }

    @Test
    public void testEffectorBodyAddedImplicitlyButBodylessSignatureInvoked() throws Exception {
        EntityInternal doubler = (EntityInternal) app.createAndManageChild(EntitySpec.create(TestEntity.class));
        
        // add it
        doubler.getMutableEntityType().addEffector(DOUBLE_1);

        // invoke it, but using something with equivalent name (and signature -- though only name is used currently)
        // ensures that the call picks up the body by looking in the actual entity
        Assert.assertEquals(doubler.invoke(DOUBLE_BODYLESS, MutableMap.of("numberToDouble", 3)).get(), (Integer)6);
    }
 
    @Test(dependsOnMethods={"testEffectorBodyAdded"})
    public void testEntityNotPermanentlyChanged() throws Exception {
        EntityInternal doubler = (EntityInternal) app.createAndManageChild(EntitySpec.create(TestEntity.class));
        // ensures that independent creations of the class previously modified do not have this effector 
        Assert.assertNull( doubler.getEffector("double") );
   }
    
    // --- overriding by using statics ---------

    public static class BadDoublingEntity extends DoublingEntity {
        public static final Effector<Integer> DOUBLE = Effectors.effector(DoublingEntity.DOUBLE).
                impl( ((EffectorWithBody<Integer>)TWO_X_PLUS_ONE).getBody() ).build();
    }

    @Test
    // also assert it works when the entity is defined on an entity
    public void testOverriddenEffectorOnEntity() throws Exception {
        Entity doubler = app.createAndManageChild(EntitySpec.create(Entity.class, BadDoublingEntity.class));
        
        Assert.assertEquals(doubler.invoke(DoublingEntity.DOUBLE, MutableMap.of("numberToDouble", 3, "numberToStartWith", 3)).get(), (Integer)7);
    }
}
