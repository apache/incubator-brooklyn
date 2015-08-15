/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.brooklyn.rest.resources;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.util.Arrays;
import java.util.Calendar;
import java.util.GregorianCalendar;
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

import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.location.LocationSpec;
import org.apache.brooklyn.api.location.NoMachinesAvailableException;
import org.apache.brooklyn.core.management.internal.LocalUsageManager;
import org.apache.brooklyn.core.management.internal.ManagementContextInternal;
import org.apache.brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import org.apache.brooklyn.location.basic.SshMachineLocation;
import org.apache.brooklyn.rest.domain.ApplicationSpec;
import org.apache.brooklyn.rest.domain.EntitySpec;
import org.apache.brooklyn.rest.domain.Status;
import org.apache.brooklyn.rest.domain.TaskSummary;
import org.apache.brooklyn.rest.domain.UsageStatistic;
import org.apache.brooklyn.rest.domain.UsageStatistics;
import org.apache.brooklyn.rest.testing.BrooklynRestResourceTest;
import org.apache.brooklyn.rest.testing.mocks.RestMockSimpleEntity;
import org.apache.brooklyn.test.entity.TestApplication;

import brooklyn.util.repeat.Repeater;
import brooklyn.util.time.Time;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.GenericType;

public class UsageResourceTest extends BrooklynRestResourceTest {

    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(UsageResourceTest.class);

    private static final long TIMEOUT_MS = 10*1000;
    
    private Calendar testStartTime;
    
    private final ApplicationSpec simpleSpec = ApplicationSpec.builder().name("simple-app").
            entities(ImmutableSet.of(new EntitySpec("simple-ent", RestMockSimpleEntity.class.getName()))).
            locations(ImmutableSet.of("localhost")).
            build();

    @BeforeMethod(alwaysRun=true)
    public void setUpMethod() {
        ((ManagementContextInternal)getManagementContext()).getStorage().remove(LocalUsageManager.APPLICATION_USAGE_KEY);
        ((ManagementContextInternal)getManagementContext()).getStorage().remove(LocalUsageManager.LOCATION_USAGE_KEY);
        testStartTime = new GregorianCalendar();
    }

