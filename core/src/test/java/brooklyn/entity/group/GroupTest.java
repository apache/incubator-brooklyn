package brooklyn.entity.group;

import static org.testng.Assert.assertEquals;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.BasicGroup;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.location.LocationSpec;
import brooklyn.location.basic.SimulatedLocation;
import brooklyn.test.Asserts;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

public class GroupTest {

    private static final int TIMEOUT_MS = 2000;

    private TestApplication app;
    private BasicGroup group;
    private TestEntity entity1;
    private TestEntity entity2;
    
    SimulatedLocation loc;


    @BeforeMethod
    public void setUp() {
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        loc = app.getManagementContext().getLocationManager().createLocation(LocationSpec.create(SimulatedLocation.class));
        group = app.createAndManageChild(EntitySpec.create(BasicGroup.class));
        entity1 = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        entity2 = app.createAndManageChild(EntitySpec.create(TestEntity.class));
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown(){
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }

    @Test
    public void testAddRemoveMembers() throws Exception {
        group.addMember(entity1);
        assertGroupMembers(entity1);
        
        group.addMember(entity2);
        assertGroupMembers(entity1, entity2);
        
        group.removeMember(entity2);
        assertGroupMembers(entity1);
        
        group.removeMember(entity1);
        assertGroupMembers(new Entity[0]);
    }
    
    @Test
    public void testEntityGetGroups() throws Exception {
        group.addMember(entity1);
        Asserts.assertEqualsIgnoringOrder(entity1.getGroups(), ImmutableSet.of(group));
        
        group.removeMember(entity1);
        Asserts.assertEqualsIgnoringOrder(entity1.getGroups(), ImmutableSet.of());
   }
    
    @Test
    public void testUnmanagedMemberAutomaticallyRemoved() throws Exception {
        group.addMember(entity1);
        Entities.unmanage(entity1);
        assertGroupMembers(new Entity[0]);
    }
    
    @Test
    public void testUnmanagedGroupAutomaticallyRemovedMembers() throws Exception {
        group.addMember(entity1);
        Entities.unmanage(group);
        Asserts.assertEqualsIgnoringOrder(entity1.getGroups(), ImmutableSet.of());
    }
    
    @Test
    public void testAddingUnmanagedMemberDoesNotFailBadly() throws Exception {
        Entities.unmanage(entity1);
        group.addMember(entity1);
        Entities.unmanage(group);
    }
    
    @Test
    public void testAddingUnmanagedGroupDoesNotFailBadly() throws Exception {
        Entities.unmanage(group);
        entity1.addGroup(group);
        Entities.unmanage(entity1);
    }
    
    private void assertGroupMembers(Entity... expectedMembers) {
        Asserts.assertEqualsIgnoringOrder(group.getMembers(), ImmutableList.copyOf(expectedMembers));
        assertEquals(group.getAttribute(BasicGroup.GROUP_SIZE), (Integer)expectedMembers.length);
    }
}
