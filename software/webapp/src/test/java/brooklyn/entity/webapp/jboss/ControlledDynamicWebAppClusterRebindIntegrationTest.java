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
package brooklyn.entity.webapp.jboss;

import static brooklyn.test.EntityTestUtils.assertAttributeEqualsEventually;
import static brooklyn.test.HttpTestUtils.assertHttpStatusCodeEquals;
import static brooklyn.test.HttpTestUtils.assertHttpStatusCodeEventuallyEquals;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.testng.Assert.assertEquals;

import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.proxy.nginx.NginxController;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.rebind.RebindTestUtils;
import brooklyn.entity.webapp.ControlledDynamicWebAppCluster;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.test.WebAppMonitor;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.internal.TimeExtras;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.io.Files;

public class ControlledDynamicWebAppClusterRebindIntegrationTest {
    private static final Logger LOG = LoggerFactory.getLogger(ControlledDynamicWebAppClusterRebindIntegrationTest.class);
    
    static { TimeExtras.init(); }

    private URL warUrl;
    private LocalhostMachineProvisioningLocation localhostProvisioningLocation;
    private TestApplication origApp;
    private TestApplication newApp;
    private List<WebAppMonitor> webAppMonitors = new CopyOnWriteArrayList<WebAppMonitor>();
	private ExecutorService executor;
    
    private ClassLoader classLoader = getClass().getClassLoader();
    private LocalManagementContext origManagementContext;
    private File mementoDir;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() {
    	String warPath = "hello-world.war";
        warUrl = checkNotNull(getClass().getClassLoader().getResource(warPath), "warUrl");
        executor = Executors.newCachedThreadPool();

        mementoDir = Files.createTempDir();
        LOG.info("Test persisting to "+mementoDir);
        origManagementContext = RebindTestUtils.newPersistingManagementContext(mementoDir, classLoader);

    	localhostProvisioningLocation = new LocalhostMachineProvisioningLocation();
        origApp = ApplicationBuilder.newManagedApp(TestApplication.class, origManagementContext);
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        for (WebAppMonitor monitor : webAppMonitors) {
        	monitor.terminate();
        }
        if (executor != null) executor.shutdownNow();
        if (newApp != null) Entities.destroyAll(newApp.getManagementContext());
        if (origApp != null) Entities.destroyAll(origApp.getManagementContext());
        if (mementoDir != null) RebindTestUtils.deleteMementoDir(mementoDir);
    }

    private TestApplication rebind() throws Exception {
        RebindTestUtils.waitForPersisted(origApp);
        
        // Stop the old management context, so original nginx won't interfere
        origManagementContext.terminate();
        
        return (TestApplication) RebindTestUtils.rebind(mementoDir, getClass().getClassLoader());
    }

    private WebAppMonitor newWebAppMonitor(String url) {
    	WebAppMonitor monitor = new WebAppMonitor(url)
//    			.delayMillis(0)
		    	.logFailures(LOG);
    	webAppMonitors.add(monitor);
    	executor.execute(monitor);
    	return monitor;
    }
    
    @Test(groups = {"Integration"})
    public void testRebindsToRunningCluster() throws Exception {
        NginxController origNginx = origApp.createAndManageChild(EntitySpec.create(NginxController.class).configure("domain", "localhost"));

        origApp.createAndManageChild(EntitySpec.create(ControlledDynamicWebAppCluster.class)
    			.configure("memberSpec", EntitySpec.create(JBoss7Server.class).configure("war", warUrl.toString()))
    			.configure("initialSize", 1)
		        .configure("controller", origNginx));
    	
        origApp.start(ImmutableList.of(localhostProvisioningLocation));
        String rootUrl = origNginx.getAttribute(JBoss7Server.ROOT_URL);
        
        assertHttpStatusCodeEventuallyEquals(rootUrl, 200);
        WebAppMonitor monitor = newWebAppMonitor(rootUrl);
        
        // Rebind
        newApp = rebind();
        NginxController newNginx = (NginxController) Iterables.find(newApp.getChildren(), Predicates.instanceOf(NginxController.class));
        ControlledDynamicWebAppCluster newCluster = (ControlledDynamicWebAppCluster) Iterables.find(newApp.getChildren(), Predicates.instanceOf(ControlledDynamicWebAppCluster.class));

        assertAttributeEqualsEventually(newNginx, SoftwareProcess.SERVICE_UP, true);
        assertHttpStatusCodeEquals(rootUrl, 200);

        // Confirm the cluster is usable: we can scale-up
        assertEquals(newCluster.getCurrentSize(), (Integer)1);
        newCluster.resize(2);
        
        Iterable<Entity> newJbosses = Iterables.filter(newCluster.getCluster().getChildren(), Predicates.instanceOf(JBoss7Server.class));
        assertEquals(Iterables.size(newJbosses), 2);
        
        Thread.sleep(1000);
        for (int i = 0; i < 10; i++) {
            assertHttpStatusCodeEquals(rootUrl, 200);
        }
        
        // Ensure while doing all of this the original jboss server remained reachable
        assertEquals(monitor.getFailures(), 0);
        
        // Ensure cluster is usable: we can scale back to stop the original jboss server
        newCluster.resize(0);
        
        assertHttpStatusCodeEventuallyEquals(rootUrl, 404);
    }
}
