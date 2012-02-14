package brooklyn.policy.followthesun;

import static org.testng.Assert.assertEquals;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.location.Location;
import brooklyn.location.basic.SimulatedLocation;
import brooklyn.policy.loadbalancing.MockContainerEntity;
import brooklyn.policy.loadbalancing.MockItemEntity;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

public class FollowTheSunModelTest {

    private Location loc1 = new SimulatedLocation();
    private Location loc2 = new SimulatedLocation();
    private MockContainerEntity container1 = new MockContainerEntity();
    private MockContainerEntity container2 = new MockContainerEntity();
    private MockItemEntity item1 = new MockItemEntity();
    private MockItemEntity item2 = new MockItemEntity();
    
    private DefaultFollowTheSunModel<MockContainerEntity, MockItemEntity> model;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        model = new DefaultFollowTheSunModel<MockContainerEntity, MockItemEntity>("myname");
    }
    
    @Test
    public void testSimpleAddAndRemove() throws Exception {
        model.onContainerAdded(container1, loc1);
        model.onContainerAdded(container2, loc2);
        model.onItemAdded(item1, container1);
        model.onItemAdded(item2, container2);
        
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
        model.onItemAdded(item1, container1);
        model.onItemAdded(item2, container2);

        model.onItemUsageUpdated(item1, ImmutableMap.of(item2, 11d));
        model.onItemUsageUpdated(item2, ImmutableMap.of(item1, 12d));
        
        assertEquals(model.getDirectSendsToItemByLocation(),
                ImmutableMap.of(item1, ImmutableMap.of(loc2, 12d), item2, ImmutableMap.of(loc1, 11d)));
    }
    
    @Test
    public void testItemAddedWithNoContainer() throws Exception {
        model.onItemAdded(item1, null);

        assertEquals(model.getItems(), ImmutableSet.of(item1));
        assertEquals(model.getItemContainer(item1), null);
        assertEquals(model.getItemLocation(item1), null);
    }
    
    @Test
    public void testItemAddedBeforeContainer() throws Exception {
        model.onItemAdded(item1, container1);
        model.onContainerAdded(container1, loc1);

        assertEquals(model.getItems(), ImmutableSet.of(item1));
        assertEquals(model.getItemContainer(item1), container1);
        assertEquals(model.getItemLocation(item1), loc1);
    }
    
    @Test
    public void testItemMovedBeforeContainerAdded() throws Exception {
        model.onContainerAdded(container1, loc1);
        model.onItemAdded(item1, container1);
        model.onItemMoved(item1, container2);
        model.onContainerAdded(container2, loc2);

        assertEquals(model.getItems(), ImmutableSet.of(item1));
        assertEquals(model.getItemContainer(item1), container2);
        assertEquals(model.getItemLocation(item1), loc2);
    }
}
