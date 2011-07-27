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
import brooklyn.test.location.MockLocation
import brooklyn.location.Location;

import com.google.common.collect.Iterables

class EntityServiceTest {
    def testService

    Entity testEntity
    Collection<Entity> testEntities = new ArrayList<Application>()
    Collection<Application> testCollection = new ArrayList<Application>()
    Location testLocation = new MockLocation([latitude: 56, longitude: -2.5]);

    @BeforeTest
    protected void setUp() {
        testService = new EntityService()
        Application testApp = new TestApplication()

        testApp.start([ testLocation ])

        testService.managementContextService = testApp.managementContext
        testCollection.add(testApp)

        testEntity = testCollection.get(0).testEntity()
        testEntities = Collections.singletonList(testEntity);
    }

    @Test
    public void testGetTopLevelEntities() {
        assertEquals(testService.getTopLevelEntities().size(), 1)
        assertEquals(testCollection.get(0).getDisplayName(), Iterables.getFirst(testService.getTopLevelEntities(), null).getDisplayName())
    }

    @Test
    public void testGetEntitiesMatchingCriteria() {
        assertEquals(testService.getEntitiesMatchingCriteria("tomcat", null, null).size(), 4)
        assertEquals(testService.getEntitiesMatchingCriteria(null, null, null).size(), 5)
        assertEquals(testService.getEntitiesMatchingCriteria(null, "testString", null).size(), 0)
    }

    @Test
    public void testFlattenEntities() {
        assertEquals(testService.flattenEntities(testCollection).size(), 5)
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
        assertEquals(cs.size(), 1);
        assertEquals(cs[testLocation], 2);
    }

    @Test
    public void testGetNearestAncestorWithCoordinates() {
        assertEquals(testService.getNearestAncestorWithCoordinates(new MockLocation()), null);
        assertEquals(testLocation, testService.getNearestAncestorWithCoordinates(testLocation));

        Location p = new MockLocation(latitude: 23, longitude: 34);
        Location c = new MockLocation(parentLocation: p);
        assertEquals(p, testService.getNearestAncestorWithCoordinates(c));

        // Parent has only latitude set, should not use this.
        Location p2 = new MockLocation(latitude: 17);
        Location c2 = new MockLocation(parentLocation: p2);
        assertEquals(null, testService.getNearestAncestorWithCoordinates(c2));

        // Parent has only latitude set, should not use this, should next ancestor.
        Location p3 = new MockLocation(latitude: 17, parentLocation: p);
        Location c3 = new MockLocation(parentLocation: p3);
        assertEquals(testService.getNearestAncestorWithCoordinates(c3), p);
    }

    @Test
    public void testGetNearestAncestorWithCoordinates() {
        assertEquals(testService.getNearestAncestorWithCoordinates(new MockLocation()), null);
        assertEquals(testLocation, testService.getNearestAncestorWithCoordinates(testLocation));
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
