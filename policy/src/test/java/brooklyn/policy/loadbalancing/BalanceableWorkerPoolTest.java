package brooklyn.policy.loadbalancing;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.fail;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.Group;
import brooklyn.entity.basic.AbstractGroup;
import brooklyn.entity.basic.AbstractGroupImpl;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.DynamicGroup;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.entity.trait.Resizable;
import brooklyn.location.basic.SimulatedLocation;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.exceptions.Exceptions;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;

public class BalanceableWorkerPoolTest {

    private static final Logger LOG = LoggerFactory.getLogger(BalanceableWorkerPoolTest.class);
    
    protected static final long TIMEOUT_MS = 10*1000;
    protected static final long SHORT_WAIT_MS = 250;
    
    protected static final long CONTAINER_STARTUP_DELAY_MS = 100;
    
    protected TestApplication app;
    protected SimulatedLocation loc;
    protected BalanceableWorkerPool pool;
    protected Group containerGroup;
    protected Group itemGroup;
    
    @BeforeMethod(alwaysRun=true)
    public void before() {
        loc = new SimulatedLocation(MutableMap.of("name", "loc"));
        
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        containerGroup = app.createAndManageChild(EntitySpec.create(DynamicGroup.class)
                .displayName("containerGroup")
                .configure(DynamicGroup.ENTITY_FILTER, Predicates.instanceOf(MockContainerEntity.class)));
        itemGroup = app.createAndManageChild(EntitySpec.create(DynamicGroup.class)
                .displayName("itemGroup")
                .configure(DynamicGroup.ENTITY_FILTER, Predicates.instanceOf(MockItemEntity.class)));
        pool = app.createAndManageChild(EntitySpec.create(BalanceableWorkerPool.class));
        pool.setContents(containerGroup, itemGroup);
        
        app.start(ImmutableList.of(loc));
    }
    
    @AfterMethod(alwaysRun=true)
    public void after() {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }
    
    @Test
    public void testDefaultResizeFailsIfContainerGroupNotResizable() throws Exception {
        try {
            pool.resize(1);
            fail();
        } catch (Exception e) {
            if (Exceptions.getFirstThrowableOfType(e, UnsupportedOperationException.class) == null) throw e;
        }
    }
    
    @Test
    public void testDefaultResizeCallsResizeOnContainerGroup() {
        LocallyResizableGroup resizable = app.createAndManageChild(EntitySpec.create(LocallyResizableGroup.class));
        
        BalanceableWorkerPool pool2 = app.createAndManageChild(EntitySpec.create(BalanceableWorkerPool.class));
        pool2.setContents(resizable, itemGroup);
        Entities.manage(pool2);
        
        pool2.resize(123);
        assertEquals(resizable.getCurrentSize(), (Integer) 123);
    }
    
    @Test
    public void testCustomResizableCalledWhenResizing() {
        LocallyResizableGroup resizable = app.createAndManageChild(EntitySpec.create(LocallyResizableGroup.class));
        
        pool.setResizable(resizable);
        
        pool.resize(123);
        assertEquals(resizable.getCurrentSize(), (Integer)123);
    }

    @ImplementedBy(LocallyResizableGroupImpl.class)
    public static interface LocallyResizableGroup extends AbstractGroup, Resizable {
    }
    
    public static class LocallyResizableGroupImpl extends AbstractGroupImpl implements LocallyResizableGroup {
        private int size = 0;

        @Override
        public Integer resize(Integer newSize) {
            size = newSize;
            return size;
        }
        @Override
        public Integer getCurrentSize() {
            return size;
        }
    }
}
