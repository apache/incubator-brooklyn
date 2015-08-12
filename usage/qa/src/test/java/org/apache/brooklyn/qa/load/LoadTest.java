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
package org.apache.brooklyn.qa.load;

import org.apache.brooklyn.qa.load.SimulatedTheeTierApp;
import org.apache.brooklyn.test.PerformanceTestUtils;

import static org.testng.Assert.assertEquals;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.StartableApplication;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.rebind.persister.PersistMode;
import brooklyn.entity.trait.Startable;

import org.apache.brooklyn.launcher.BrooklynLauncher;
import org.apache.brooklyn.management.ManagementContext;
import org.apache.brooklyn.management.ha.HighAvailabilityMode;

import brooklyn.location.Location;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.util.os.Os;
import brooklyn.util.time.Duration;

import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

/**
 * Customers ask about the scalability of Brooklyn. These load tests investigate how many 
 * concurrent apps can be deployed and managed by a single Brooklyn management node.
 * 
 * The apps are "simulated" in that they don't create the underlying resources 
 * (we are not checking if the test machine can run 100s of app-servers simultaneously!) 
 * The install/customize/launch will instead execute ssh commands of comparable length,
 * but that just echo rather than execute the actual commands.
 * 
 * "SIMULATE_EXTERNAL_MONITORING" means that we do not poll the entities directly (over ssh, http or 
 * whatever). Instead we simulate the metrics being injected directly to be set on the entity (e.g. 
 * having been collected from a Graphite server).
 * 
 * "SKIP_SSH_ON_START" means don't do the normal install+customize+launch ssh commands. Instead, just
 * startup the entities so we can monitor their resource usage.
 */
public class LoadTest {

    // TODO Could/should issue provisioning request through REST api, rather than programmatically; 
    // and poll to detect completion.
    
    /*
     * Useful commands when investigating:
     *     LOG_FILE=usage/qa/brooklyn-camp-tests.log
     *     grep -E "OutOfMemoryError|[P|p]rovisioning time|sleeping before|CPU fraction|LoadTest using" $LOG_FILE | less
     *     grep -E "OutOfMemoryError|[P|p]rovisioning time" $LOG_FILE; grep "CPU fraction" $LOG_FILE | tail -1; grep "LoadTest using" $LOG_FILE | tail -1
     *     grep -E "OutOfMemoryError|LoadTest using" $LOG_FILE
     */
    private static final Logger LOG = LoggerFactory.getLogger(LoadTest.class);

    private File persistenceDir;
    private BrooklynLauncher launcher;
    private String webServerUrl;
    private ManagementContext managementContext;
    private ListeningExecutorService executor;
    private Future<?> cpuFuture;
    
    private Location localhost;
    
    List<Duration> provisioningTimes;


    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        // Create management node
        persistenceDir = Files.createTempDir();
        launcher = BrooklynLauncher.newInstance()
                .persistMode(PersistMode.CLEAN)
                .highAvailabilityMode(HighAvailabilityMode.MASTER)
                .persistenceDir(persistenceDir)
                .start();
        webServerUrl = launcher.getServerDetails().getWebServerUrl();
        managementContext = launcher.getServerDetails().getManagementContext();

        localhost = managementContext.getLocationRegistry().resolve("localhost");
        
        provisioningTimes = Collections.synchronizedList(Lists.<Duration>newArrayList());

        // Create executors
        executor = MoreExecutors.listeningDecorator(Executors.newCachedThreadPool());

        // Monitor utilisation (memory/CPU) while tests run
        executor.submit(new Callable<Void>() {
            public Void call() {
                try {
                    while (true) {
                        managementContext.getExecutionManager(); // force GC to be instantiated
                        String usage = ((LocalManagementContext)managementContext).getGarbageCollector().getUsageString();
                        LOG.info("LoadTest using "+usage);
                        Thread.sleep(1000);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); // exit gracefully
                } catch (Exception e) {
                    LOG.error("Error getting usage info", e);
                }
                return null;
            }});
        
