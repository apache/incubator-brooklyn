package io.brooklyn.camp.brooklyn;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import java.io.StringReader;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import brooklyn.entity.Entity;
import brooklyn.location.Location;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

@Test
public class LocationsYamlTest extends AbstractYamlTest {
    private static final Logger log = LoggerFactory.getLogger(LocationsYamlTest.class);

    @Test
    public void testLocationString() throws Exception {
        String yaml = 
                "location: localhost\n"+
                "services:\n"+
                "- serviceType: brooklyn.test.entity.TestEntity\n";
        
        Entity app = createStartWaitAndLogApplication(new StringReader(yaml));
        LocalhostMachineProvisioningLocation loc = (LocalhostMachineProvisioningLocation) Iterables.getOnlyElement(app.getLocations());
        assertNotNull(loc);
    }

    @Test
    public void testLocationComplexString() throws Exception {
        String yaml = 
                "location: localhost:(name=myname)\n"+
                "services:\n"+
                "- serviceType: brooklyn.test.entity.TestEntity\n";
        
        Entity app = createStartWaitAndLogApplication(new StringReader(yaml));
        LocalhostMachineProvisioningLocation loc = (LocalhostMachineProvisioningLocation) Iterables.getOnlyElement(app.getLocations());
        assertEquals(loc.getDisplayName(), "myname");
    }

    @Test
    public void testLocationSplitLineWithNoConfig() throws Exception {
        String yaml = 
                "location:\n"+
                "  localhost\n"+
                "services:\n"+
                "- serviceType: brooklyn.test.entity.TestEntity\n";
        
        Entity app = createStartWaitAndLogApplication(new StringReader(yaml));
        LocalhostMachineProvisioningLocation loc = (LocalhostMachineProvisioningLocation) Iterables.getOnlyElement(app.getLocations());
        assertNotNull(loc);
    }

    @Test
    public void testMultiLocations() throws Exception {
        String yaml = 
                "locations:\n"+
                "- localhost:(name=loc1)\n"+
                "- localhost:(name=loc2)\n"+
                "services:\n"+
                "- serviceType: brooklyn.test.entity.TestEntity\n";
        
        Entity app = createStartWaitAndLogApplication(new StringReader(yaml));
        List<Location> locs = ImmutableList.copyOf(app.getLocations());
        assertEquals(locs.size(), 2, "locs="+locs);
        LocalhostMachineProvisioningLocation loc1 = (LocalhostMachineProvisioningLocation) locs.get(0);
        LocalhostMachineProvisioningLocation loc2 = (LocalhostMachineProvisioningLocation) locs.get(1);
        assertEquals(loc1.getDisplayName(), "loc1");
        assertEquals(loc2.getDisplayName(), "loc2");
    }

    @Test
    public void testLocationConfig() throws Exception {
        String yaml = 
                "location:\n"+
                "  localhost:\n"+
                "    displayName: myname\n"+
                "    myconfkey: myconfval\n"+
                "services:\n"+
                "- serviceType: brooklyn.test.entity.TestEntity\n";
        
        Entity app = createStartWaitAndLogApplication(new StringReader(yaml));
        LocalhostMachineProvisioningLocation loc = (LocalhostMachineProvisioningLocation) Iterables.getOnlyElement(app.getLocations());
        assertEquals(loc.getDisplayName(), "myname");
        assertEquals(loc.getAllConfig(false).get("myconfkey"), "myconfval");
    }

    @Test
    public void testMultiLocationConfig() throws Exception {
        String yaml = 
                "locations:\n"+
                "- localhost:\n"+
                "    displayName: myname1\n"+
                "    myconfkey: myconfval1\n"+
                "- localhost:\n"+
                "    displayName: myname2\n"+
                "    myconfkey: myconfval2\n"+
                "services:\n"+
                "- serviceType: brooklyn.test.entity.TestEntity\n";
        
        Entity app = createStartWaitAndLogApplication(new StringReader(yaml));
        List<Location> locs = ImmutableList.copyOf(app.getLocations());
        assertEquals(locs.size(), 2, "locs="+locs);
        LocalhostMachineProvisioningLocation loc1 = (LocalhostMachineProvisioningLocation) locs.get(0);
        LocalhostMachineProvisioningLocation loc2 = (LocalhostMachineProvisioningLocation) locs.get(1);
        assertEquals(loc1.getDisplayName(), "myname1");
        assertEquals(loc1.getAllConfig(false).get("myconfkey"), "myconfval1");
        assertEquals(loc2.getDisplayName(), "myname2");
        assertEquals(loc2.getAllConfig(false).get("myconfkey"), "myconfval2");
    }

    // TODO Fails because PlanInterpretationContext constructor throws NPE on location's value (using ImmutableMap).
    @Test(groups="WIP")
    public void testLocationBlank() throws Exception {
        String yaml = 
                "location: \n"+
                "services:\n"+
                "- serviceType: brooklyn.test.entity.TestEntity\n";
        
        Entity app = createStartWaitAndLogApplication(new StringReader(yaml));
        assertTrue(app.getLocations().isEmpty(), "locs="+app.getLocations());
    }

    @Test
    public void testInvalidLocationAndLocations() throws Exception {
        String yaml = 
                "location: localhost\n"+
                "locations:\n"+
                "- localhost\n"+
                "services:\n"+
                "- serviceType: brooklyn.test.entity.TestEntity\n";
        
        try {
            createStartWaitAndLogApplication(new StringReader(yaml));
        } catch (IllegalStateException e) {
            if (!e.toString().contains("Conflicting 'location' and 'locations'")) throw e;
        }
    }

    @Test
    public void testInvalidLocationList() throws Exception {
        // should have used "locations:" instead of "location:"
        String yaml = 
                "location:\n"+
                "- localhost\n"+
                "services:\n"+
                "- serviceType: brooklyn.test.entity.TestEntity\n";
        
        try {
            createStartWaitAndLogApplication(new StringReader(yaml));
        } catch (IllegalStateException e) {
            if (!e.toString().contains("must be a string or map")) throw e;
        }
    }
    
    @Test
    public void testRootLocationPassedToChild() throws Exception {
        String yaml = 
                "locations:\n"+
                "- localhost:(name=loc1)\n"+
                "services:\n"+
                "- serviceType: brooklyn.test.entity.TestEntity\n";
        
        Entity app = createStartWaitAndLogApplication(new StringReader(yaml));
        Entity child = Iterables.getOnlyElement(app.getChildren());
        LocalhostMachineProvisioningLocation loc = (LocalhostMachineProvisioningLocation) Iterables.getOnlyElement(child.getLocations());
        assertEquals(loc.getDisplayName(), "loc1");
    }

    @Override
    protected Logger getLogger() {
        return log;
    }
    
}
