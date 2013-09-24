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

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.SoftwareProcessEntityTest;
import brooklyn.location.Location;
import brooklyn.location.LocationSpec;
import brooklyn.location.NoMachinesAvailableException;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.management.internal.LocalUsageManager;
import brooklyn.management.internal.ManagementContextInternal;
import brooklyn.rest.domain.ApplicationSpec;
import brooklyn.rest.domain.EntitySpec;
import brooklyn.rest.domain.Status;
import brooklyn.rest.domain.TaskSummary;
import brooklyn.rest.domain.UsageStatistic;
import brooklyn.rest.domain.UsageStatistics;
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
        ((ManagementContextInternal)getManagementContext()).getStorage().remove(LocalUsageManager.APPLICATION_USAGE_KEY);
        ((ManagementContextInternal)getManagementContext()).getStorage().remove(LocalUsageManager.LOCATION_USAGE_KEY);
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
        Iterable<UsageStatistics> usages = response.getEntity(new GenericType<List<UsageStatistics>>() {});
        UsageStatistics usage = Iterables.getOnlyElement(usages);
        assertAppUsage(usage, appId, ImmutableList.of(Status.STARTING, Status.RUNNING), roundDown(preStart), postStart);

        // check app ignored if endDate before app started
        response = client().resource("/v1/usage/applications?start="+0+"&end="+preStart.getTime()).get(ClientResponse.class);
        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
        usages = response.getEntity(new GenericType<List<UsageStatistics>>() {});
        assertTrue(Iterables.isEmpty(usages), "usages="+usages);

        // check app start and end date truncated, even if running for longer
        // note that start==end means we get a snapshot of the apps in use at that exact time.
        response = client().resource("/v1/usage/applications?start="+postStart.getTime()+"&end="+postStart.getTime()).get(ClientResponse.class);
        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
        usages = response.getEntity(new GenericType<List<UsageStatistics>>() {});
        usage = Iterables.getOnlyElement(usages);
        assertAppUsage(usage, appId, ImmutableList.of(Status.RUNNING), roundDown(preStart), postStart);
        assertAppUsageTimesTruncated(usage, roundDown(postStart), roundDown(postStart));

        // Delete the app
        Date preDelete = new Date();
        deleteApp(appId);
        Date postDelete = new Date();

        // Deleted app still returned, if in time range
        response = client().resource("/v1/usage/applications").get(ClientResponse.class);
        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
        usages = response.getEntity(new GenericType<List<UsageStatistics>>() {});
        usage = Iterables.getOnlyElement(usages);
        assertAppUsage(usage, appId, ImmutableList.of(Status.STARTING, Status.RUNNING, Status.DESTROYED), roundDown(preStart), postDelete);
        assertAppUsage(ImmutableList.copyOf(usage.getStatistics()).subList(2, 3), appId, ImmutableList.of(Status.DESTROYED), roundDown(preDelete), postDelete);

        response = client().resource("/v1/usage/applications?start=" + (postDelete.getTime()+1)).get(ClientResponse.class);
        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
        usages = response.getEntity(new GenericType<List<UsageStatistics>>() {});
        assertTrue(Iterables.isEmpty(usages), "usages="+usages);
    }

    @Test
    public void testGetApplicationUsagesForNonExistantApp() throws Exception {
        ClientResponse response = client().resource("/v1/usage/applications/wrongid").get(ClientResponse.class);
        assertEquals(response.getStatus(), Response.Status.NOT_FOUND.getStatusCode());
    }
    
    @Test
    public void testGetApplicationUsage() throws Exception {
        // Create an app
        Date preStart = new Date();
        String appId = createApp(simpleSpec);
        Date postStart = new Date();
        
        // Normal request returns all
        ClientResponse response = client().resource("/v1/usage/applications/" + appId).get(ClientResponse.class);
        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
        UsageStatistics usage = response.getEntity(new GenericType<UsageStatistics>() {});
        assertAppUsage(usage, appId, ImmutableList.of(Status.STARTING, Status.RUNNING), roundDown(preStart), postStart);

        // Time-constrained requests
        response = client().resource("/v1/usage/applications/" + appId + "?start=1970-01-01T00:00:00-0100").get(ClientResponse.class);
        usage = response.getEntity(new GenericType<UsageStatistics>() {});
        assertAppUsage(usage, appId, ImmutableList.of(Status.STARTING, Status.RUNNING), roundDown(preStart), postStart);
        
        response = client().resource("/v1/usage/applications/" + appId + "?start=9999-01-01T00:00:00+0100").get(ClientResponse.class);
        assertTrue(response.getStatus() >= 500, "end defaults to NOW, so future start should fail");
        
        response = client().resource("/v1/usage/applications/" + appId + "?start=9999-01-01T00:00:00%2B0100&end=9999-01-02T00:00:00%2B0100").get(ClientResponse.class);
        usage = response.getEntity(new GenericType<UsageStatistics>() {});
        assertTrue(usage.getStatistics().isEmpty());

        response = client().resource("/v1/usage/applications/" + appId + "?end=9999-01-01T00:00:00+0100").get(ClientResponse.class);
        usage = response.getEntity(new GenericType<UsageStatistics>() {});
        assertAppUsage(usage, appId, ImmutableList.of(Status.STARTING, Status.RUNNING), roundDown(preStart), postStart);

        response = client().resource("/v1/usage/applications/" + appId + "?start=9999-01-01T00:00:00+0100&end=9999-02-01T00:00:00+0100").get(ClientResponse.class);
        usage = response.getEntity(new GenericType<UsageStatistics>() {});
        assertTrue(usage.getStatistics().isEmpty());

        response = client().resource("/v1/usage/applications/" + appId + "?start=1970-01-01T00:00:00-0100&end=9999-01-01T00:00:00+0100").get(ClientResponse.class);
        usage = response.getEntity(new GenericType<UsageStatistics>() {});
        assertAppUsage(usage, appId, ImmutableList.of(Status.STARTING, Status.RUNNING), roundDown(preStart), postStart);
        
        response = client().resource("/v1/usage/applications/" + appId + "?end=1970-01-01T00:00:00-0100").get(ClientResponse.class);
        usage = response.getEntity(new GenericType<UsageStatistics>() {});
        assertTrue(usage.getStatistics().isEmpty());
        
        // Delete the app
        Date preDelete = new Date();
        deleteApp(appId);
        Date postDelete = new Date();

        // Deleted app still returned, if in time range
        response = client().resource("/v1/usage/applications/" + appId).get(ClientResponse.class);
        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
        usage = response.getEntity(new GenericType<UsageStatistics>() {});
        assertAppUsage(usage, appId, ImmutableList.of(Status.STARTING, Status.RUNNING, Status.DESTROYED), roundDown(preStart), postDelete);
        assertAppUsage(ImmutableList.copyOf(usage.getStatistics()).subList(2, 3), appId, ImmutableList.of(Status.DESTROYED), roundDown(preDelete), postDelete);

        // Deleted app not returned if terminated before time range begins
        response = client().resource("/v1/usage/applications/" + appId +"?start=" + (postDelete.getTime()+1)).get(ClientResponse.class);
        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
        usage = response.getEntity(new GenericType<UsageStatistics>() {});
        assertTrue(usage.getStatistics().isEmpty(), "usages="+usage);
    }

    @Test
    public void testGetMachineUsagesForNonExistantMachine() throws Exception {
        ClientResponse response = client().resource("/v1/usage/machines/wrongid").get(ClientResponse.class);
        assertEquals(response.getStatus(), Response.Status.NOT_FOUND.getStatusCode());
    }

    @Test
    public void testGetMachineUsagesInitiallyEmpty() throws Exception {
        // All machines: empty
        ClientResponse response = client().resource("/v1/usage/machines").get(ClientResponse.class);
        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
        Iterable<UsageStatistics> usages = response.getEntity(new GenericType<List<UsageStatistics>>() {});
        assertTrue(Iterables.isEmpty(usages));
        
        // Specific machine that does not exist: get 404
        response = client().resource("/v1/usage/machines/machineIdThatDoesNotExist").get(ClientResponse.class);
        assertEquals(response.getStatus(), Response.Status.NOT_FOUND.getStatusCode());
    }

    @Test
    public void testListAndGetMachineUsage() throws Exception {
        Location location = getManagementContext().getLocationManager().createLocation(LocationSpec.create(DynamicLocalhostMachineProvisioningLocation.class));
        TestApplication app = ApplicationBuilder.newManagedApp(TestApplication.class, getManagementContext());
        SoftwareProcessEntityTest.MyService entity = app.createAndManageChild(brooklyn.entity.proxying.EntitySpec.create(SoftwareProcessEntityTest.MyService.class));
        
        Date preStart = new Date();
        app.start(ImmutableList.of(location));
        Date postStart = new Date();
        Location machine = Iterables.getOnlyElement(entity.getLocations());

        // All machines
        ClientResponse response = client().resource("/v1/usage/machines").get(ClientResponse.class);
        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
        Iterable<UsageStatistics> usages = response.getEntity(new GenericType<List<UsageStatistics>>() {});
        UsageStatistics usage = Iterables.getOnlyElement(usages);
        assertMachineUsage(usage, app.getId(), machine.getId(), ImmutableList.of(Status.ACCEPTED), roundDown(preStart), postStart);

        // Specific machine
        response = client().resource("/v1/usage/machines/"+machine.getId()).get(ClientResponse.class);
        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
        usage = response.getEntity(new GenericType<UsageStatistics>() {});
        assertMachineUsage(usage, app.getId(), machine.getId(), ImmutableList.of(Status.ACCEPTED), roundDown(preStart), postStart);
    }

    @Test
    public void testListMachinesUsageForApp() throws Exception {
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
        Iterable<UsageStatistics> usages = response.getEntity(new GenericType<List<UsageStatistics>>() {});
        UsageStatistics usage = Iterables.getOnlyElement(usages);
        assertMachineUsage(usage, app.getId(), machine.getId(), ImmutableList.of(Status.ACCEPTED), roundDown(preStart), postStart);
        
        // Stop the machine
        Date preStop = new Date();
        app.stop();
        Date postStop = new Date();
        
        // Deleted machine still returned, if in time range
        response = client().resource("/v1/usage/machines?application=" + appId).get(ClientResponse.class);
        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
        usages = response.getEntity(new GenericType<List<UsageStatistics>>() {});
        usage = Iterables.getOnlyElement(usages);
        assertMachineUsage(usage, app.getId(), machine.getId(), ImmutableList.of(Status.ACCEPTED, Status.DESTROYED), roundDown(preStart), postStop);
        assertMachineUsage(ImmutableList.copyOf(usage.getStatistics()).subList(1,2), appId, machine.getId(), ImmutableList.of(Status.DESTROYED), roundDown(preStop), postStop);

        // Terminated machines ignored if terminated since start-time
        response = client().resource("/v1/usage/applications?start=" + (postStop.getTime()+1)).get(ClientResponse.class);
        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
        usages = response.getEntity(new GenericType<List<UsageStatistics>>() {});
        assertTrue(Iterables.isEmpty(usages), "usages="+usages);
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

    private void assertMachineUsage(UsageStatistics usage, String appId, String machineId, List<Status> states, Date pre, Date post) throws Exception {
        assertUsage(usage.getStatistics(), appId, machineId, states, pre, post, false);
    }
    
    private void assertMachineUsage(Iterable<UsageStatistic> usages, String appId, String machineId, List<Status> states, Date pre, Date post) throws Exception {
        assertUsage(usages, appId, machineId, states, pre, post, false);
    }
    
    private void assertAppUsage(UsageStatistics usage, String appId, List<Status> states, Date pre, Date post) throws Exception {
        assertUsage(usage.getStatistics(), appId, appId, states, pre, post, false);
    }
    
    private void assertAppUsage(Iterable<UsageStatistic> usages, String appId, List<Status> states, Date pre, Date post) throws Exception {
        assertUsage(usages, appId, appId, states, pre, post, false);
    }

    private void assertUsage(Iterable<UsageStatistic> usages, String appId, String id, List<Status> states, Date pre, Date post, boolean allowGaps) throws Exception {
        String errMsg = "usages="+usages;
        Date now = new Date();
        Date lowerBound = pre;
        Date strictStart = null;
        
        assertEquals(Iterables.size(usages), states.size(), errMsg);
        for (int i = 0; i < Iterables.size(usages); i++) {
            UsageStatistic usage = Iterables.get(usages, i);
            Date usageStart = format.parse(usage.getStart());
            Date usageEnd = format.parse(usage.getEnd());
            assertEquals(usage.getId(), id, errMsg);
            assertEquals(usage.getApplicationId(), appId, errMsg);
            assertEquals(usage.getStatus(), states.get(i), errMsg);
            assertDateOrders(usages, lowerBound, usageStart, post);
            assertDateOrders(usages, usageEnd, now);
            if (strictStart != null) {
                assertEquals(usageStart, strictStart, errMsg);
            }
            if (!allowGaps) {
                strictStart = usageEnd;
            }
            lowerBound = usageEnd;
        }
    }

    private void assertAppUsageTimesTruncated(UsageStatistics usages, Date strictStart, Date strictEnd) throws Exception {
        String errMsg = "usages="+usages+"; strictStart="+strictStart+"; strictEnd="+strictEnd;
        Date usageStart = format.parse(Iterables.getFirst(usages.getStatistics(), null).getStart());
        Date usageEnd = format.parse(Iterables.getLast(usages.getStatistics()).getStart());
        assertEquals(usageStart, strictStart, errMsg);
        assertEquals(usageEnd, strictEnd, errMsg);
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
