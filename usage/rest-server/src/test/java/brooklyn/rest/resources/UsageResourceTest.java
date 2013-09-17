package brooklyn.rest.resources;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.AbstractApplication;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.SoftwareProcessEntityTest;
import brooklyn.location.Location;
import brooklyn.location.LocationSpec;
import brooklyn.location.NoMachinesAvailableException;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.management.internal.LocalLocationManager;
import brooklyn.management.internal.ManagementContextInternal;
import brooklyn.rest.domain.ApplicationSpec;
import brooklyn.rest.domain.EntitySpec;
import brooklyn.rest.domain.Statistic;
import brooklyn.rest.domain.Status;
import brooklyn.rest.domain.TaskSummary;
import brooklyn.rest.testing.BrooklynRestResourceTest;
import brooklyn.rest.testing.mocks.RestMockSimpleEntity;
import brooklyn.rest.util.JsonUtils;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.internal.Repeater;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.GenericType;

public class UsageResourceTest extends BrooklynRestResourceTest {

    private static final Logger LOG = LoggerFactory.getLogger(UsageResourceTest.class);

    private static final long TIMEOUT_MS = 10*1000;
    
    private Date testStartTime;
    private DateFormat format = new SimpleDateFormat(JsonUtils.DATE_FORMAT);
    
    private final ApplicationSpec simpleSpec = ApplicationSpec.builder().name("simple-app").
            entities(ImmutableSet.of(new EntitySpec("simple-ent", RestMockSimpleEntity.class.getName()))).
            locations(ImmutableSet.of("localhost")).
            build();

    @BeforeMethod(alwaysRun=true)
    public void setUpMethod() {
        ((ManagementContextInternal)getManagementContext()).getStorage().remove(AbstractApplication.APPLICATION_USAGE_KEY);
        ((ManagementContextInternal)getManagementContext()).getStorage().remove(LocalLocationManager.LOCATION_USAGE_KEY);
        testStartTime = new Date();
    }

    @Override
    protected void setUpResources() throws Exception {
        addResources();
    }
    
    @Test
    public void testListApplicationUsages() throws Exception {
        // Create an app
        Date preStart = new Date();
        String appId = createApp(simpleSpec);
        Date postStart = new Date();

        // Check that app's usage is returned
        ClientResponse response = client().resource("/v1/usage/applications").get(ClientResponse.class);
        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
        List<Statistic> usages = response.getEntity(new GenericType<List<Statistic>>() {});
        Statistic usage = Iterables.getOnlyElement(usages);
        Date usageStart = format.parse(usage.getStart());
        Date usageEnd = format.parse(usage.getStart());
        
        assertEquals(usage.getApplicationId(), appId);
        assertEquals(usage.getStatus(), Status.RUNNING);
        assertDateOrders(usage, roundDown(preStart), usageStart, postStart);
        assertDateOrders(usage, usageStart, usageEnd, new Date());

        // check app ignored if endDate before app started
        response = client().resource("/v1/usage/applications?start="+0+"&end="+preStart.getTime()).get(ClientResponse.class);
        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
        usages = response.getEntity(new GenericType<List<Statistic>>() {});
        assertEquals(usages, ImmutableList.of(), "usages="+usages);

        // check app start and end date truncated, even if running for longer
        response = client().resource("/v1/usage/applications?start="+postStart.getTime()+"&end="+postStart.getTime()).get(ClientResponse.class);
        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
        usages = response.getEntity(new GenericType<List<Statistic>>() {});
        usage = Iterables.getOnlyElement(usages);
        usageStart = format.parse(usage.getStart());
        usageEnd = format.parse(usage.getEnd());
        assertEquals(usageStart, roundDown(postStart));
        assertEquals(usageEnd, roundDown(postStart));

        // Delete the app
        Date preDelete = new Date();
        deleteApp(appId);
        Date postDelete = new Date();

        // Deleted app still returned, if in time range
        response = client().resource("/v1/usage/applications").get(ClientResponse.class);
        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
        usages = response.getEntity(new GenericType<List<Statistic>>() {});
        Statistic usageRunning = usages.get(0);
        Statistic usageTerminated = usages.get(1);
        assertEquals(usages.size(), 2);
        assertEquals(usageRunning.getApplicationId(), appId);
        assertEquals(usageRunning.getStatus(), Status.RUNNING);
        assertDateOrders(usages, roundDown(preDelete), format.parse(usageRunning.getEnd()), postDelete);
        assertEquals(usageTerminated.getApplicationId(), appId);
        assertEquals(usageTerminated.getStatus(), Status.TERMINATED);
        assertDateOrders(usages, roundDown(preDelete), format.parse(usageTerminated.getStart()), format.parse(usageTerminated.getEnd()), postDelete);

        response = client().resource("/v1/usage/applications?start=" + (postDelete.getTime()+1)).get(ClientResponse.class);
        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
        usages = response.getEntity(new GenericType<List<Statistic>>() {});
        assertTrue(usages.isEmpty(), "usages="+usages);
    }

