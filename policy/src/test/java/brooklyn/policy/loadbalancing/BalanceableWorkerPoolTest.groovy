package brooklyn.policy.loadbalancing

import static brooklyn.test.TestUtils.*
import static org.testng.Assert.*

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.Entity
import brooklyn.entity.Group
import brooklyn.entity.basic.AbstractGroup
import brooklyn.entity.basic.DynamicGroup
import brooklyn.entity.basic.Entities
import brooklyn.entity.trait.Resizable
import brooklyn.location.basic.SimulatedLocation
import brooklyn.test.entity.TestApplication

class BalanceableWorkerPoolTest {

    protected static final Logger LOG = LoggerFactory.getLogger(AbstractLoadBalancingPolicyTest.class)
    
    protected static final long TIMEOUT_MS = 10*1000;
    protected static final long SHORT_WAIT_MS = 250;
    
    protected static final long CONTAINER_STARTUP_DELAY_MS = 100
    
    protected TestApplication app
    protected SimulatedLocation loc
    protected BalanceableWorkerPool pool
    protected Group containerGroup
    protected Group itemGroup
    
    @BeforeMethod(alwaysRun=true)
    public void before() {
        loc = new SimulatedLocation(name:"loc")
        
        app = new TestApplication()
        containerGroup = new DynamicGroup([name:"containerGroup"], app, { e -> (e instanceof MockContainerEntity) })
        itemGroup = new DynamicGroup([name:"itemGroup"], app, { e -> (e instanceof MockItemEntity) })
        pool = new BalanceableWorkerPool([:], app)
        pool.setContents(containerGroup, itemGroup)
        app.start([loc])
    }
    
    @AfterMethod(alwaysRun=true)
    public void after() {
        if (app != null) Entities.destroy(app);
    }
    
    @Test(expectedExceptions=UnsupportedOperationException.class)
    public void testDefaultResizeFailsIfContainerGroupNotResizable() {
        try {
            pool.resize(1)
        } catch (Exception e) {
            throw unwrapThrowable(e)
        }
    }
    
    @Test
    public void testDefaultResizeCallsResizeOnContainerGroup() {
        LocallyResizableGroup resizable = new LocallyResizableGroup(parent:app)
        app.managementContext.manage(resizable)
        
        BalanceableWorkerPool pool2 = new BalanceableWorkerPool([:], app)
        pool2.setContents(resizable, itemGroup)
        app.managementContext.manage(pool2)
        
        pool2.resize(123)
        assertEquals(resizable.currentSize, 123)
    }
    
    @Test
    public void testCustomResizableCalledWhenResizing() {
        LocallyResizableGroup resizable = new LocallyResizableGroup(parent:app)
        app.managementContext.manage(resizable)
        
        pool.setResizable(resizable)
        
        pool.resize(123)
        assertEquals(resizable.currentSize, 123)
    }
    
    public static class LocallyResizableGroup extends AbstractGroup implements Resizable {
        private int size = 0
        public LocallyResizableGroup(Map props, Entity parent=null) {
            super(props, parent)
        }
        Integer resize(Integer newSize) {
            size = newSize
        }
        Integer getCurrentSize() {
            return size
        }
    }
}
