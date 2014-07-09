package brooklyn.entity.basic;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.BrooklynAppUnitTestSupport;
import brooklyn.entity.basic.BrooklynShutdownHooks.BrooklynShutdownHookJob;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.test.entity.BlockingEntity;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.time.Duration;

public class BrooklynShutdownHooksTest extends BrooklynAppUnitTestSupport {

    private TestEntity entity;
    
    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        entity = app.createAndManageChild(EntitySpec.create(TestEntity.class));
    }
    
    @Test
    public void testInvokeStopEntityOnShutdown() throws Exception {
        BrooklynShutdownHooks.invokeStopOnShutdown(entity);
        BrooklynShutdownHooks.BrooklynShutdownHookJob job = BrooklynShutdownHookJob.newInstanceForTesting();
        job.run();
        
        assertTrue(entity.getCallHistory().contains("stop"));
    }
    
    @Test
    public void testInvokeStopEntityTimesOutOnShutdown() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        BlockingEntity blockingEntity = app.createAndManageChild(EntitySpec.create(BlockingEntity.class)
                .configure(BlockingEntity.SHUTDOWN_LATCH, latch));

        // It will timeout after shutdown-timeout
        BrooklynShutdownHooks.setShutdownTimeout(Duration.of(100, TimeUnit.MILLISECONDS));
        BrooklynShutdownHooks.invokeStopOnShutdown(blockingEntity);
        BrooklynShutdownHooks.BrooklynShutdownHookJob job = BrooklynShutdownHookJob.newInstanceForTesting();
        job.run();
        
        latch.countDown();
    }
    
    @Test
    public void testInvokeTerminateManagementContextOnShutdown() throws Exception {
        BrooklynShutdownHooks.invokeTerminateOnShutdown(mgmt);
        BrooklynShutdownHooks.BrooklynShutdownHookJob job = BrooklynShutdownHookJob.newInstanceForTesting();
        job.run();
        
        assertFalse(mgmt.isRunning());
    }

    // Should first stop entities, then terminate management contexts
    @Test
    public void testInvokeStopEntityAndTerminateManagementContextOnShutdown() throws Exception {
        BrooklynShutdownHooks.invokeTerminateOnShutdown(mgmt);
        BrooklynShutdownHooks.invokeStopOnShutdown(entity);
        BrooklynShutdownHooks.BrooklynShutdownHookJob job = BrooklynShutdownHookJob.newInstanceForTesting();
        job.run();
        
        assertTrue(entity.getCallHistory().contains("stop"));
        assertFalse(mgmt.isRunning());
    }
}
