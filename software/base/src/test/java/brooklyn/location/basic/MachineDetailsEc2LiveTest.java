package brooklyn.location.basic;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;

import brooklyn.entity.AbstractEc2LiveTest;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.EmptySoftwareProcess;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.trait.Startable;
import brooklyn.location.Location;
import brooklyn.location.MachineDetails;
import brooklyn.location.OsDetails;
import brooklyn.test.EntityTestUtils;
import brooklyn.util.collections.MutableMap;

// This test really belongs in brooklyn-location but depends on AbstractEc2LiveTest in brooklyn-software-base
public class MachineDetailsEc2LiveTest extends AbstractEc2LiveTest {

    private static final Logger LOG = LoggerFactory.getLogger(MachineDetailsEc2LiveTest.class);
    private static final int TIMEOUT_MS = 1000 * 60 * 10; // ten minutes

    @Override
    protected void doTest(Location loc) throws Exception {
        Entity testEntity = app.createAndManageChild(EntitySpec.create(EmptySoftwareProcess.class));
        app.start(ImmutableList.of(loc));
        EntityTestUtils.assertAttributeEqualsEventually(MutableMap.of("timeout", TIMEOUT_MS),
                testEntity, Startable.SERVICE_UP, true);

        SshMachineLocation sshLoc = Locations.findUniqueSshMachineLocation(testEntity.getLocations()).get();
        MachineDetails machine = app.getExecutionContext()
                .submit(BasicMachineDetails.taskForSshMachineLocation(sshLoc))
                .getUnchecked();
        LOG.info("Found the following at {}: {}", loc, machine);
        assertNotNull(machine);
        OsDetails details = machine.getOsDetails();
        assertNotNull(details);
        assertNotNull(details.getArch());
        assertNotNull(details.getName());
        assertNotNull(details.getVersion());
        assertFalse(details.getArch().startsWith("architecture:"));
        assertFalse(details.getName().startsWith("name:"));
        assertFalse(details.getVersion().startsWith("version:"));
    }
}
