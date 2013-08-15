package brooklyn.entity.basic;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertSame;
import static org.testng.Assert.assertTrue;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.Entity;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.location.Location;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation.LocalhostMachine;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

public class SameServerEntityTest {

    private LocalhostMachineProvisioningLocation loc;
    private TestApplication app;
    private SameServerEntity entity;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() {
        loc = new LocalhostMachineProvisioningLocation();
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        entity = app.createAndManageChild(EntitySpec.create(SameServerEntity.class));
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }
    
    @Test
    public void testUsesSameMachineLocationForEachChild() throws Exception {
        Entity child1 = entity.addChild(EntitySpec.create(TestEntity.class));
        Entity child2 = entity.addChild(EntitySpec.create(TestEntity.class));
        Entities.manage(child1);
        Entities.manage(child2);
        
        app.start(ImmutableList.of(loc));
        
        Location child1Loc = Iterables.getOnlyElement(child1.getLocations());
        Location child2Loc = Iterables.getOnlyElement(child2.getLocations());
        
        assertSame(child1Loc, child2Loc);
        assertTrue(child1Loc instanceof LocalhostMachine, "loc="+child1Loc);
        
        assertEquals(ImmutableSet.of(child1Loc), ImmutableSet.copyOf(loc.getInUse()));

        app.stop();
        
        assertEquals(ImmutableSet.of(), ImmutableSet.copyOf(loc.getInUse()));
    }
}
