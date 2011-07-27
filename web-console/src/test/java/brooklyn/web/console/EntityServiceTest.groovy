package brooklyn.web.console

import static org.testng.Assert.*

import org.testng.annotations.BeforeTest
import org.testng.annotations.Test

import brooklyn.entity.Application
import brooklyn.entity.Entity
import brooklyn.entity.Group
import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.basic.AbstractEntity
import brooklyn.entity.basic.AbstractGroup
import brooklyn.test.entity.MockLocation
import brooklyn.location.Location;

import com.google.common.collect.Iterables

class EntityServiceTest {
    def testService

    Entity testEntity
    Collection<Entity> testEntities = new ArrayList<Application>()
    Collection<Application> testCollection = new ArrayList<Application>()

    @BeforeTest
    protected void setUp() {
        testService = new EntityService()
        Application testApp = new TestApplication()

        Location l = new MockLocation([latitude: 56, longitude: -2.5]);
        testApp.start([ l ])

        testService.managementContextService = testApp.managementContext
        testCollection.add(testApp)

        testEntity = testCollection.get(0).testEntity()
        testEntities = Collections.singletonList(testEntity);
    }

    @Test
    public void testGetTopLevelEntities() {
        assertEquals(1, testService.getTopLevelEntities().size())
        assertEquals(testCollection.get(0).getDisplayName(), Iterables.getFirst(testService.getTopLevelEntities(), null).getDisplayName())
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

    @Test
    public void testGetAllLeafEntities() {
        List<Entity> leaves = testService.getAllLeafEntities(testCollection);
        assertEquals(leaves.size(), 2)
    }

    @Test
    public void testEntityCountsAtLocatedLocations() {
        Map <Location, Integer> cs = testService.entityCountsAtLocatedLocations();
        assertEquals(0, cs.size());
    }
}

private class TestApplication extends AbstractApplication {
    TestApplication(Map props=[:]) {
        super(props)
        displayName = "Application";

        Group tomcatCluster = new TestGroupEntity("tomcat cluster 1a")
            .addOwnedChildren([new TestLeafEntity("tomcat node 1a.1"),
                               new TestLeafEntity("tomcat node 1a.2")]);

        addOwnedChildren([new TestGroupEntity("tomcat tier 1").addOwnedChildren([tomcatCluster])]);
    }

    Entity testEntity(){
        new TestGroupEntity("tomcat cluster 1a").addOwnedChildren([
            new TestLeafEntity("tomcat node 1a.1"),
            new TestLeafEntity("tomcat node 1a.2")
        ])
    }

    AbstractEntity addOwnedChildren(Collection<Entity> children) {
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
