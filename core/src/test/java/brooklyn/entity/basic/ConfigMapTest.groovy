package brooklyn.entity.basic

import static org.testng.Assert.*

import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

import org.testng.Assert
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.config.ConfigMap
import brooklyn.config.ConfigPredicates
import brooklyn.entity.Entity
import brooklyn.event.basic.BasicConfigKey
import brooklyn.event.basic.BasicAttributeSensorAndConfigKey.IntegerAttributeSensorAndConfigKey
import brooklyn.management.ExecutionManager
import brooklyn.management.Task
import brooklyn.test.entity.TestApplication
import brooklyn.test.entity.TestApplicationImpl
import brooklyn.util.task.BasicTask
import brooklyn.util.task.DeferredSupplier

import com.google.common.base.Predicate
import com.google.common.collect.ImmutableSet
import com.google.common.util.concurrent.MoreExecutors

public class ConfigMapTest {

    private static final int TIMEOUT_MS = 10*1000;

    private TestApplication app
    private MySubEntity entity
    private ExecutorService executor;
    private ExecutionManager executionManager;

    @BeforeMethod(alwaysRun=true)
    public void setUp() {
        app = new TestApplicationImpl()
        entity = new MySubEntity(parent:app)
        Entities.startManagement(app);
        executor = MoreExecutors.listeningDecorator(Executors.newCachedThreadPool());
        executionManager = app.getManagementContext().getExecutionManager();
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        Entities.destroyAll(app.getManagementContext());
        if (executor != null) executor.shutdownNow();
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
        MySubEntity entity2 = new MySubEntity((MySubEntity.SUPER_KEY_1): "changed", app);
        assertEquals(entity2.getConfig(MySubEntity.SUPER_KEY_1), "changed")
    }

    @Test
    public void testConfigureFromSuperKey() throws Exception {
        MySubEntity entity2 = new MySubEntity((MyBaseEntity.SUPER_KEY_1): "changed", app);
        assertEquals(entity2.getConfig(MySubEntity.SUPER_KEY_1), "changed")
    }

    @Test
    public void testConfigSubMap() throws Exception {
        entity.configure(MyBaseEntity.SUPER_KEY_1, "s1");
        entity.configure(MySubEntity.SUB_KEY_2, "s2");
        ConfigMap sub = entity.getConfigMap().submap(ConfigPredicates.matchingGlob("sup*"));
        Assert.assertEquals(sub.getRawConfig(MyBaseEntity.SUPER_KEY_1), "s1");
        Assert.assertNull(sub.getRawConfig(MySubEntity.SUB_KEY_2));
    }

    @Test(expectedExceptions=IllegalArgumentException.class)
    public void testFailFastOnInvalidConfigKeyCoercion() throws Exception {
        MyOtherEntity entity2 = new MyOtherEntity(parent:app)
        BasicConfigKey key = MyOtherEntity.INT_KEY
        entity2.setConfig(key, "thisisnotanint");
    }

    @Test
    public void testGetConfigOfTypeClosureReturnsClosure() throws Exception {
        MyOtherEntity entity2 = new MyOtherEntity(app);
        entity2.setConfig(MyOtherEntity.CLOSURE_KEY, { return "abc" } );

        Closure configVal = entity2.getConfig(MyOtherEntity.CLOSURE_KEY);
        assertTrue(configVal instanceof Closure, "configVal="+configVal);
        assertEquals(configVal.call(), "abc");
    }

    @Test
    public void testGetConfigOfPredicateTaskReturnsCoercedClosure() throws Exception {
        MyOtherEntity entity2 = new MyOtherEntity(parent:app)
        entity2.setConfig(MyOtherEntity.PREDICATE_KEY, { return it != null } );
        Entities.manage(entity2);

        Predicate<?> predicate = entity2.getConfig(MyOtherEntity.PREDICATE_KEY);
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

        assertEquals(0, entity2.getConfig(MyOtherEntity.INT_KEY));
        assertEquals(1, entity2.getConfig(MyOtherEntity.INT_KEY));
    }

    @Test
    public void testGetConfigWithFutureWaitsForResult() throws Exception {
        LatchingCallable work = new LatchingCallable("abc");
        Future<String> future = executor.submit(work);

        MyOtherEntity entity2 = new MyOtherEntity(parent:app)
        entity2.setConfig(MyOtherEntity.STRING_KEY, future);
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

        MyOtherEntity entity2 = new MyOtherEntity(parent:app)
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

        MyOtherEntity entity2 = new MyOtherEntity(parent:app)
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
        MyBaseEntity() {
        }
        MyBaseEntity(Map flags) {
            super(flags)
        }
        MyBaseEntity(Map flags, Entity parent) {
            super(flags, parent)
        }
        MyBaseEntity(Entity parent) {
            super(parent)
        }
        public static final BasicConfigKey SUPER_KEY_1 = [ String, "superKey1", "superKey1 key", "superKey1 default"]
        public static final BasicConfigKey SUPER_KEY_2 = [ String, "superKey2", "superKey2 key", "superKey2 default"]
    }

   public static class MySubEntity extends MyBaseEntity implements MyInterface {
       MySubEntity() {
        }
       MySubEntity(Map flags) {
            super(flags)
        }
       MySubEntity(Map flags, Entity parent) {
            super(flags, parent)
        }
       MySubEntity(Entity parent) {
            super(parent)
        }
        public static final BasicConfigKey SUPER_KEY_1 = [ MyBaseEntity.SUPER_KEY_1, "overridden superKey1 default"]
        public static final BasicConfigKey SUB_KEY_2 = [ String, "subKey2", "subKey2 key", "subKey2 default"]
    }

    public interface MyInterface {
        public static final BasicConfigKey INTERFACE_KEY_1 = [ String, "interfaceKey1", "interface key 1", "interfaceKey1 default"]
    }

    public static class MyOtherEntity extends AbstractEntity {
        MyOtherEntity() {
        }
        MyOtherEntity(Map flags) {
            super(flags)
        }
        MyOtherEntity(Map flags, Entity parent) {
            super(flags, parent)
        }
        MyOtherEntity(Entity parent) {
            super(parent)
        }
        public static final BasicConfigKey<Integer> INT_KEY = [ Integer, "intKey", "int key", 1]
        public static final BasicConfigKey<String> STRING_KEY = [ String, "stringKey", "string key", null]
        public static final BasicConfigKey<Object> OBJECT_KEY = [ Object, "objectKey", "object key", null]
        public static final BasicConfigKey<Closure> CLOSURE_KEY = [ Closure, "closureKey", "closure key", null]
        public static final BasicConfigKey<Future> FUTURE_KEY = [ Future, "futureKey", "future key", null]
        public static final BasicConfigKey<Task> TASK_KEY = [ Task, "taskKey", "task key", null]
        public static final BasicConfigKey<Predicate> PREDICATE_KEY = [ Predicate, "predicateKey", "predicate key", null]
        public static final IntegerAttributeSensorAndConfigKey SENSOR_AND_CONFIG_KEY = [ "sensorConfigKey", "sensor+config key", 1]
    }

    static class LatchingCallable<T> implements Callable<T> {
        final CountDownLatch latchCalled = new CountDownLatch(1);
        final CountDownLatch latchContinued = new CountDownLatch(1);
        final AtomicInteger callCount = new AtomicInteger(0);
        final T result;
        
        public LatchingCallable(T result) {
            this.result = result;
        }
        
        public T call() throws Exception {
            callCount.incrementAndGet();
            latchCalled.countDown();
            latchContinued.await();
            return result;
        }
    }
}
