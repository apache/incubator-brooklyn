package brooklyn.web.console

import static org.testng.Assert.*

import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.Application
import brooklyn.entity.Effector
import brooklyn.entity.Entity
import brooklyn.entity.Group
import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.basic.AbstractEntity
import brooklyn.entity.basic.AbstractGroup
import brooklyn.entity.basic.EffectorInferredFromAnnotatedMethod
import brooklyn.entity.trait.Startable
import brooklyn.location.Location
import brooklyn.test.location.MockLocation
import brooklyn.web.console.entity.TaskSummary

import com.google.common.collect.Iterables

class EntityServiceTest {
    def testService

    Entity testEntity
    List<Entity> testEntities
    List<Application> testCollection
    Location testLocation = new MockLocation([latitude: 56, longitude: -2.5]);

    @BeforeMethod
    protected void setUp() {
        testEntities = []
        testCollection = []
        
        testService = new EntityService()
        Application testApp = new TestApplication(testLocation: testLocation)

        testApp.start([ testLocation ])

        testService.managementContextService = testApp.managementContext
        testCollection.add(testApp)

        testEntity = testCollection.get(0).testEntity()
        testEntities = Collections.singletonList(testEntity);
    }

    @Test
    public void testGetTopLevelEntities() {
        assertEquals(testService.getTopLevelEntities().size(), 1)
        assertEquals(testCollection.get(0).getDisplayName(),
                     Iterables.getFirst(testService.getTopLevelEntities(), null).getDisplayName())
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
    public void testGetTasksOfEntity() {
        // The group is not startable, so expect nothing initially. Same for the group's child
        TestApplication app = testCollection.get(0)
        TestGroupEntity tier = app.ownedChildren.iterator().next()
        TestGroupEntity cluster = tier.ownedChildren.iterator().next()

        println "app=${app.id}, tier=${tier.id}, cluster=${cluster.id}, " +
                "app.children=${app.ownedChildren}, tier.children=${tier.ownedChildren}"
                
        assertEquals(testService.getTasksOfEntity(tier.id), [], ""+testService.getTasksOfEntity(tier.id))
        
        tier.invoke(TestGroupEntity.MY_GROUP_EFFECTOR)
        List<TaskSummary> tasks = testService.getTasksOfEntity(tier.id)
        assertEquals(tasks.size(), 1, ""+tasks)
        assertExpectedTask(tasks.get(0), tier, TestGroupEntity.MY_GROUP_EFFECTOR)
        
        assertEquals(testService.getTasksOfEntity(cluster.id), [], ""+testService.getTasksOfEntity(cluster.id))
    }

    @Test
    public void testGetTasksOfAllEntities() {
        // The app will have had start called on it; the other things are not startable
        TestApplication app = testCollection.get(0)
        TestGroupEntity tier = app.ownedChildren.iterator().next()
        TestGroupEntity cluster = tier.ownedChildren.iterator().next()

        // Initial tasks has app.start()        
        List<TaskSummary> initialTasks = testService.getTasksOfAllEntities()
        assertEquals(initialTasks.size(), 1, ""+initialTasks)
        assertExpectedTask(initialTasks.get(0), app, Startable.START)
        
        tier.invoke(TestGroupEntity.MY_GROUP_EFFECTOR)
        List<TaskSummary> tasks = testService.getTasksOfAllEntities()
        assertEquals(tasks.size(), 2, ""+tasks)
        assertExpectedTask(tasks.get(0), tier, TestGroupEntity.MY_GROUP_EFFECTOR)
        assertExpectedTask(tasks.get(1), app, Startable.START)
    }
    
    private void assertExpectedTask(TaskSummary actual, Entity entity, Effector effector) {
        assertEquals(actual.entityDisplayName, entity.displayName, ""+actual)
        assertEquals(actual.entityId, entity.id, ""+actual)
        assertTrue(actual.description.contains(effector.getName()), ""+actual)
    }
}

class TestApplication extends AbstractApplication {
    public static final Effector<Void> MY_APP_EFFECTOR =
        new EffectorInferredFromAnnotatedMethod<Void>(TestApplication.class, "myAppEffector", "Do something");
    
    TestApplication(Map props=[:]) {
        super(props)
        displayName = "Application";
        Location testLocation = props.remove("testLocation");

        Group tomcatCluster = new TestGroupEntity("tomcat cluster 1a")
        .addOwnedChildren([new TestLeafEntity("tomcat node 1a.1", testLocation),
                           new TestLeafEntity("tomcat node 1a.2", testLocation)]);

        addOwnedChildren([new TestGroupEntity("tomcat tier 1").addOwnedChildren([tomcatCluster])]);
    }

    public void myAppEffector() {
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
}

class TestGroupEntity extends AbstractGroup {
    public static final Effector<Void> MY_GROUP_EFFECTOR =
        new EffectorInferredFromAnnotatedMethod<Void>(TestGroupEntity.class, "myGroupEffector", "Do something");
    
    TestGroupEntity(String displayName) {
        this.displayName = displayName
    }

    public void myGroupEffector() {
    }
    
    TestGroupEntity addOwnedChildren(Collection<Entity> children) {
        children.each { addOwnedChild(it) }
        return this
    }
}

class TestLeafEntity extends AbstractEntity {
    TestLeafEntity(String displayName) {
        this.displayName = displayName;
    }

    TestLeafEntity(String displayName, Location hackInLocation) {
        this.displayName = displayName;
        this.locations.add(hackInLocation);
    }
}
