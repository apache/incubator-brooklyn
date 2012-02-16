package brooklyn.policy.loadbalancing;

import static brooklyn.test.TestUtils.*
import static org.testng.Assert.*

import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.Application
import brooklyn.entity.Entity
import brooklyn.entity.Group
import brooklyn.entity.basic.DynamicGroup
import brooklyn.location.basic.SimulatedLocation
import brooklyn.test.entity.TestApplication

public class ItemsInContainersGroupTest {

    private static final long TIMEOUT_MS = 5000
    
    private TestApplication app
    private SimulatedLocation loc
    private Group containerGroup
    private ItemsInContainersGroup itemGroup

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        app = new TestApplication()
        Closure containerGroupFilter = { Entity e -> 
                e instanceof MockContainerEntity && 
                e.getConfig(MockContainerEntity.MOCK_MEMBERSHIP) == "ingroup"
            }
        containerGroup = new DynamicGroup([name:"containerGroup"], app, containerGroupFilter)
        itemGroup = new ItemsInContainersGroup([:], app)
        itemGroup.setContainers(containerGroup)
        app.start([loc])
    }

    @Test
    public void testSimpleMembership() throws Exception {
        MockContainerEntity containerIn = newContainer(app, "A", "ingroup")
        MockItemEntity item1 = newItem(app, containerIn, "1")
        MockItemEntity item2 = newItem(app, containerIn, "2")
        
        assertItemsEventually(item1, item2)
    }

    @Test
    public void testItemsInOtherContainersIgnored() throws Exception {
        MockContainerEntity containerOut = newContainer(app, "A", "outgroup")
        MockItemEntity item1 = newItem(app, containerOut, "1")
        
        assertItemsEventually()
    }
    
    @Test
    public void testItemMovedInIsAdded() throws Exception {
        MockContainerEntity containerIn = newContainer(app, "A", "ingroup")
        MockContainerEntity containerOut = newContainer(app, "A", "outgroup")
        MockItemEntity item1 = newItem(app, containerOut, "1")
        item1.move(containerIn)
        
        assertItemsEventually(item1)
    }

    @Test
    public void testItemMovedOutIsRemoved() throws Exception {
        MockContainerEntity containerIn = newContainer(app, "A", "ingroup")
        MockContainerEntity containerOut = newContainer(app, "A", "outgroup")
        MockItemEntity item1 = newItem(app, containerIn, "1")
        assertItemsEventually(item1)
        
        item1.move(containerOut)
        assertItemsEventually()
    }

    @Test
    public void testItemUnmanagedIsRemoved() throws Exception {
        MockContainerEntity containerIn = newContainer(app, "A", "ingroup")
        MockItemEntity item1 = newItem(app, containerIn, "1")
        assertItemsEventually(item1)
        
        app.getManagementContext().unmanage(item1)
        assertItemsEventually()
    }

    // TODO How to test this? Will it be used?
    // Adding a new container then adding items to it is tested in many other methods.
    @Test(enabled=false)
    public void testContainerAddedWillAddItsItems() throws Exception {
    }

    @Test
    public void testContainerRemovedWillRemoveItsItems() throws Exception {
        MockContainerEntity containerA = newContainer(app, "A", "ingroup")
        MockItemEntity item1 = newItem(app, containerA, "1")
        assertItemsEventually(item1)
        
        app.getManagementContext().unmanage(containerA)
        assertItemsEventually()
    }

    private void assertItemsEventually(MockItemEntity... expected) {
        executeUntilSucceeds(timeout:TIMEOUT_MS) {
            assertEquals(itemGroup.getMembers() as Set, expected as Set)
        }
    }   
     
    private MockContainerEntity newContainer(Application app, String name, String membership) {
        MockContainerEntity container = new MockContainerEntity([displayName:name])
        container.setConfig(MockContainerEntity.MOCK_MEMBERSHIP, membership)
        container.setOwner(app)
        app.getManagementContext().manage(container)
        container.start([loc])
        return container
    }
    
    private static MockItemEntity newItem(Application app, MockContainerEntity container, String name) {
        MockItemEntity item = new MockItemEntity([displayName:name], app)
        app.getManagementContext().manage(item)
        item.move(container)
        return item
    }
}