    @Test
    public void testListApplicationUsages() throws Exception {
        // Create an app
        Calendar preStart = new GregorianCalendar();
        String appId = createApp(simpleSpec);
        Calendar postStart = new GregorianCalendar();
        
        // We will retrieve usage from one millisecond after start; this guarantees to not be  
        // told about both STARTING+RUNNING, which could otherwise happen if they are in the 
        // same milliscond.
        Calendar afterPostStart = Time.newCalendarFromMillisSinceEpochUtc(postStart.getTime().getTime()+1);
        
        // Check that app's usage is returned
        ClientResponse response = client().resource("/v1/usage/applications").get(ClientResponse.class);
        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
        Iterable<UsageStatistics> usages = response.getEntity(new GenericType<List<UsageStatistics>>() {});
        UsageStatistics usage = Iterables.getOnlyElement(usages);
        assertAppUsage(usage, appId, ImmutableList.of(Status.STARTING, Status.RUNNING), roundDown(preStart), postStart);

        // check app ignored if endCalendar before app started
        response = client().resource("/v1/usage/applications?start="+0+"&end="+(preStart.getTime().getTime()-1)).get(ClientResponse.class);
        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
        usages = response.getEntity(new GenericType<List<UsageStatistics>>() {});
        assertTrue(Iterables.isEmpty(usages), "usages="+usages);
        
        // Wait, so that definitely asking about things that have happened (not things in the future, 
        // or events that are happening this exact same millisecond)
        waitForFuture(afterPostStart.getTime().getTime());

        // Check app start + end date truncated, even if running for longer (i.e. only tell us about this time window).
        // Note that start==end means we get a snapshot of the apps in use at that exact time.
        //
        // The start/end times in UsageStatistic are in String format, and are rounded down to the nearest second.
        // The comparison does use the milliseconds passed in the REST call though.
        // The rounding down result should be the same as roundDown(afterPostStart), because that is the time-window
        // we asked for.
        response = client().resource("/v1/usage/applications?start="+afterPostStart.getTime().getTime()+"&end="+afterPostStart.getTime().getTime()).get(ClientResponse.class);
        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
        usages = response.getEntity(new GenericType<List<UsageStatistics>>() {});
        usage = Iterables.getOnlyElement(usages);
        assertAppUsage(usage, appId, ImmutableList.of(Status.RUNNING), roundDown(preStart), postStart);
        assertAppUsageTimesTruncated(usage, roundDown(afterPostStart), roundDown(afterPostStart));

        // Delete the app
        Calendar preDelete = new GregorianCalendar();
        deleteApp(appId);
        Calendar postDelete = new GregorianCalendar();

        // Deleted app still returned, if in time range
        response = client().resource("/v1/usage/applications").get(ClientResponse.class);
        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
        usages = response.getEntity(new GenericType<List<UsageStatistics>>() {});
        usage = Iterables.getOnlyElement(usages);
        assertAppUsage(usage, appId, ImmutableList.of(Status.STARTING, Status.RUNNING, Status.DESTROYED), roundDown(preStart), postDelete);
        assertAppUsage(ImmutableList.copyOf(usage.getStatistics()).subList(2, 3), appId, ImmutableList.of(Status.DESTROYED), roundDown(preDelete), postDelete);

        long afterPostDelete = postDelete.getTime().getTime()+1;
        waitForFuture(afterPostDelete);
        
        response = client().resource("/v1/usage/applications?start=" + afterPostDelete).get(ClientResponse.class);
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
        Calendar preStart = new GregorianCalendar();
        String appId = createApp(simpleSpec);
        Calendar postStart = new GregorianCalendar();
        
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
        assertTrue(response.getStatus() >= 400, "end defaults to NOW, so future start should fail, instead got code "+response.getStatus());
        
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
        Calendar preDelete = new GregorianCalendar();
        deleteApp(appId);
        Calendar postDelete = new GregorianCalendar();

        // Deleted app still returned, if in time range
        response = client().resource("/v1/usage/applications/" + appId).get(ClientResponse.class);
        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
        usage = response.getEntity(new GenericType<UsageStatistics>() {});
        assertAppUsage(usage, appId, ImmutableList.of(Status.STARTING, Status.RUNNING, Status.DESTROYED), roundDown(preStart), postDelete);
        assertAppUsage(ImmutableList.copyOf(usage.getStatistics()).subList(2, 3), appId, ImmutableList.of(Status.DESTROYED), roundDown(preDelete), postDelete);

        // Deleted app not returned if terminated before time range begins
        long afterPostDelete = postDelete.getTime().getTime()+1;
        waitForFuture(afterPostDelete);
        response = client().resource("/v1/usage/applications/" + appId +"?start=" + afterPostDelete).get(ClientResponse.class);
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
        SoftwareProcessEntityTest.MyService entity = app.createAndManageChild(org.apache.brooklyn.api.entity.proxying.EntitySpec.create(SoftwareProcessEntityTest.MyService.class));
        
        Calendar preStart = new GregorianCalendar();
        app.start(ImmutableList.of(location));
        Calendar postStart = new GregorianCalendar();
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
        SoftwareProcessEntityTest.MyService entity = app.createAndManageChild(org.apache.brooklyn.api.entity.proxying.EntitySpec.create(SoftwareProcessEntityTest.MyService.class));
        String appId = app.getId();
        
        Calendar preStart = new GregorianCalendar();
        app.start(ImmutableList.of(location));
        Calendar postStart = new GregorianCalendar();
        Location machine = Iterables.getOnlyElement(entity.getLocations());

        // For running machine
        ClientResponse response = client().resource("/v1/usage/machines?application="+appId).get(ClientResponse.class);
        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
        Iterable<UsageStatistics> usages = response.getEntity(new GenericType<List<UsageStatistics>>() {});
        UsageStatistics usage = Iterables.getOnlyElement(usages);
        assertMachineUsage(usage, app.getId(), machine.getId(), ImmutableList.of(Status.ACCEPTED), roundDown(preStart), postStart);
        
        // Stop the machine
        Calendar preStop = new GregorianCalendar();
        app.stop();
        Calendar postStop = new GregorianCalendar();
        
        // Deleted machine still returned, if in time range
        response = client().resource("/v1/usage/machines?application=" + appId).get(ClientResponse.class);
        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
        usages = response.getEntity(new GenericType<List<UsageStatistics>>() {});
        usage = Iterables.getOnlyElement(usages);
        assertMachineUsage(usage, app.getId(), machine.getId(), ImmutableList.of(Status.ACCEPTED, Status.DESTROYED), roundDown(preStart), postStop);
        assertMachineUsage(ImmutableList.copyOf(usage.getStatistics()).subList(1,2), appId, machine.getId(), ImmutableList.of(Status.DESTROYED), roundDown(preStop), postStop);

        // Terminated machines ignored if terminated since start-time
        long futureTime = postStop.getTime().getTime()+1;
        waitForFuture(futureTime);
        response = client().resource("/v1/usage/applications?start=" + futureTime).get(ClientResponse.class);
        assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
        usages = response.getEntity(new GenericType<List<UsageStatistics>>() {});
        assertTrue(Iterables.isEmpty(usages), "usages="+usages);
    }

