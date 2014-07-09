package brooklyn.policy.followthesun;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.Entities;
import brooklyn.location.Location;
import brooklyn.location.basic.SimulatedLocation;
import brooklyn.policy.loadbalancing.MockContainerEntity;
import brooklyn.policy.loadbalancing.MockContainerEntityImpl;
import brooklyn.policy.loadbalancing.MockItemEntity;
import brooklyn.policy.loadbalancing.MockItemEntityImpl;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

public class FollowTheSunModelTest {

    private Location loc1 = new SimulatedLocation(DefaultFollowTheSunModel.newHashMap("name","loc1"));
    private Location loc2 = new SimulatedLocation(DefaultFollowTheSunModel.newHashMap("name","loc2"));
    private MockContainerEntity container1 = new MockContainerEntityImpl();
    private MockContainerEntity container2 = new MockContainerEntityImpl();
    private MockItemEntity item1 = new MockItemEntityImpl();
    private MockItemEntity item2 = new MockItemEntityImpl();
    private MockItemEntity item3 = new MockItemEntityImpl();
    
    private DefaultFollowTheSunModel<MockContainerEntity, MockItemEntity> model;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        model = new DefaultFollowTheSunModel<MockContainerEntity, MockItemEntity>("myname");
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        // noting to tear down; no management context created
    }

    @Test
    public void testSimpleAddAndRemove() throws Exception {
        model.onContainerAdded(container1, loc1);
        model.onContainerAdded(container2, loc2);
        model.onItemAdded(item1, container1, true);
        model.onItemAdded(item2, container2, true);
        
        assertEquals(model.getContainerLocation(container1), loc1);
        assertEquals(model.getContainerLocation(container2), loc2);
        assertEquals(model.getItems(), ImmutableSet.of(item1, item2));
        assertEquals(model.getItemLocation(item1), loc1);
        assertEquals(model.getItemLocation(item2), loc2);
        assertEquals(model.getItemContainer(item1), container1);
        assertEquals(model.getItemContainer(item2), container2);
        
        model.onContainerRemoved(container2);
        model.onItemRemoved(item2);
        
        assertEquals(model.getContainerLocation(container1), loc1);
        assertEquals(model.getContainerLocation(container2), null);
        assertEquals(model.getItems(), ImmutableSet.of(item1));
        assertEquals(model.getItemLocation(item1), loc1);
        assertEquals(model.getItemLocation(item2), null);
        assertEquals(model.getItemContainer(item1), container1);
        assertEquals(model.getItemContainer(item2), null);
    }
    
    @Test
    public void testItemUsageMetrics() throws Exception {
        model.onContainerAdded(container1, loc1);
        model.onContainerAdded(container2, loc2);
        model.onItemAdded(item1, container1, true);
        model.onItemAdded(item2, container2, true);

        model.onItemUsageUpdated(item1, ImmutableMap.of(item2, 12d));
        model.onItemUsageUpdated(item2, ImmutableMap.of(item1, 11d));
        
        assertEquals(model.getDirectSendsToItemByLocation(),
                ImmutableMap.of(item1, ImmutableMap.of(loc2, 12d), item2, ImmutableMap.of(loc1, 11d)));
    }
    
    @Test
    public void testItemUsageReportedIfLocationSetAfterUsageUpdate() throws Exception {
        model.onContainerAdded(container1, null);
        model.onContainerAdded(container2, null);
        model.onItemAdded(item1, container1, true);
        model.onItemAdded(item2, container2, true);
        model.onItemUsageUpdated(item1, ImmutableMap.of(item2, 12d));
        model.onContainerLocationUpdated(container1, loc1);
        model.onContainerLocationUpdated(container2, loc2);
        
        assertEquals(model.getDirectSendsToItemByLocation(),
                ImmutableMap.of(item1, ImmutableMap.of(loc2, 12d)));
    }
    
    @Test
    public void testItemUsageMetricsSummedForActorsInSameLocation() throws Exception {
        model.onContainerAdded(container1, loc1);
        model.onContainerAdded(container2, loc2);
        model.onItemAdded(item1, container1, true);
        model.onItemAdded(item2, container2, true);
        model.onItemAdded(item3, container2, true);

        model.onItemUsageUpdated(item1, ImmutableMap.of(item2, 12d, item3, 13d));
        
        assertEquals(model.getDirectSendsToItemByLocation(),
                ImmutableMap.of(item1, ImmutableMap.of(loc2, 12d+13d)));
    }
    
    @Test
    public void testItemMovedWillUpdateLocationUsage() throws Exception {
        model.onContainerAdded(container1, loc1);
        model.onContainerAdded(container2, loc2);
        model.onItemAdded(item1, container1, false);
        model.onItemAdded(item2, container2, false);
        model.onItemUsageUpdated(item2, ImmutableMap.of(item1, 12d));
        
        model.onItemMoved(item1, container2);

        assertEquals(model.getDirectSendsToItemByLocation(),
                ImmutableMap.of(item2, ImmutableMap.of(loc2, 12d)));
        assertEquals(model.getItemContainer(item1), container2);
        assertEquals(model.getItemLocation(item1), loc2);
    }
    
    @Test
    public void testItemAddedWithNoContainer() throws Exception {
        model.onItemAdded(item1, null, true);

        assertEquals(model.getItems(), ImmutableSet.of(item1));
        assertEquals(model.getItemContainer(item1), null);
        assertEquals(model.getItemLocation(item1), null);
    }
    
    @Test
    public void testItemAddedBeforeContainer() throws Exception {
        model.onItemAdded(item1, container1, true);
        model.onContainerAdded(container1, loc1);

        assertEquals(model.getItems(), ImmutableSet.of(item1));
        assertEquals(model.getItemContainer(item1), container1);
        assertEquals(model.getItemLocation(item1), loc1);
    }
    
    @Test
    public void testItemMovedBeforeContainerAdded() throws Exception {
        model.onContainerAdded(container1, loc1);
        model.onItemAdded(item1, container1, true);
        model.onItemMoved(item1, container2);
        model.onContainerAdded(container2, loc2);

        assertEquals(model.getItems(), ImmutableSet.of(item1));
        assertEquals(model.getItemContainer(item1), container2);
        assertEquals(model.getItemLocation(item1), loc2);
    }
    
    @Test
    public void testItemAddedAnswersMovability() throws Exception {
        model.onItemAdded(item1, container1, false);
        model.onItemAdded(item2, container1, true);
        assertTrue(model.isItemMoveable(item1));
        assertFalse(model.isItemMoveable(item2));
    }
    
    @Test
    public void testWorkrateUpdateAfterItemRemovalIsNotRecorded() throws Exception {
        model.onContainerAdded(container1, loc1);
        model.onItemAdded(item1, container1, true);
        model.onItemAdded(item2, container1, true);
        model.onItemRemoved(item1);
        model.onItemUsageUpdated(item1, ImmutableMap.of(item2, 123d));
        
        assertFalse(model.getDirectSendsToItemByLocation().containsKey(item1));
    }
}
