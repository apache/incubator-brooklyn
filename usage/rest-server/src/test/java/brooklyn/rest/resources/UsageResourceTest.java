package brooklyn.rest.resources;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.SoftwareProcessEntityTest;
import brooklyn.location.Location;
import brooklyn.location.LocationSpec;
import brooklyn.location.NoMachinesAvailableException;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.rest.domain.ApplicationSpec;
import brooklyn.rest.domain.EntitySpec;
import brooklyn.rest.domain.Statistic;
import brooklyn.rest.domain.Status;
import brooklyn.rest.domain.TaskSummary;
import brooklyn.rest.testing.BrooklynRestResourceTest;
import brooklyn.rest.testing.mocks.RestMockSimpleEntity;
import brooklyn.rest.util.JsonUtils;
import brooklyn.test.entity.TestApplication;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.GenericType;

public class UsageResourceTest extends BrooklynRestResourceTest {

    private static final Logger LOG = LoggerFactory.getLogger(UsageResourceTest.class);

    private final ApplicationSpec simpleSpec = ApplicationSpec.builder().name("simple-app").
            entities(ImmutableSet.of(new EntitySpec("simple-ent", RestMockSimpleEntity.class.getName()))).
            locations(ImmutableSet.of("localhost")).
            build();

    @Test
    public void testListApplicationUsages() throws InterruptedException {
        DateFormat format = new SimpleDateFormat(JsonUtils.DATE_FORMAT);
        Date start = new Date();
        ClientResponse response = client().resource("/v1/applications")
                .post(ClientResponse.class, simpleSpec);
        TaskSummary createTask = response.getEntity(TaskSummary.class);
        assertEquals(response.getStatus(), Response.Status.CREATED.getStatusCode());

        response = client().resource("/v1/usage/applications").get(ClientResponse.class);

        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
        List<Statistic> usage = response.getEntity(new GenericType<List<Statistic>>() {});
        assertFalse(usage.isEmpty());

        response = client().resource("/v1/usage/applications?start=" + format.format(start)).get(ClientResponse.class);

        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
        usage = response.getEntity(new GenericType<List<Statistic>>() {});
        assertFalse(usage.isEmpty());
    }

    @Test
    public void testGetApplicationUsagesEmptyForNonExistantApp() {
        ClientResponse response = client().resource("/v1/usage/applications/x").get(ClientResponse.class);

        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
        List<Statistic> usages = response.getEntity(new GenericType<List<Statistic>>() {});
        assertTrue(usages.isEmpty());
    }
    
    @Test
    public void testGetApplicationUsages() {
        ClientResponse response = client().resource("/v1/applications")
                .post(ClientResponse.class, simpleSpec);
        assertEquals(response.getStatus(), Response.Status.CREATED.getStatusCode());
        TaskSummary createTask = response.getEntity(TaskSummary.class);
        
        response = client().resource("/v1/usage/applications/" + createTask.getEntityId()).get(ClientResponse.class);

        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
        List<Statistic> usages = response.getEntity(new GenericType<List<Statistic>>() {});
        assertFalse(usages.isEmpty());
        
        Statistic usage = usages.get(0);
        assertEquals(usage.getId(), createTask.getEntityId());
        assertEquals(usage.getStatus(), Status.RUNNING);

        response = client().resource("/v1/usage/applications/" + createTask.getEntityId() + "?start=1970-01-01T00:00:00-0100").get(ClientResponse.class);
        usages = response.getEntity(new GenericType<List<Statistic>>() {});
        assertFalse(usages.isEmpty());
        
        response = client().resource("/v1/usage/applications/" + createTask.getEntityId() + "?start=9999-01-01T00:00:00+0100").get(ClientResponse.class);
        assertTrue(response.getStatus() >= 500, "end defaults to NOW, so future start should fail");
        
        response = client().resource("/v1/usage/applications/" + createTask.getEntityId() + "?start=9999-01-01T00:00:00%2B0100&end=9999-01-02T00:00:00%2B0100").get(ClientResponse.class);
        usages = response.getEntity(new GenericType<List<Statistic>>() {});
        assertTrue(usages.isEmpty());

        response = client().resource("/v1/usage/applications/" + createTask.getEntityId() + "?end=9999-01-01T00:00:00+0100").get(ClientResponse.class);
        usages = response.getEntity(new GenericType<List<Statistic>>() {});
        assertFalse(usages.isEmpty());

        response = client().resource("/v1/usage/applications/" + createTask.getEntityId() + "?start=9999-01-01T00:00:00+0100&end=9999-02-01T00:00:00+0100").get(ClientResponse.class);
        usages = response.getEntity(new GenericType<List<Statistic>>() {});
        assertTrue(usages.isEmpty());

        response = client().resource("/v1/usage/applications/" + createTask.getEntityId() + "?start=1970-01-01T00:00:00-0100&end=9999-01-01T00:00:00+0100").get(ClientResponse.class);
        usages = response.getEntity(new GenericType<List<Statistic>>() {});
        assertFalse(usages.isEmpty());
        
        response = client().resource("/v1/usage/applications/" + createTask.getEntityId() + "?end=1970-01-01T00:00:00-0100").get(ClientResponse.class);
        usages = response.getEntity(new GenericType<List<Statistic>>() {});
        assertTrue(usages.isEmpty());
    }

