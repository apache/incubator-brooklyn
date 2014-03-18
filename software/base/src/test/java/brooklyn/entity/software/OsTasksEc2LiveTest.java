package brooklyn.entity.software;

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
import brooklyn.location.OsDetails;
import brooklyn.test.EntityTestUtils;
import brooklyn.util.collections.MutableMap;

public class OsTasksEc2LiveTest extends AbstractEc2LiveTest {

    private static final Logger LOG = LoggerFactory.getLogger(OsTasksEc2LiveTest.class);
    private static final int TIMEOUT_MS = 1000 * 60 * 10; // ten minutes

    @Override
    protected void doTest(Location loc) throws Exception {
        Entity testEntity = app.createAndManageChild(EntitySpec.create(EmptySoftwareProcess.class));
        app.start(ImmutableList.of(loc));
        EntityTestUtils.assertAttributeEqualsEventually(MutableMap.of("timeout", TIMEOUT_MS),
                testEntity, Startable.SERVICE_UP, true);

        OsDetails details = app.getExecutionContext().submit(OsTasks.getOsDetailsTask(testEntity)).getUnchecked();
        LOG.info("OsTasks live test found the following at {}: name={}, version={}, arch={}, is64bit={}",
                new Object[] {loc, details.getName(), details.getVersion(),
                        details.getArch(), details.is64bit()});
        assertNotNull(details);
        assertNotNull(details.getArch());
        assertNotNull(details.getName());
        assertNotNull(details.getVersion());
        assertFalse(details.getArch().startsWith("architecture:"));
        assertFalse(details.getName().startsWith("name:"));
        assertFalse(details.getVersion().startsWith("version:"));
    }
}
