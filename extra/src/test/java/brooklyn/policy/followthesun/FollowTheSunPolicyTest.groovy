package brooklyn.policy.followthesun

import static brooklyn.test.TestUtils.*
import static java.util.concurrent.TimeUnit.*
import static org.testng.Assert.*

import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.Entity
import brooklyn.event.AttributeSensor
import brooklyn.policy.loadbalancing.MockContainerEntity
import brooklyn.policy.loadbalancing.MockItemEntity
import brooklyn.test.entity.TestApplication

import com.google.common.collect.ImmutableMap

public class FollowTheSunPolicyTest extends AbstractFollowTheSunPolicyTest {
    
    @BeforeMethod(alwaysRun=true) 
    public void before() {
        super.before()
    }
    @Test
    public void testPolicyUpdatesModel() {
        MockContainerEntity containerA = newContainer(app, loc1, "A")
        MockItemEntity item1 = newItem(app, containerA, "1")
        MockItemEntity item2 = newItem(app, containerA, "2")
        item2.setAttribute(TEST_METRIC, ImmutableMap.of(item1, 11d))
        
        executeUntilSucceeds(timeout:TIMEOUT_MS) {
            assertEquals(model.getItems(), [item1, item2] as Set)
            assertEquals(model.getItemContainer(item1), containerA)
            assertEquals(model.getItemLocation(item1), loc1)
            assertEquals(model.getContainerLocation(containerA), loc1)
            assertEquals(model.getDirectSendsToItemByLocation(), [(item2):[(loc1):11d]])
        }
    }
    
    @Test
    public void testNoopBalancing() {
        // Set-up containers and items.
        MockContainerEntity containerA = newContainer(app, loc1, "A")
        MockContainerEntity containerB = newContainer(app, loc2, "B")
        MockItemEntity item1 = newItem(app, containerA, "1", Collections.emptyMap())
        MockItemEntity item2 = newItem(app, containerB, "2", Collections.emptyMap())
        
        Thread.sleep(SHORT_WAIT_MS)
        assertItemDistributionEventually([(containerA):[item1], (containerB):[item2]])
    }
    
    @Test
    public void testMovesItemToFollowDemand() {
        // Set-up containers and items.
        MockContainerEntity containerA = newContainer(app, loc1, "A")
        MockContainerEntity containerB = newContainer(app, loc2, "B")
        MockItemEntity item1 = newItem(app, containerA, "1")
        MockItemEntity item2 = newItem(app, containerB, "2")

        item1.setAttribute(TEST_METRIC, ImmutableMap.of(item2, 100d))
        
        assertItemDistributionEventually([(containerA):[], (containerB):[item1,item2]])
    }
    
    @Test
    public void testNoopIfDemandIsTiny() {
        // Set-up containers and items.
        MockContainerEntity containerA = newContainer(app, loc1, "A")
        MockContainerEntity containerB = newContainer(app, loc2, "B")
        MockItemEntity item1 = newItem(app, containerA, "1")
        MockItemEntity item2 = newItem(app, containerB, "2")

        item1.setAttribute(TEST_METRIC, ImmutableMap.of(item2, 0.1d))
        
        Thread.sleep(SHORT_WAIT_MS)
        assertItemDistributionEventually([(containerA):[item1], (containerB):[item2]])
    }
    
    @Test
    public void testNoopIfDemandIsSimilarToCurrentLocation() {
        // Set-up containers and items.
        MockContainerEntity containerA = newContainer(app, loc1, "A")
        MockContainerEntity containerB = newContainer(app, loc2, "B")
        MockItemEntity item1 = newItem(app, containerA, "1")
        MockItemEntity item2 = newItem(app, containerA, "2")
        MockItemEntity item3 = newItem(app, containerB, "3")
        
        item1.setAttribute(TEST_METRIC, ImmutableMap.of(item2, 100d, item3, 100.1d))
        
        Thread.sleep(SHORT_WAIT_MS)
        assertItemDistributionEventually([(containerA):[item1,item2], (containerB):[item3]])
    }
    
    @Test
    public void testMoveDecisionIgnoresDemandFromItself() {
        // Set-up containers and items.
        MockContainerEntity containerA = newContainer(app, loc1, "A")
        MockContainerEntity containerB = newContainer(app, loc2, "B")
        MockItemEntity item1 = newItem(app, containerA, "1")
        MockItemEntity item2 = newItem(app, containerB, "2")
        
        item1.setAttribute(TEST_METRIC, ImmutableMap.of(item1, 100d))
        item2.setAttribute(TEST_METRIC, ImmutableMap.of(item2, 100d))
        
        Thread.sleep(SHORT_WAIT_MS)
        assertItemDistributionEventually([(containerA):[item1], (containerB):[item2]])
    }
    
    @Test
    public void testItemRemovedCausesRecalculationOfOptimalLocation() {
        // Set-up containers and items.
        MockContainerEntity containerA = newContainer(app, loc1, "A")
        MockContainerEntity containerB = newContainer(app, loc2, "B")
        MockItemEntity item1 = newItem(app, containerA, "1")
        MockItemEntity item2 = newItem(app, containerA, "2")
        MockItemEntity item3 = newItem(app, containerB, "3")
        
        item1.setAttribute(TEST_METRIC, ImmutableMap.of(item2, 100d, item3, 1000d))
        
        assertItemDistributionEventually([(containerA):[item2], (containerB):[item1,item3]])
        
        item3.stop()
        app.getManagementContext().unmanage(item3)
        
        assertItemDistributionEventually([(containerA):[item1, item2], (containerB):[]])
    }
    
    @Test
    public void testItemMovedCausesRecalculationOfOptimalLocationForOtherItems() {
        // Set-up containers and items.
        MockContainerEntity containerA = newContainer(app, loc1, "A")
        MockContainerEntity containerB = newContainer(app, loc2, "B")
        MockItemEntity item1 = newItem(app, containerA, "1")
        MockItemEntity item2 = newItem(app, containerA, "2")
        MockItemEntity item3 = newItem(app, containerB, "3")
        
        item1.setAttribute(TEST_METRIC, ImmutableMap.of(item2, 100d, item3, 1000d))
        
        assertItemDistributionEventually([(containerA):[item2], (containerB):[item1,item3]])
        
        item3.move(containerA)
        
        assertItemDistributionEventually([(containerA):[item1, item2, item3], (containerB):[]])
    }
    
    @Test
    public void testImmovableItemIsNotMoved() {
        MockContainerEntity containerA = newContainer(app, loc1, "A")
        MockContainerEntity containerB = newContainer(app, loc2, "B")
        MockItemEntity item1 = newLockedItem(app, containerA, "1")
        MockItemEntity item2 = newItem(app, containerB, "2")
        
        item1.setAttribute(TEST_METRIC, ImmutableMap.of(item2, 100d))
        
        assertItemDistributionContinually([(containerA):[item1], (containerB):[item2]])
    }
    
    @Test
    public void testImmovableItemContributesTowardsLoad() {
        MockContainerEntity containerA = newContainer(app, loc1, "A")
        MockContainerEntity containerB = newContainer(app, loc2, "B")
        MockItemEntity item1 = newLockedItem(app, containerA, "1")
        MockItemEntity item2 = newItem(app, containerA, "2")
        
        item2.setAttribute(TEST_METRIC, ImmutableMap.of(item1, 100d))
        
        assertItemDistributionContinually([(containerA):[item1, item2], (containerB):[]])
    }
}
