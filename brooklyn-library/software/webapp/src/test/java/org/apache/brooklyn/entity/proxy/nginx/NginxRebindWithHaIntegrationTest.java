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
package org.apache.brooklyn.entity.proxy.nginx;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.LocationSpec;
import org.apache.brooklyn.api.mgmt.Task;
import org.apache.brooklyn.api.mgmt.ha.HighAvailabilityMode;
import org.apache.brooklyn.api.sensor.Feed;
import org.apache.brooklyn.core.BrooklynFeatureEnablement;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.EntityInternal;
import org.apache.brooklyn.core.entity.lifecycle.Lifecycle;
import org.apache.brooklyn.core.mgmt.internal.LocalManagementContext;
import org.apache.brooklyn.core.mgmt.rebind.RebindTestFixtureWithApp;
import org.apache.brooklyn.core.mgmt.rebind.RebindTestUtils;
import org.apache.brooklyn.core.test.entity.TestApplication;
import org.apache.brooklyn.entity.group.DynamicCluster;
import org.apache.brooklyn.entity.webapp.tomcat.TomcatServer;
import org.apache.brooklyn.location.localhost.LocalhostMachineProvisioningLocation;
import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.apache.brooklyn.location.ssh.SshMachineLocationReuseIntegrationTest.RecordingSshjTool;
import org.apache.brooklyn.test.EntityTestUtils;
import org.apache.brooklyn.test.WebAppMonitor;
import org.apache.brooklyn.test.support.TestResourceUnavailableException;
import org.apache.brooklyn.util.core.task.BasicExecutionManager;
import org.apache.brooklyn.util.net.Networking;
import org.apache.brooklyn.util.repeat.Repeater;
import org.apache.brooklyn.util.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

/**
 * Test the operation of the {@link NginxController} class.
 */
public class NginxRebindWithHaIntegrationTest extends RebindTestFixtureWithApp {

    private static final Logger LOG = LoggerFactory.getLogger(NginxRebindWithHaIntegrationTest.class);

    private List<WebAppMonitor> webAppMonitors = new CopyOnWriteArrayList<WebAppMonitor>();
    private ExecutorService executor;
    private LocalhostMachineProvisioningLocation loc;

    private Boolean feedRegistration;
    
    @Override
    protected boolean useLiveManagementContext() {
        // For Aled, the test failed without own ~/.brooklyn/brooklyn.properties.
        // Suspect that was caused by local environment, with custom brooklyn.ssh.config.scriptHeader
        // to set things like correct Java on path.
        return true;
    }

    public String getTestWar() {
        TestResourceUnavailableException.throwIfResourceUnavailable(getClass(), "/hello-world.war");
        return "classpath://hello-world.war";
    }

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        super.setUp();
        loc = origManagementContext.getLocationManager().createLocation(LocationSpec.create(LocalhostMachineProvisioningLocation.class)
            .configure("address", Networking.getLocalHost())
            .configure(SshMachineLocation.SSH_TOOL_CLASS, RecordingSshjTool.class.getName()));
        executor = Executors.newCachedThreadPool();
        
        feedRegistration = BrooklynFeatureEnablement.isEnabled(BrooklynFeatureEnablement.FEATURE_FEED_REGISTRATION_PROPERTY);
        BrooklynFeatureEnablement.setEnablement(BrooklynFeatureEnablement.FEATURE_FEED_REGISTRATION_PROPERTY, true);
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        try {
            if (feedRegistration!=null)
                BrooklynFeatureEnablement.setEnablement(BrooklynFeatureEnablement.FEATURE_FEED_REGISTRATION_PROPERTY, feedRegistration);

            for (WebAppMonitor monitor : webAppMonitors) {
                monitor.terminate();
            }
            webAppMonitors.clear();
            if (executor != null) executor.shutdownNow();
            super.tearDown();
        } finally {
            RecordingSshjTool.reset();
        }
    }
    
    @Override
    protected TestApplication createApp() {
        origManagementContext.getHighAvailabilityManager().changeMode(HighAvailabilityMode.MASTER);
        return super.createApp();
    }

    @Test(groups = "Integration")
    public void testChangeModeFailureStopsTasksButHappyUponResumption() throws Exception {
        DynamicCluster origServerPool = origApp.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure(DynamicCluster.MEMBER_SPEC, EntitySpec.create(TomcatServer.class).configure("war", getTestWar()))
                .configure("initialSize", 1));
        
        NginxController origNginx = origApp.createAndManageChild(EntitySpec.create(NginxController.class)
                .configure("serverPool", origServerPool)
                .configure("domain", "localhost"));
        
        origApp.start(ImmutableList.of(loc));
        Assert.assertTrue(RecordingSshjTool.connectionCount.get()>0);

        Collection<Feed> origFeeds = ((EntityInternal)origNginx).feeds().getFeeds();
        LOG.info("feeds before rebind are: "+origFeeds);
        Assert.assertTrue(origFeeds.size() >= 1);

        origManagementContext.getRebindManager().forcePersistNow();

        List<Task<?>> tasksBefore = ((BasicExecutionManager)origManagementContext.getExecutionManager()).getAllTasks();
        LOG.info("tasks before disabling HA, "+tasksBefore.size()+": "+tasksBefore);
        Assert.assertFalse(tasksBefore.isEmpty());
        origManagementContext.getHighAvailabilityManager().changeMode(HighAvailabilityMode.DISABLED);
        origApp = null;
        
        Repeater.create().every(Duration.millis(20)).backoffTo(Duration.ONE_SECOND).limitTimeTo(Duration.THIRTY_SECONDS).until(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                origManagementContext.getGarbageCollector().gcIteration();
                List<Task<?>> tasksAfter = ((BasicExecutionManager)origManagementContext.getExecutionManager()).getAllTasks();
                LOG.info("tasks after disabling HA, "+tasksAfter.size()+": "+tasksAfter);
                return tasksAfter.isEmpty();
            }
        }).runRequiringTrue();
        
        // disable SSH to localhost to ensure we don't try to ssh while rebinding
        
        RecordingSshjTool.forbidden.set(true);
        newManagementContext = createNewManagementContext();
        newApp = (TestApplication) RebindTestUtils.rebind((LocalManagementContext)newManagementContext, classLoader);

        NginxController newNginx = Iterables.getOnlyElement(Entities.descendants(newApp, NginxController.class));
        
        Collection<Feed> newFeeds = ((EntityInternal)newNginx).feeds().getFeeds();
        LOG.info("feeds after rebind are: "+newFeeds);
        Assert.assertTrue(newFeeds.size() >= 1);
        
        // eventually goes on fire, because we disabled ssh
        EntityTestUtils.assertAttributeEqualsEventually(newNginx, Attributes.SERVICE_STATE_ACTUAL, Lifecycle.ON_FIRE);
        
        // re-enable SSH and it should right itself
        RecordingSshjTool.forbidden.set(false);
        EntityTestUtils.assertAttributeEqualsEventually(newNginx, Attributes.SERVICE_STATE_ACTUAL, Lifecycle.RUNNING);
    }

}
