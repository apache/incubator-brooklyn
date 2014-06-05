package brooklyn.entity.group;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.location.LocationSpec;
import brooklyn.location.basic.SimulatedLocation;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;

import com.google.common.collect.ImmutableList;


public class QuarantineGroupTest {

    private static final int TIMEOUT_MS = 2000;

    SimulatedLocation loc;
    TestApplication app;
    private TestEntity e1;
    private TestEntity e2;
    private QuarantineGroup group;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        app.getManagementContext().getLocationManager().createLocation(LocationSpec.create(SimulatedLocation.class));
        e1 = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        e2 = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        group = app.createAndManageChild(EntitySpec.create(QuarantineGroup.class));
    }

    @AfterMethod(alwaysRun = true)
    public void tearDown(){
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }

    @Test
    public void testExpungeMembersWhenNone() throws Exception {
        group.expungeMembers(true);
        group.expungeMembers(false);
    }
    
    @Test
    public void testExpungeMembersWithoutStop() throws Exception {
        group.addMember(e1);
        group.addMember(e2);
        group.expungeMembers(false);
        
        assertFalse(Entities.isManaged(e1));
        assertFalse(Entities.isManaged(e2));
        assertEquals(e1.getCallHistory(), ImmutableList.of());
        assertEquals(e2.getCallHistory(), ImmutableList.of());
    }

    @Test
    public void testExpungeMembersWithStop() throws Exception {
        group.addMember(e1);
        group.addMember(e2);
        group.expungeMembers(true);
        
        assertFalse(Entities.isManaged(e1));
        assertFalse(Entities.isManaged(e2));
        assertEquals(e1.getCallHistory(), ImmutableList.of("stop"));
        assertEquals(e2.getCallHistory(), ImmutableList.of("stop"));
    }
}