    private String createApp(ApplicationSpec spec) {
        ClientResponse response = clientDeploy(spec);
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
    
    private void assertCalendarOrders(Object context, Calendar... Calendars) {
        if (Calendars.length <= 1) return;
        
        long[] times = new long[Calendars.length];
        for (int i = 0; i < times.length; i++) {
            times[i] = millisSinceStart(Calendars[i]);
        }
        String err = "context="+context+"; Calendars="+Arrays.toString(Calendars) + "; CalendarsSanitized="+Arrays.toString(times);
        
        Calendar Calendar = Calendars[0];
        for (int i = 1; i < Calendars.length; i++) {
            assertTrue(Calendar.getTime().getTime() <= Calendars[i].getTime().getTime(), err);
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

    private long millisSinceStart(Calendar time) {
        return time.getTime().getTime() - testStartTime.getTime().getTime();
    }
    
    private Calendar roundDown(Calendar calendar) {
        long time = calendar.getTime().getTime();
        long timeDown = ((long)(time / 1000)) * 1000;
        return Time.newCalendarFromMillisSinceEpochUtc(timeDown);
    }
    
    @SuppressWarnings("unused")
    private Calendar roundUp(Calendar calendar) {
        long time = calendar.getTime().getTime();
        long timeDown = ((long)(time / 1000)) * 1000;
        long timeUp = (time == timeDown) ? time : timeDown + 1000;
        return Time.newCalendarFromMillisSinceEpochUtc(timeUp);
    }

    private void assertMachineUsage(UsageStatistics usage, String appId, String machineId, List<Status> states, Calendar pre, Calendar post) throws Exception {
        assertUsage(usage.getStatistics(), appId, machineId, states, pre, post, false);
    }
    
    private void assertMachineUsage(Iterable<UsageStatistic> usages, String appId, String machineId, List<Status> states, Calendar pre, Calendar post) throws Exception {
        assertUsage(usages, appId, machineId, states, pre, post, false);
    }
    
    private void assertAppUsage(UsageStatistics usage, String appId, List<Status> states, Calendar pre, Calendar post) throws Exception {
        assertUsage(usage.getStatistics(), appId, appId, states, pre, post, false);
    }
    
    private void assertAppUsage(Iterable<UsageStatistic> usages, String appId, List<Status> states, Calendar pre, Calendar post) throws Exception {
        assertUsage(usages, appId, appId, states, pre, post, false);
    }

    private void assertUsage(Iterable<UsageStatistic> usages, String appId, String id, List<Status> states, Calendar pre, Calendar post, boolean allowGaps) throws Exception {
        String errMsg = "usages="+usages;
        Calendar now = new GregorianCalendar();
        Calendar lowerBound = pre;
        Calendar strictStart = null;
        
        assertEquals(Iterables.size(usages), states.size(), errMsg);
        for (int i = 0; i < Iterables.size(usages); i++) {
            UsageStatistic usage = Iterables.get(usages, i);
            Calendar usageStart = Time.parseCalendar(usage.getStart());
            Calendar usageEnd = Time.parseCalendar(usage.getEnd());
            assertEquals(usage.getId(), id, errMsg);
            assertEquals(usage.getApplicationId(), appId, errMsg);
            assertEquals(usage.getStatus(), states.get(i), errMsg);
            assertCalendarOrders(usages, lowerBound, usageStart, post);
            assertCalendarOrders(usages, usageEnd, now);
            if (strictStart != null) {
                assertEquals(usageStart, strictStart, errMsg);
            }
            if (!allowGaps) {
                strictStart = usageEnd;
            }
            lowerBound = usageEnd;
        }
    }

    private void assertAppUsageTimesTruncated(UsageStatistics usages, Calendar strictStart, Calendar strictEnd) throws Exception {
        String errMsg = "strictStart="+Time.makeDateString(strictStart)+"; strictEnd="+Time.makeDateString(strictEnd)+";usages="+usages;
        Calendar usageStart = Time.parseCalendar(Iterables.getFirst(usages.getStatistics(), null).getStart());
        Calendar usageEnd = Time.parseCalendar(Iterables.getLast(usages.getStatistics()).getStart());
        // time zones might be different - so must convert to date
        assertEquals(usageStart.getTime(), strictStart.getTime(), "usageStart="+Time.makeDateString(usageStart)+";"+errMsg);
        assertEquals(usageEnd.getTime(), strictEnd.getTime(), errMsg);
    }
    
    public static class DynamicLocalhostMachineProvisioningLocation extends LocalhostMachineProvisioningLocation {
        private static final long serialVersionUID = 2163357613938738967L;

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

    private void waitForFuture(long futureTime) throws InterruptedException {
        long now;
        while ((now = System.currentTimeMillis()) < futureTime) {
            Thread.sleep(futureTime - now);
        }
    }

}