    @Test
    public void testGetApplicationUsagesEmptyForNonExistantApp() throws Exception {
        ClientResponse response = client().resource("/v1/usage/applications/x").get(ClientResponse.class);

        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
        List<Statistic> usages = response.getEntity(new GenericType<List<Statistic>>() {});
        assertTrue(usages.isEmpty());
    }
    
    @Test
    public void testGetApplicationUsages() throws Exception {
        // Create an app
        Date preStart = new Date();
        String appId = createApp(simpleSpec);
        Date postStart = new Date();
        
        // Normal request returns all
        ClientResponse response = client().resource("/v1/usage/applications/" + appId).get(ClientResponse.class);
        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
        List<Statistic> usages = response.getEntity(new GenericType<List<Statistic>>() {});
        Statistic usage = Iterables.getOnlyElement(usages);
        assertEquals(usage.getId(), appId);
        assertEquals(usage.getStatus(), Status.RUNNING);
        assertDateOrders(usage, roundDown(preStart), format.parse(usage.getStart()), postStart);

        // Time-constrained requests
        response = client().resource("/v1/usage/applications/" + appId + "?start=1970-01-01T00:00:00-0100").get(ClientResponse.class);
        usages = response.getEntity(new GenericType<List<Statistic>>() {});
        usage = Iterables.getOnlyElement(usages);
        assertEquals(usage.getId(), appId);
        
        response = client().resource("/v1/usage/applications/" + appId + "?start=9999-01-01T00:00:00+0100").get(ClientResponse.class);
        assertTrue(response.getStatus() >= 500, "end defaults to NOW, so future start should fail");
        
        response = client().resource("/v1/usage/applications/" + appId + "?start=9999-01-01T00:00:00%2B0100&end=9999-01-02T00:00:00%2B0100").get(ClientResponse.class);
        usages = response.getEntity(new GenericType<List<Statistic>>() {});
        assertTrue(usages.isEmpty());

        response = client().resource("/v1/usage/applications/" + appId + "?end=9999-01-01T00:00:00+0100").get(ClientResponse.class);
        usages = response.getEntity(new GenericType<List<Statistic>>() {});
        usage = Iterables.getOnlyElement(usages);
        assertEquals(usage.getId(), appId);

        response = client().resource("/v1/usage/applications/" + appId + "?start=9999-01-01T00:00:00+0100&end=9999-02-01T00:00:00+0100").get(ClientResponse.class);
        usages = response.getEntity(new GenericType<List<Statistic>>() {});
        assertTrue(usages.isEmpty());

        response = client().resource("/v1/usage/applications/" + appId + "?start=1970-01-01T00:00:00-0100&end=9999-01-01T00:00:00+0100").get(ClientResponse.class);
        usages = response.getEntity(new GenericType<List<Statistic>>() {});
        usage = Iterables.getOnlyElement(usages);
        assertEquals(usage.getId(), appId);
        
        response = client().resource("/v1/usage/applications/" + appId + "?end=1970-01-01T00:00:00-0100").get(ClientResponse.class);
        usages = response.getEntity(new GenericType<List<Statistic>>() {});
        assertTrue(usages.isEmpty());
        
        // Delete the app
        Date preDelete = new Date();
        deleteApp(appId);
        Date postDelete = new Date();

        // Deleted app still returned, if in time range
        response = client().resource("/v1/usage/applications/" + appId).get(ClientResponse.class);
        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
        usages = response.getEntity(new GenericType<List<Statistic>>() {});
        Statistic usageRunning = usages.get(0);
        Statistic usageTerminated = usages.get(1);
        assertEquals(usages.size(), 2);
        assertEquals(usageRunning.getApplicationId(), appId);
        assertEquals(usageRunning.getStatus(), Status.RUNNING);
        assertDateOrders(usages, roundDown(preStart), format.parse(usageRunning.getStart()), postStart);
        assertDateOrders(usages, roundDown(preDelete), format.parse(usageRunning.getEnd()), postDelete);
        assertEquals(usageTerminated.getApplicationId(), appId);
        assertEquals(usageTerminated.getStatus(), Status.TERMINATED);
        assertDateOrders(usages, roundDown(preDelete), format.parse(usageTerminated.getStart()), format.parse(usageTerminated.getEnd()), postDelete);

        response = client().resource("/v1/usage/applications?start=" + (postDelete.getTime()+1)).get(ClientResponse.class);
        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
        usages = response.getEntity(new GenericType<List<Statistic>>() {});
        assertTrue(usages.isEmpty(), "usages="+usages);
    }