    @Test
    public void testGetMachineUsagesInitiallyEmpty() {
        ClientResponse response = client().resource("/v1/usage/machines").get(ClientResponse.class);

        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
        List<Statistic> usage = response.getEntity(new GenericType<List<Statistic>>() {});
        assertTrue(usage.isEmpty());
    }

    @Test(dependsOnMethods="testGetMachineUsagesInitiallyEmpty")
    public void testGetMachineUsageWithMachines() {
        Location location = getManagementContext().getLocationManager().createLocation(LocationSpec.create(DynamicLocalhostMachineProvisioningLocation.class));
        TestApplication app = ApplicationBuilder.newManagedApp(TestApplication.class, getManagementContext());
        SoftwareProcessEntityTest.MyService entity = app.createAndManageChild(brooklyn.entity.proxying.EntitySpec.create(SoftwareProcessEntityTest.MyService.class));
        app.start(ImmutableList.of(location));
        Location machine = Iterables.getOnlyElement(entity.getLocations());
        
        ClientResponse response = client().resource("/v1/usage/machines").get(ClientResponse.class);

        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
        
        List<Statistic> usage = response.getEntity(new GenericType<List<Statistic>>() {});
        assertEquals(usage.size(), 1, "usage="+usage);
        assertEquals(usage.get(0).getId(), machine.getId(), "usage="+usage);
        assertEquals(usage.get(0).getStatus(), Status.ACCEPTED, "usage="+usage);
        assertTrue(usage.get(0).getDuration() >= 0, "usage="+usage);

        LOG.info("usage="+usage);
    }

    @Test(dependsOnMethods={"testGetMachineUsagesInitiallyEmpty", "testGetMachineUsageWithMachines"})
    public void testGetMachineUsageForApp() {
        Location location = getManagementContext().getLocationManager().createLocation(LocationSpec.create(DynamicLocalhostMachineProvisioningLocation.class));
        TestApplication app = ApplicationBuilder.newManagedApp(TestApplication.class, getManagementContext());
        SoftwareProcessEntityTest.MyService entity = app.createAndManageChild(brooklyn.entity.proxying.EntitySpec.create(SoftwareProcessEntityTest.MyService.class));
        app.start(ImmutableList.of(location));
        Location machine = Iterables.getOnlyElement(entity.getLocations());
        
        ClientResponse response = client().resource("/v1/usage/machines?application="+app.getId()).get(ClientResponse.class);

        
        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
        
        List<Statistic> usage = response.getEntity(new GenericType<List<Statistic>>() {});
        assertEquals(usage.size(), 1, "usage="+usage);
        assertEquals(usage.get(0).getId(), machine.getId(), "usage="+usage);
        assertEquals(usage.get(0).getStatus(), Status.ACCEPTED, "usage="+usage);
        assertTrue(usage.get(0).getDuration() >= 0, "usage="+usage);

        LOG.info("usage="+usage);
    }

    @Override
    protected void setUpResources() throws Exception {
        addResources();
    }
    
    public static class DynamicLocalhostMachineProvisioningLocation extends LocalhostMachineProvisioningLocation {
        @Override
        public SshMachineLocation obtain(Map<?, ?> flags) throws NoMachinesAvailableException {
            System.out.println("called DynamicLocalhostMachineProvisioningLocation.obtain");
            return super.obtain(flags);
        }
        
        @Override
        public void release(SshMachineLocation machine) {
            System.out.println("called DynamicLocalhostMachineProvisioningLocation.release");
            super.release(machine);
            super.machines.remove(machine);
        }
    }
}
