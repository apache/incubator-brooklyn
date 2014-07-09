package brooklyn.policy.loadbalancing;

import static org.testng.Assert.assertEquals;

import java.util.Collections;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.Entities;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

public class LoadBalancingModelTest {

    private static final double PRECISION = 0.00001;
    
    private MockContainerEntity container1 = new MockContainerEntityImpl();
    private MockContainerEntity container2 = new MockContainerEntityImpl();
    private MockItemEntity item1 = new MockItemEntityImpl();
    private MockItemEntity item2 = new MockItemEntityImpl();
    private MockItemEntity item3 = new MockItemEntityImpl();
    
    private DefaultBalanceablePoolModel<MockContainerEntity, MockItemEntity> model;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        model = new DefaultBalanceablePoolModel<MockContainerEntity, MockItemEntity>("myname");
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        // nothing to tear down; no management context created
    }

    @Test
    public void testPoolRatesCorrectlySumContainers() throws Exception {
        model.onContainerAdded(container1, 10d, 20d);
        model.onContainerAdded(container2, 11d, 22d);
        
        assertEquals(model.getPoolLowThreshold(), 10d+11d, PRECISION);
        assertEquals(model.getPoolHighThreshold(), 20d+22d, PRECISION);
    }
    
    @Test
    public void testPoolRatesCorrectlySumItems() throws Exception {
        model.onContainerAdded(container1, 10d, 20d);
        model.onItemAdded(item1, container1, true);
        model.onItemAdded(item2, container1, true);
        
        model.onItemWorkrateUpdated(item1, 1d);
        assertEquals(model.getCurrentPoolWorkrate(), 1d, PRECISION);
        
        model.onItemWorkrateUpdated(item2, 2d);
        assertEquals(model.getCurrentPoolWorkrate(), 1d+2d, PRECISION);
        
        model.onItemWorkrateUpdated(item2, 4d);
        assertEquals(model.getCurrentPoolWorkrate(), 1d+4d, PRECISION);
        
        model.onItemRemoved(item1);
        assertEquals(model.getCurrentPoolWorkrate(), 4d, PRECISION);
    }
    
    @Test
    public void testWorkrateUpdateAfterItemRemovalIsNotRecorded() throws Exception {
        model.onContainerAdded(container1, 10d, 20d);
        model.onItemAdded(item1, container1, true);
        model.onItemRemoved(item1);
        model.onItemWorkrateUpdated(item1, 123d);
        
        assertEquals(model.getCurrentPoolWorkrate(), 0d, PRECISION);
        assertEquals(model.getContainerWorkrates().get(container1), 0d, PRECISION);
        assertEquals(model.getItemWorkrate(item1), null);
    }
    
    @Test
    public void testItemMovedWillUpdateContainerWorkrates() throws Exception {
        model.onContainerAdded(container1, 10d, 20d);
        model.onContainerAdded(container2, 11d, 21d);
        model.onItemAdded(item1, container1, false);
        model.onItemWorkrateUpdated(item1, 123d);
        
        model.onItemMoved(item1, container2);
        
        assertEquals(model.getItemsForContainer(container1), Collections.emptySet());
        assertEquals(model.getItemsForContainer(container2), ImmutableSet.of(item1));
        assertEquals(model.getItemWorkrate(item1), 123d);
        assertEquals(model.getTotalWorkrate(container1), 0d);
        assertEquals(model.getTotalWorkrate(container2), 123d);
        assertEquals(model.getItemWorkrates(container1), Collections.emptyMap());
        assertEquals(model.getItemWorkrates(container2), ImmutableMap.of(item1, 123d));
        assertEquals(model.getContainerWorkrates(), ImmutableMap.of(container1, 0d, container2, 123d));
        assertEquals(model.getCurrentPoolWorkrate(), 123d);
    }
}