    @Test
    public void testGetMachineUsagesInitiallyEmpty() throws Exception {
        ClientResponse response = client().resource("/v1/usage/machines").get(ClientResponse.class);

        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
        List<Statistic> usage = response.getEntity(new GenericType<List<Statistic>>() {});
        assertTrue(usage.isEmpty());
    }

    @Test
    public void testGetMachineUsageWithMachines() throws Exception {
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

    @Test
    public void testGetMachineUsageForApp() throws Exception {
        Location location = getManagementContext().getLocationManager().createLocation(LocationSpec.create(DynamicLocalhostMachineProvisioningLocation.class));
        TestApplication app = ApplicationBuilder.newManagedApp(TestApplication.class, getManagementContext());
        SoftwareProcessEntityTest.MyService entity = app.createAndManageChild(brooklyn.entity.proxying.EntitySpec.create(SoftwareProcessEntityTest.MyService.class));
        String appId = app.getId();
        Date preStart = new Date();
        app.start(ImmutableList.of(location));
        Date postStart = new Date();
        Location machine = Iterables.getOnlyElement(entity.getLocations());

        // For running machine
        ClientResponse response = client().resource("/v1/usage/machines?application="+appId).get(ClientResponse.class);
        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
        List<Statistic> usages = response.getEntity(new GenericType<List<Statistic>>() {});
        Statistic usage = Iterables.getOnlyElement(usages);
        assertEquals(usage.getId(), machine.getId(), "usage="+usages);
        assertEquals(usage.getStatus(), Status.ACCEPTED, "usage="+usages);
        assertTrue(usage.getDuration() >= 0, "usage="+usages);
        assertDateOrders(usages, roundDown(preStart), format.parse(usage.getStart()), postStart);
        assertDateOrders(usages, roundDown(postStart), format.parse(usage.getEnd()), new Date());
        
        // Stop the machine
        Date preStop = new Date();
        app.stop();
        Date postStop = new Date();
        
        // Deleted machine still returned, if in time range
        response = client().resource("/v1/usage/machines?application=" + appId).get(ClientResponse.class);
        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
        usages = response.getEntity(new GenericType<List<Statistic>>() {});
        Statistic usageRunning = usages.get(0);
        Statistic usageTerminated = usages.get(1);
        assertEquals(usages.size(), 2);
        assertEquals(usageRunning.getApplicationId(), appId);
        assertEquals(usageRunning.getStatus(), Status.ACCEPTED);
        assertDateOrders(usages, roundDown(preStart), format.parse(usageRunning.getStart()), postStart);
        assertDateOrders(usages, roundDown(preStop), format.parse(usageRunning.getEnd()), postStop);
        assertEquals(usageTerminated.getApplicationId(), appId);
        assertEquals(usageTerminated.getStatus(), Status.TERMINATED);
        assertDateOrders(usages, roundDown(preStop), format.parse(usageTerminated.getStart()), format.parse(usageTerminated.getEnd()), postStop);

        // Terminated machines ignored if terminated since start-time
        response = client().resource("/v1/usage/applications?start=" + (postStop.getTime()+1)).get(ClientResponse.class);
        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
        usages = response.getEntity(new GenericType<List<Statistic>>() {});
        assertTrue(usages.isEmpty(), "usages="+usages);
    }

    private String createApp(ApplicationSpec spec) {
        ClientResponse response = client().resource("/v1/applications")
                .post(ClientResponse.class, spec);
        assertEquals(response.getStatus(), Response.Status.CREATED.getStatusCode());
        TaskSummary createTask = response.getEntity(TaskSummary.class);
        waitForTask(createTask.getId());
        return createTask.getEntityId();
    }
    
    private void deleteApp(String appId) {
        ClientResponse response = client().resource("/v1/applications/"+appId)
                .delete(ClientResponse.class);
        assertEquals(response.getStatus(), Response.Status.ACCEPTED.getStatusCode());
        TaskSummary deletionTask = response.getEntity(TaskSummary.class);
        waitForTask(deletionTask.getId());
    }
    
    private void assertDateOrders(Object context, Date... dates) {
        if (dates.length <= 1) return;
        
        long[] times = new long[dates.length];
        for (int i = 0; i < times.length; i++) {
            times[i] = millisSinceStart(dates[i]);
        }
        String err = "context="+context+"; dates="+Arrays.toString(dates) + "; datesSanitized="+Arrays.toString(times);
        
        Date date = dates[0];
        for (int i = 1; i < dates.length; i++) {
            assertTrue(date.getTime() <= dates[i].getTime(), err);
        }
    }
    
    private void waitForTask(final String taskId) {
        boolean success = Repeater.create()
                .repeat(new Runnable() { public void run() {}})
                .until(new Callable<Boolean>() {
                    @Override public Boolean call() {
                        ClientResponse response = client().resource("/v1/activities/"+taskId).get(ClientResponse.class);
                        if (response.getStatus() == Response.Status.NOT_FOUND.getStatusCode()) {
                            return true;
                        }
                        TaskSummary summary = response.getEntity(TaskSummary.class);
                        return summary != null && summary.getEndTimeUtc() != null;
                    }})
                .every(10L, TimeUnit.MILLISECONDS)
                .limitTimeTo(TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .run();
        assertTrue(success, "task "+taskId+" not finished");
    }

    private long millisSinceStart(Date time) {
        return time.getTime() - testStartTime.getTime();
    }
    
    private Date roundDown(Date date) {
        long time = date.getTime();
        long timeDown = ((long)(time / 1000)) * 1000;
        return new Date(timeDown);
    }
    
    private Date roundUp(Date date) {
        long time = date.getTime();
        long timeDown = ((long)(time / 1000)) * 1000;
        long timeUp = (time == timeDown) ? time : timeDown + 1000;
        return new Date(timeUp);
    }

    public static class DynamicLocalhostMachineProvisioningLocation extends LocalhostMachineProvisioningLocation {
        @Override
        public SshMachineLocation obtain(Map<?, ?> flags) throws NoMachinesAvailableException {
            return super.obtain(flags);
        }
        
        @Override
        public void release(SshMachineLocation machine) {
            super.release(machine);
            super.machines.remove(machine);
            getManagementContext().getLocationManager().unmanage(machine);
        }
    }
}
