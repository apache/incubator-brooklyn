package brooklyn.web.console

import grails.test.*
import brooklyn.entity.Application
import brooklyn.entity.Entity
import brooklyn.management.ExecutionManager
import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.basic.AbstractGroup
import brooklyn.entity.basic.AbstractEntity
import brooklyn.management.ManagementContext

class EntityServiceTests extends GrailsUnitTestCase {

    def testService

    Entity testEntity
    Collection<Entity> testEntities = new ArrayList<Application>()
    Collection<Application> testCollection = new ArrayList<Application>()

    protected void setUp() {
        testService = new EntityService()
        testService.managementContextService = new TestManagementContext()
        testCollection.add(new TestApplication())

        testEntity = testCollection.get(0).testEntity()
        testEntities = Collections.singletonList(testEntity);
        super.setUp()
    }

    protected void tearDown() {
        super.tearDown()
    }

    void testGetTopLevelEntities() {
        assertEquals(1, testService.getTopLevelEntities().size())
        assertEquals(testCollection.get(0).getDisplayName(), testService.getTopLevelEntities()[0].getDisplayName())
    }

    void testGetEntitiesMatchingCriteria() {
        assertEquals(4, testService.getEntitiesMatchingCriteria("tomcat", null, null).size())
        assertEquals(5, testService.getEntitiesMatchingCriteria(null, null, null).size())
        assertEquals(0, testService.getEntitiesMatchingCriteria(null, "testString", null).size())
    }

    void testFlattenEntities() {
        assertEquals(5, testService.flattenEntities(testCollection.get(0).entities).size())
    }

    void testGetChildren() {
        assertEquals(2, testService.getChildren(testEntity).size())
    }

    void testIsChildOf() {
        assertTrue(testService.isChildOf(testEntity.ownedChildren.asList().get(0), testEntities))
    }

    private class TestManagementContext implements ManagementContext{
        Collection<Application> getApplications() {
            return Collections.singletonList(new TestApplication());
        }

        Entity getEntity(String id) {
            throw new UnsupportedOperationException();
        }

        public ExecutionManager getExecutionManager() {
            throw new UnsupportedOperationException();
        }
    }

    private class TestApplication extends AbstractApplication {
        TestApplication() {
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
}
