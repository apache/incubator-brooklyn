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

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import groovy.lang.Closure;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.config.ConfigKey;
import brooklyn.config.ConfigMap;
import brooklyn.config.ConfigPredicates;
import brooklyn.entity.BrooklynAppUnitTestSupport;
import brooklyn.entity.Entity;
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey.IntegerAttributeSensorAndConfigKey;
import brooklyn.management.ExecutionManager;
import brooklyn.management.Task;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.task.BasicTask;
import brooklyn.util.task.DeferredSupplier;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.MoreExecutors;

public class ConfigMapTest extends BrooklynAppUnitTestSupport {

    private static final int TIMEOUT_MS = 10*1000;

    private MySubEntity entity;
    private ExecutorService executor;
    private ExecutionManager executionManager;

    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        entity = new MySubEntity(app);
        Entities.manage(entity);
        executor = MoreExecutors.listeningDecorator(Executors.newCachedThreadPool());
        executionManager = mgmt.getExecutionManager();
    }

    @AfterMethod(alwaysRun=true)
    @Override
    public void tearDown() throws Exception {
        if (executor != null) executor.shutdownNow();
        super.tearDown();
    }

    @Test
    public void testGetConfigKeysReturnsFromSuperAndInterfacesAndSubClass() throws Exception {
        assertEquals(entity.getEntityType().getConfigKeys(), ImmutableSet.of(
                MySubEntity.SUPER_KEY_1, MySubEntity.SUPER_KEY_2, MySubEntity.SUB_KEY_2, MySubEntity.INTERFACE_KEY_1));
    }

    @Test
    public void testConfigKeyDefaultUsesValueInSubClass() throws Exception {
        assertEquals(entity.getConfig(MyBaseEntity.SUPER_KEY_1), "overridden superKey1 default");
    }

    @Test
    public void testConfigureFromKey() throws Exception {
        MySubEntity entity2 = new MySubEntity(MutableMap.of(MySubEntity.SUPER_KEY_1, "changed"), app);
        Entities.manage(entity2);
        assertEquals(entity2.getConfig(MySubEntity.SUPER_KEY_1), "changed");
    }

    @Test
    public void testConfigureFromSuperKey() throws Exception {
        MySubEntity entity2 = new MySubEntity(MutableMap.of(MyBaseEntity.SUPER_KEY_1, "changed"), app);
        Entities.manage(entity2);
        assertEquals(entity2.getConfig(MySubEntity.SUPER_KEY_1), "changed");
    }

    @Test
    public void testConfigSubMap() throws Exception {
        entity.setConfig(MyBaseEntity.SUPER_KEY_1, "s1");
        entity.setConfig(MySubEntity.SUB_KEY_2, "s2");
        ConfigMap sub = entity.getConfigMap().submap(ConfigPredicates.matchingGlob("sup*"));
        Assert.assertEquals(sub.getConfigRaw(MyBaseEntity.SUPER_KEY_1, true).get(), "s1");
        Assert.assertFalse(sub.getConfigRaw(MySubEntity.SUB_KEY_2, true).isPresent());
    }

    @Test(expectedExceptions=IllegalArgumentException.class)
    public void testFailFastOnInvalidConfigKeyCoercion() throws Exception {
        MyOtherEntity entity2 = new MyOtherEntity(app);
        ConfigKey<Integer> key = MyOtherEntity.INT_KEY;
        entity2.setConfig((ConfigKey)key, "thisisnotanint");
    }

    @Test
    public void testGetConfigOfPredicateTaskReturnsCoercedClosure() throws Exception {
        MyOtherEntity entity2 = new MyOtherEntity(app);
        entity2.setConfig(MyOtherEntity.PREDICATE_KEY, Predicates.notNull());
        Entities.manage(entity2);

        Predicate predicate = entity2.getConfig(MyOtherEntity.PREDICATE_KEY);
        assertTrue(predicate instanceof Predicate, "predicate="+predicate);
        assertTrue(predicate.apply(1));
        assertFalse(predicate.apply(null));
    }

    @Test
    public void testGetConfigWithDeferredSupplierReturnsSupplied() throws Exception {
        DeferredSupplier<Integer> supplier = new DeferredSupplier<Integer>() {
            volatile int next = 0;
            public Integer get() {
                return next++;
            }
        };

        MyOtherEntity entity2 = new MyOtherEntity(app);
        entity2.setConfig(MyOtherEntity.INT_KEY, supplier);
        Entities.manage(entity2);

        assertEquals(entity2.getConfig(MyOtherEntity.INT_KEY), Integer.valueOf(0));
        assertEquals(entity2.getConfig(MyOtherEntity.INT_KEY), Integer.valueOf(1));
    }

    @Test
    public void testGetConfigWithFutureWaitsForResult() throws Exception {
        LatchingCallable work = new LatchingCallable("abc");
        Future<String> future = executor.submit(work);

        final MyOtherEntity entity2 = new MyOtherEntity(app);
        entity2.setConfig((ConfigKey)MyOtherEntity.STRING_KEY, future);
        Entities.manage(entity2);

        Future<String> getConfigFuture = executor.submit(new Callable<String>() {
            public String call() {
                return entity2.getConfig(MyOtherEntity.STRING_KEY);
            }});

        assertTrue(work.latchCalled.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        assertFalse(getConfigFuture.isDone());

        work.latchContinued.countDown();
        assertEquals(getConfigFuture.get(TIMEOUT_MS, TimeUnit.MILLISECONDS), "abc");
    }

    @Test
    public void testGetConfigWithExecutedTaskWaitsForResult() throws Exception {
        LatchingCallable work = new LatchingCallable("abc");
        Task<String> task = executionManager.submit(work);

        final MyOtherEntity entity2 = new MyOtherEntity(app);
        entity2.setConfig(MyOtherEntity.STRING_KEY, task);
        Entities.manage(entity2);

        Future<String> getConfigFuture = executor.submit(new Callable<String>() {
            public String call() {
                return entity2.getConfig(MyOtherEntity.STRING_KEY);
            }});

        assertTrue(work.latchCalled.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        assertFalse(getConfigFuture.isDone());

        work.latchContinued.countDown();
        assertEquals(getConfigFuture.get(TIMEOUT_MS, TimeUnit.MILLISECONDS), "abc");
        assertEquals(work.callCount.get(), 1);
    }

    @Test
    public void testGetConfigWithUnexecutedTaskIsExecutedAndWaitsForResult() throws Exception {
        LatchingCallable work = new LatchingCallable("abc");
        Task<String> task = new BasicTask<String>(work);

        final MyOtherEntity entity2 = new MyOtherEntity(app);
        entity2.setConfig(MyOtherEntity.STRING_KEY, task);
        Entities.manage(entity2);

        Future<String> getConfigFuture = executor.submit(new Callable<String>() {
            public String call() {
                return entity2.getConfig(MyOtherEntity.STRING_KEY);
            }});

        assertTrue(work.latchCalled.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        assertFalse(getConfigFuture.isDone());

        work.latchContinued.countDown();
        assertEquals(getConfigFuture.get(TIMEOUT_MS, TimeUnit.MILLISECONDS), "abc");
        assertEquals(work.callCount.get(), 1);
    }

    public static class MyBaseEntity extends AbstractEntity {
        public static final ConfigKey<String> SUPER_KEY_1 = ConfigKeys.newStringConfigKey("superKey1", "superKey1 key", "superKey1 default");
        public static final ConfigKey<String> SUPER_KEY_2 = ConfigKeys.newStringConfigKey("superKey2", "superKey2 key", "superKey2 default");
        
        public MyBaseEntity() {
        }
        public MyBaseEntity(Map flags) {
            super(flags);
        }
        public MyBaseEntity(Map flags, Entity parent) {
            super(flags, parent);
        }
        public MyBaseEntity(Entity parent) {
            super(parent);
        }
    }

    public static class MySubEntity extends MyBaseEntity implements MyInterface {
        public static final ConfigKey<String> SUPER_KEY_1 = ConfigKeys.newConfigKeyWithDefault(MyBaseEntity.SUPER_KEY_1, "overridden superKey1 default");
        public static final ConfigKey<String> SUB_KEY_2 = ConfigKeys.newStringConfigKey("subKey2", "subKey2 key", "subKey2 default");
        
        MySubEntity() {
        }
        MySubEntity(Map flags) {
            super(flags);
        }
        MySubEntity(Map flags, Entity parent) {
            super(flags, parent);
        }
        MySubEntity(Entity parent) {
            super(parent);
        }
    }

    public interface MyInterface {
        public static final ConfigKey<String> INTERFACE_KEY_1 = ConfigKeys.newStringConfigKey("interfaceKey1", "interface key 1", "interfaceKey1 default");
    }

    public static class MyOtherEntity extends AbstractEntity {
        public static final ConfigKey<Integer> INT_KEY = ConfigKeys.newIntegerConfigKey("intKey", "int key", 1);
        public static final ConfigKey<String> STRING_KEY = ConfigKeys.newStringConfigKey("stringKey", "string key", null);
        public static final ConfigKey<Object> OBJECT_KEY = ConfigKeys.newConfigKey(Object.class, "objectKey", "object key", null);
        public static final ConfigKey<Closure> CLOSURE_KEY = ConfigKeys.newConfigKey(Closure.class, "closureKey", "closure key", null);
        public static final ConfigKey<Future> FUTURE_KEY = ConfigKeys.newConfigKey(Future.class, "futureKey", "future key", null);
        public static final ConfigKey<Task> TASK_KEY = ConfigKeys.newConfigKey(Task.class, "taskKey", "task key", null);
        public static final ConfigKey<Predicate> PREDICATE_KEY = ConfigKeys.newConfigKey(Predicate.class, "predicateKey", "predicate key", null);
        public static final IntegerAttributeSensorAndConfigKey SENSOR_AND_CONFIG_KEY = new IntegerAttributeSensorAndConfigKey("sensorConfigKey", "sensor+config key", 1);
        
        public MyOtherEntity() {
        }
        public MyOtherEntity(Map flags) {
            super(flags);
        }
        public MyOtherEntity(Map flags, Entity parent) {
            super(flags, parent);
        }
        public MyOtherEntity(Entity parent) {
            super(parent);
        }
    }

    static class LatchingCallable<T> implements Callable<T> {
        final CountDownLatch latchCalled = new CountDownLatch(1);
        final CountDownLatch latchContinued = new CountDownLatch(1);
        final AtomicInteger callCount = new AtomicInteger(0);
        final T result;
        
        public LatchingCallable(T result) {
            this.result = result;
        }
        
        @Override
        public T call() throws Exception {
            callCount.incrementAndGet();
            latchCalled.countDown();
            latchContinued.await();
            return result;
        }
    }
}