        cpuFuture = PerformanceTestUtils.sampleProcessCpuTime(Duration.ONE_SECOND, "during testProvisionAppsConcurrently");

    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (cpuFuture != null) cpuFuture.cancel(true);
        if (executor != null) executor.shutdownNow();
        if (launcher != null) launcher.terminate();
        if (persistenceDir != null) Os.deleteRecursively(persistenceDir);
    }
    
    /**
     * Creates multiple apps simultaneously. 
     * 
     * Long-term target is 50 concurrent provisioning requests (which may be issued while there are
     * many existing applications under management). Until we reach that point, we can partition the
     * load across multiple (separate) brooklyn management nodes.
     *   TODO TBD: is that 50 VMs worth, or 50 apps with 4 VMs in each? 
     * 
     * TODO Does not measure the cost of jclouds for creating all the VMs/containers.
     */
    @Test(groups="Acceptance")
    public void testLocalhostProvisioningAppsConcurrently() throws Exception {
        final int NUM_CONCURRENT_APPS_PROVISIONING = 20; 
        
        List<ListenableFuture<StartableApplication>> futures = Lists.newArrayList();
        for (int i = 0; i < NUM_CONCURRENT_APPS_PROVISIONING; i++) {
            ListenableFuture<StartableApplication> future = executor.submit(newProvisionAppTask(managementContext, 
                    EntitySpec.create(StartableApplication.class, SimulatedTheeTierApp.class)
                            .configure(SimulatedTheeTierApp.SIMULATE_EXTERNAL_MONITORING, true)
                            .displayName("Simulated app "+i)));
            futures.add(future);
        }
        
        List<StartableApplication> apps = Futures.allAsList(futures).get();
        
        for (StartableApplication app : apps) {
            assertEquals(app.getAttribute(Startable.SERVICE_UP), (Boolean)true);
        }
    }
    
    /**
     * Creates many apps, to monitor resource usage etc.
     * 
     * "SIMULATE_EXTERNAL_MONITORING" means that we do not poll the entities directly (over ssh, http or 
     * whatever). Instead we simulate the metrics being injected directly to be set on the entity (e.g. 
     * having been collected from a Graphite server).
     * 
     * Long-term target is 2500 VMs under management.
     * Until we reach that point, we can partition the load across multiple (separate) brooklyn management nodes.
     */
    @Test(groups="Acceptance")
    public void testLocalhostManyApps() throws Exception {
        final int NUM_APPS = 630; // target is 2500 VMs; each blueprint has 4 (rounding up)
        final int NUM_APPS_PER_BATCH = 10;
        final int SLEEP_BETWEEN_BATCHES = 10*1000;
        final boolean SKIP_SSH_ON_START = true; // getting ssh errors otherwise!
        
        int counter = 0;
        
        for (int i = 0; i < NUM_APPS / NUM_APPS_PER_BATCH; i++) {
            List<ListenableFuture<StartableApplication>> futures = Lists.newArrayList();
            for (int j = 0; j < NUM_APPS_PER_BATCH; j++) {
                ListenableFuture<StartableApplication> future = executor.submit(newProvisionAppTask(
                        managementContext, 
                        EntitySpec.create(StartableApplication.class, SimulatedTheeTierApp.class)
                                .configure(SimulatedTheeTierApp.SIMULATE_EXTERNAL_MONITORING, true)
                                .configure(SimulatedTheeTierApp.SKIP_SSH_ON_START, SKIP_SSH_ON_START)
                                .displayName("Simulated app "+(++counter))));
                futures.add(future);
            }
            
            List<StartableApplication> apps = Futures.allAsList(futures).get();
            
            for (StartableApplication app : apps) {
                assertEquals(app.getAttribute(Startable.SERVICE_UP), (Boolean)true);
            }

            synchronized (provisioningTimes) {
                LOG.info("cycle="+i+"; numApps="+counter+": provisioning times: "+provisioningTimes);
                provisioningTimes.clear();
            }

            LOG.info("cycle="+i+"; numApps="+counter+": sleeping before next batch of apps");
            Thread.sleep(SLEEP_BETWEEN_BATCHES);
        }
    }
    
    protected <T extends StartableApplication> Callable<T> newProvisionAppTask(final ManagementContext managementContext, final EntitySpec<T> entitySpec) {
        return new Callable<T>() {
            public T call() {
                Stopwatch stopwatch = Stopwatch.createStarted();
                T app = managementContext.getEntityManager().createEntity(entitySpec);
                Entities.startManagement(app, managementContext);
                app.start(ImmutableList.of(localhost));
                Duration duration = Duration.of(stopwatch.elapsed(TimeUnit.MILLISECONDS), TimeUnit.MILLISECONDS);
                LOG.info("Provisioning time: "+duration);
                provisioningTimes.add(duration);

                return app;
            }
        };
    }
}
