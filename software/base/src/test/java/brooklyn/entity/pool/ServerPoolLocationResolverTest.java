package brooklyn.entity.pool;

import static org.testng.Assert.assertEquals;

import java.util.Map;

import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

import brooklyn.config.BrooklynProperties;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.EmptySoftwareProcess;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.location.Location;
import brooklyn.location.LocationSpec;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.location.dynamic.DynamicLocation;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.test.entity.LocalManagementContextForTests;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.collections.MutableMap;

public class ServerPoolLocationResolverTest {

    private LocalManagementContext managementContext;
    private Entity locationOwner;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        managementContext = new LocalManagementContextForTests(BrooklynProperties.Factory.newEmpty());
        TestApplication t = ApplicationBuilder.newManagedApp(TestApplication.class, managementContext);
        locationOwner = t.createAndManageChild(EntitySpec.create(ServerPool.class)
                .configure(ServerPool.INITIAL_SIZE, 0)
                .configure(ServerPool.MEMBER_SPEC, EntitySpec.create(EmptySoftwareProcess.class)));
        Location poolLocation = managementContext.getLocationManager()
                .createLocation(LocationSpec.create(LocalhostMachineProvisioningLocation.class));
        t.start(ImmutableList.of(poolLocation));
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (managementContext != null) Entities.destroyAll(managementContext);
    }

    @Test
    public void testResolve() {
        ServerPoolLocation location = resolve("pool:" + locationOwner.getId());
        assertEquals(location.getOwner().getId(), locationOwner.getId());
    }

    @Test
    public void testSetsDisplayName() {
        ServerPoolLocation location = resolve("pool:" + locationOwner.getId() + ":(displayName=xyz)");
        assertEquals(location.getDisplayName(), "xyz");
    }

    private ServerPoolLocation resolve(String val) {
        Map<String, Object> flags = MutableMap.<String, Object>of(DynamicLocation.OWNER.getName(), locationOwner);
        Location l = managementContext.getLocationRegistry().resolve(val, flags);
        Assert.assertNotNull(l);
        return (ServerPoolLocation) l;
    }

}
