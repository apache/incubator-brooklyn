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
    private static final int NUM_CONTAINERS = 20
    private static final int WORKRATE_UPDATE_PERIOD_MS = 1000
    
    private ScheduledExecutorService scheduledExecutor;

    @BeforeMethod(alwaysRun=true)
    public void before() {
        scheduledExecutor = Executors.newScheduledThreadPool(10)
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
        
        for (int i = 0; i < NUM_CONTAINERS; i++) {
            containers.add(newContainer(app, "container"+i, 10, 30))
        }
        for (int i = 0; i < NUM_CONTAINERS; i++) {
            newItemWithPeriodicWorkrates(app, containers.get(0), "item"+i, 20)
        }

        assertWorkratesEventually(containers, Collections.nCopies(NUM_CONTAINERS, 20d), WORKRATE_JITTER)
    }
    
    @Test
    public void testConcurrentlyAddContainers() {
        List<MockItemEntity> items = []
        List<MockContainerEntity> containers = []
        
        containers.add(newContainer(app, "container-orig", 10, 30))
        
        for (int i = 0; i < NUM_CONTAINERS; i++) {
            newItemWithPeriodicWorkrates(app, containers.get(0), "item"+i, 20)
        }
        for (int i = 0; i < NUM_CONTAINERS-1; i++) {
            scheduledExecutor.submit( {containers.add(newContainer(app, "container"+i, 10, 30))} )
        }

        assertWorkratesEventually(containers, Collections.nCopies(NUM_CONTAINERS, 20d), WORKRATE_JITTER)
    }
    
    @Test
    public void testConcurrentlyAddItems() {
        List<MockItemEntity> items = []
        List<MockContainerEntity> containers = []
        
        for (int i = 0; i < NUM_CONTAINERS; i++) {
            containers.add(newContainer(app, "container"+i, 10, 30))
        }
        for (int i = 0; i < NUM_CONTAINERS; i++) {
            scheduledExecutor.submit( {items.add(newItemWithPeriodicWorkrates(app, containers.get(0), "item"+i, 20))} )
        }
        assertWorkratesEventually(containers, Collections.nCopies(NUM_CONTAINERS, 20d), WORKRATE_JITTER)
    }
    
    @Test(invocationCount=100)
    public void testConcurrentlyRemoveContainers() {
        List<MockItemEntity> items = []
        List<MockContainerEntity> containers = []
        
        for (int i = 0; i < NUM_CONTAINERS; i++) {
            containers.add(newContainer(app, "container"+i, 15, 45))
        }
        for (int i = 0; i < NUM_CONTAINERS; i++) {
            items.add(newItemWithPeriodicWorkrates(app, containers.get(i), "item"+i, 20))
        }
        
        for (int i = 0; i < NUM_CONTAINERS/2; i++) {
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
        
        assertWorkratesEventually(containers, Collections.nCopies((int)(NUM_CONTAINERS/2), 40d), WORKRATE_JITTER*2)
    }
    
    @Test
    public void testConcurrentlyRemoveItems() {
        List<MockItemEntity> items = []
        List<MockContainerEntity> containers = []
        
        for (int i = 0; i < NUM_CONTAINERS; i++) {
            containers.add(newContainer(app, "container"+i, 15, 45))
        }
        for (int i = 0; i < NUM_CONTAINERS*2; i++) {
            items.add(newItemWithPeriodicWorkrates(app, containers.get(i%NUM_CONTAINERS), "item"+i, 20))
        }
        // should now have item0 and item{0+NUM_CONTAINERS} on container0, etc
        
        for (int i = 0; i < NUM_CONTAINERS; i++) {
            // not removing consecutive items as that would leave it balanced!
            int indexToStop = (i < NUM_CONTAINERS/2) ? NUM_CONTAINERS : 0 
            MockItemEntity itemToStop = items.remove(indexToStop)
            scheduledExecutor.submit( {
                    try {
                        itemToStop.stop()
                        app.managementContext.unmanage(itemToStop)
                    } catch (Throwable t) {
                        LOG.error("Error stopping item $itemToStop", t);
                    }
                } )
        }
        
        assertWorkratesEventually(containers, Collections.nCopies(NUM_CONTAINERS, 20d), WORKRATE_JITTER)
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
                0, WORKRATE_UPDATE_PERIOD_MS, TimeUnit.MILLISECONDS)
        futureRef.set(future)
    }
}
