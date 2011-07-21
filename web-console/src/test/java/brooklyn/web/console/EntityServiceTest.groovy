package brooklyn.web.console

import static org.testng.Assert.*

import java.util.Collection;

import brooklyn.entity.Application
import brooklyn.entity.Entity
import brooklyn.management.ExecutionContext
import brooklyn.management.ExecutionManager
import brooklyn.management.SubscriptionManager
import brooklyn.management.SubscriptionContext
import brooklyn.management.ManagementContext
import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.basic.AbstractGroup
import brooklyn.entity.basic.AbstractEntity

import org.testng.annotations.Test
import org.testng.annotations.BeforeTest

class EntityServiceTest {

    def testService

    Entity testEntity
    Collection<Entity> testEntities = new ArrayList<Application>()
    Collection<Application> testCollection = new ArrayList<Application>()

    @BeforeTest
    protected void setUp() {
        testService = new EntityService()
        testService.managementContextService = new TestManagementContext()
        testCollection.add(new TestApplication())

        testEntity = testCollection.get(0).testEntity()
        testEntities = Collections.singletonList(testEntity);
    }

    @Test
    public void testGetTopLevelEntities() {
        assertEquals(1, testService.getTopLevelEntities().size())
        assertEquals(testCollection.get(0).getDisplayName(), testService.getTopLevelEntities()[0].getDisplayName())
    }

    @Test
    public void testGetEntitiesMatchingCriteria() {
        assertEquals(4, testService.getEntitiesMatchingCriteria("tomcat", null, null).size())
        assertEquals(5, testService.getEntitiesMatchingCriteria(null, null, null).size())
        assertEquals(0, testService.getEntitiesMatchingCriteria(null, "testString", null).size())
    }

    @Test
    public void testFlattenEntities() {
        assertEquals(5, testService.flattenEntities(testCollection).size())
    }

    @Test
    public void testGetChildren() {
        assertEquals(2, testService.getChildren(testEntity).size())
    }

    @Test
    public void testIsChildOf() {
        assertTrue(testService.isChildOf(testEntity.ownedChildren.asList().get(0), testEntities))
    }
}

//TODO no need for this mock (or for other mock impls of ManagementContext?) -- just 
//use the real management context ... e.g. new TestApplication().getManagementContext() ... no?  (Alex's suggest'n)
private class TestManagementContext implements ManagementContext{
    Collection<Application> getApplications() {
        return Collections.singletonList(new TestApplication());
    }

    @Override
    public Collection<Entity> getEntities() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isManaged(Entity entity) {
        throw new UnsupportedOperationException();
    }

    Entity getEntity(String id) {
        throw new UnsupportedOperationException();
    }

    public ExecutionManager getExecutionManager() {
        throw new UnsupportedOperationException();
    }
    public SubscriptionManager getSubscriptionManager() {
        throw new UnsupportedOperationException();
    }

    public ExecutionContext getExecutionContext(Entity entity) {
        throw new UnsupportedOperationException();
    }

    public SubscriptionContext getSubscriptionContext(Entity entity) {
        throw new UnsupportedOperationException();
    }
}

private class TestApplication extends AbstractApplication {
    TestApplication(Map props=[:]) {
        super(props)
        displayName = "Application";
        addOwnedChildren([
            new TestGroupEntity("tomcat tier 1").addOwnedChildren([
                new TestGroupEntity("tomcat cluster 1a").addOwnedChildren([
                    new TestLeafEntity("tomcat node 1a.1"),
                    new TestLeafEntity("tomcat node 1a.2")
                ])
            ])
        ])
    }

    Entity testEntity(){
        new TestGroupEntity("tomcat cluster 1a").addOwnedChildren([
            new TestLeafEntity("tomcat node 1a.1"),
            new TestLeafEntity("tomcat node 1a.2")
        ])
    }

    AbstractGroup addOwnedChildren(Collection<Entity> children) {
        children.each { addOwnedChild(it) }
        return this
    }

    private class TestGroupEntity extends AbstractGroup {
        TestGroupEntity(String displayName) {
            this.displayName = displayName
        }

        TestGroupEntity addOwnedChildren(Collection<Entity> children) {
            children.each { addOwnedChild(it) }
            return this
        }
    }

    private class TestLeafEntity extends AbstractEntity {
        TestLeafEntity(String displayName) {
            this.displayName = displayName;
        }
    }
}
	
