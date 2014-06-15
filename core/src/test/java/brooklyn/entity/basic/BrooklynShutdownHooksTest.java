package brooklyn.entity.basic;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.BrooklynShutdownHooks.BrooklynShutdownHookJob;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.management.ManagementContext;
import brooklyn.test.entity.BlockingEntity;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.time.Duration;

public class BrooklynShutdownHooksTest {

    private ManagementContext managementContext;
    private TestApplication app;
    private TestEntity entity;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() {
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        entity = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        managementContext = app.getManagementContext();
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() {
        if (app != null) Entities.destroyAll(app.getManagementContext());
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
        BrooklynShutdownHooks.invokeTerminateOnShutdown(managementContext);
        BrooklynShutdownHooks.BrooklynShutdownHookJob job = BrooklynShutdownHookJob.newInstanceForTesting();
        job.run();
        
        assertFalse(managementContext.isRunning());
    }

    // Should first stop entities, then terminate management contexts
    @Test
    public void testInvokeStopEntityAndTerminateManagementContextOnShutdown() throws Exception {
        BrooklynShutdownHooks.invokeTerminateOnShutdown(managementContext);
        BrooklynShutdownHooks.invokeStopOnShutdown(entity);
        BrooklynShutdownHooks.BrooklynShutdownHookJob job = BrooklynShutdownHookJob.newInstanceForTesting();
        job.run();
        
        assertTrue(entity.getCallHistory().contains("stop"));
        assertFalse(managementContext.isRunning());
    }
}
