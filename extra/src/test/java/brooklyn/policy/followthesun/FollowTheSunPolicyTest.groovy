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
    
    private String loc1Name
    private String loc2Name
    
    @BeforeMethod(alwaysRun=true) 
    public void before() {
        super.before()
        loc1Name = loc1.getName()
        loc2Name = loc2.getName()
    }
    @Test
    public void testPolicyUpdatesModel() {
        MockContainerEntity containerA = newContainer(app, loc1, "A")
        MockItemEntity item1 = newItem(app, containerA, "1")
        MockItemEntity item2 = newItem(app, containerA, "2")
        item1.setAttribute(TEST_METRIC, ImmutableMap.of(item2, 11d))
        
        executeUntilSucceeds(timeout:TIMEOUT_MS) {
            assertEquals(model.getItems(), [item1, item2] as Set)
            assertEquals(model.getItemContainer(item1), containerA)
            assertEquals(model.getItemLocation(item1), loc1Name)
            assertEquals(model.getContainerLocation(containerA), loc1Name)
            assertEquals(model.getDirectSendsToItemByLocation(), [(item2):[(loc1Name):11d]])
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

        item2.setAttribute(TEST_METRIC, ImmutableMap.of(item1, 100d))
        
        assertItemDistributionEventually([(containerA):[], (containerB):[item1,item2]])
    }
    
    @Test
    public void testNoopIfDemandIsTiny() {
        // Set-up containers and items.
        MockContainerEntity containerA = newContainer(app, loc1, "A")
        MockContainerEntity containerB = newContainer(app, loc2, "B")
        MockItemEntity item1 = newItem(app, containerA, "1")
        MockItemEntity item2 = newItem(app, containerB, "2")

        item2.setAttribute(TEST_METRIC, ImmutableMap.of(item1, 0.1d))
        
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
        
        item2.setAttribute(TEST_METRIC, ImmutableMap.of(item1, 100d))
        item3.setAttribute(TEST_METRIC, ImmutableMap.of(item1, 100.1d))
        
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
        item2.setAttribute(TEST_METRIC, ImmutableMap.of(item1, 100d))
        
        Thread.sleep(SHORT_WAIT_MS)
        assertItemDistributionEventually([(containerA):[], (containerB):[item1,item2]])
    }
}
