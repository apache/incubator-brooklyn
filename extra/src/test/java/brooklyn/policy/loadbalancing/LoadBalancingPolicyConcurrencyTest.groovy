package brooklyn.policy.loadbalancing

import static brooklyn.test.TestUtils.*
import static java.util.concurrent.TimeUnit.*
import static org.testng.Assert.*

import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.Application

public class LoadBalancingPolicyConcurrencyTest extends AbstractLoadBalancingPolicyTest {
    
    private static final double WORKRATE_JITTER = 2d
    
    private ScheduledExecutorService scheduledExecutor;

    @BeforeMethod(alwaysRun=true)
    public void before() {
        scheduledExecutor = Executors.newSingleThreadScheduledExecutor()
        super.before();
    }
    
    @AfterMethod(alwaysRun=true)
    public void after() {
        if (scheduledExecutor != null) scheduledExecutor.shutdownNow()
        super.after()
    }
    
    @Test
    public void testSimplePeriodicWorkrateUpdates() {
        List<MockItemEntity> items = []
        List<MockContainerEntity> containers = []
        
        for (int i = 0; i < 2; i++) {
            containers.add(newContainer(app, "container"+i, 10, 30))
        }
        for (int i = 0; i < 4; i++) {
            newItemWithPeriodicWorkrates(app, containers.get(0), "item"+i, 10)
        }

        assertWorkratesEventually(containers, [20d, 20d], WORKRATE_JITTER)
    }
    
    @Test
    public void testConcurrentlyAddContainers() {
        List<MockItemEntity> items = []
        List<MockContainerEntity> containers = []
        
        containers.add(newContainer(app, "container-orig", 10, 30))
        
        for (int i = 0; i < 3; i++) {
            newItemWithPeriodicWorkrates(app, containers.get(0), "item"+i, 20)
        }
        for (int i = 0; i < 2; i++) {
            scheduledExecutor.submit( {containers.add(newContainer(app, "container"+i, 10, 30))} )
        }

        assertWorkratesEventually(containers, [20d, 20d, 20d], WORKRATE_JITTER)
    }
    
    @Test
    public void testConcurrentlyAddItems() {
        List<MockItemEntity> items = []
        List<MockContainerEntity> containers = []
        
        for (int i = 0; i < 3; i++) {
            containers.add(newContainer(app, "container"+i, 10, 30))
        }
        for (int i = 0; i < 3; i++) {
            scheduledExecutor.submit( {items.add(newItemWithPeriodicWorkrates(app, containers.get(0), "item"+i, 20))} )
        }
        assertWorkratesEventually(containers, [20d, 20d, 20d], WORKRATE_JITTER)
    }
    
    @Test
    public void testConcurrentlyRemoveContainers() {
        List<MockItemEntity> items = []
        List<MockContainerEntity> containers = []
        
        for (int i = 0; i < 4; i++) {
            containers.add(newContainer(app, "container"+i, 10, 20))
        }
        for (int i = 0; i < 4; i++) {
            items.add(newItemWithPeriodicWorkrates(app, containers.get(i), "item"+i, 10))
        }
        
        for (int i = 0; i < 2; i++) {
            MockContainerEntity containerToStop = containers.remove(0)
            scheduledExecutor.submit( {
                    try {
                        containerToStop.offloadAndStop(containers.last());
                        app.managementContext.unmanage(containerToStop)
                    } catch (Throwable t) {
                        LOG.error("Error stopping container $containerToStop", t);
                    }
                } )
        }
        
        assertWorkratesEventually(containers, [20d, 20d], WORKRATE_JITTER)
    }
    
    @Test
    public void testConcurrentlyRemoveItems() {
        List<MockItemEntity> items = []
        List<MockContainerEntity> containers = []
        
        for (int i = 0; i < 2; i++) {
            containers.add(newContainer(app, "container"+i, 10, 20))
        }
        for (int i = 0; i < 3; i++) {
            items.add(newItemWithPeriodicWorkrates(app, containers.get(i%2), "item"+i, 10))
        }
        // should now have item0 and item2 on container1, and item1 on container2
        
        MockItemEntity itemToStop = items.remove(1)
        scheduledExecutor.submit( {
                try {
                    itemToStop.stop()
                    app.managementContext.unmanage(itemToStop)
                } catch (Throwable t) {
                    LOG.error("Error stopping item $itemToStop", t);
                }
            } )
        
        assertWorkratesEventually(containers, [10d, 10d], WORKRATE_JITTER)
    }
    
    protected MockItemEntity newItemWithPeriodicWorkrates(Application app, MockContainerEntity container, String name, double workrate) {
        MockItemEntity item = newItem(app, container, name, workrate)
        scheduleItemWorkrateUpdates(item, workrate, WORKRATE_JITTER)
        return item
    }
    
    private void scheduleItemWorkrateUpdates(MockItemEntity item, double workrate, double jitter) {
        AtomicReference<Future<?>> futureRef = new AtomicReference<Future<?>>();
        Future<?> future = scheduledExecutor.scheduleAtFixedRate(
                {
                    if (item.isStopped() && futureRef.get() != null) {
                        futureRef.get().cancel()
                        return
                    }
                    double jitteredWorkrate = workrate + (random.nextDouble()*jitter*2 - jitter)
                    item.setAttribute(TEST_METRIC, Math.max(0, jitteredWorkrate))
                },
                0, 500, TimeUnit.MILLISECONDS)
        futureRef.set(future)
    }
}
