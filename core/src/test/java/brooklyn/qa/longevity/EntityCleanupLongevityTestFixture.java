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
package brooklyn.qa.longevity;

import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.internal.storage.BrooklynStorage;
import brooklyn.internal.storage.DataGrid;
import brooklyn.internal.storage.impl.BrooklynStorageImpl;
import brooklyn.location.LocationSpec;
import brooklyn.location.basic.SimulatedLocation;
import brooklyn.management.ManagementContext;
import brooklyn.management.internal.AbstractManagementContext;
import brooklyn.management.internal.ManagementContextInternal;
import brooklyn.test.entity.LocalManagementContextForTests;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.task.BasicExecutionManager;
import brooklyn.util.task.TaskScheduler;
import brooklyn.util.text.Strings;
import brooklyn.util.time.Time;

import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;

public abstract class EntityCleanupLongevityTestFixture {

    private static final Logger LOG = LoggerFactory.getLogger(EntityCleanupLongevityTestFixture.class);

    protected ManagementContext managementContext;
    protected SimulatedLocation loc;
    protected TestApplication app;

    // since GC is not definitive (would that it were!)
    final static long MEMORY_MARGIN_OF_ERROR = 10*1024*1024;

    /** Iterations might currently leave behind:
     * <li> brooklyn.management.usage.ApplicationUsage$ApplicationEvent (one each for started/stopped/destroyed, per app)
     * <li> SingleThreadedScheduler (subscription delivery tag for the entity)
     * <p>
     * Set at 2kb per iter for now. We'd like to drop this to 0 of course!
     */
    final static long ACCEPTABLE_LEAK_PER_ITERATION = 2*1024;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() {
        managementContext = new LocalManagementContextForTests();
        
        // do this to ensure GC is initialized
        managementContext.getExecutionManager();
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() {
        if (managementContext != null) Entities.destroyAll(managementContext);
    }
    
    protected abstract int numIterations();
    protected abstract boolean checkMemoryLeaks();
    
    protected void doTestManyTimesAndAssertNoMemoryLeak(String testName, Runnable iterationBody) {
        int iterations = numIterations();
        Stopwatch timer = Stopwatch.createStarted();
        long last = timer.elapsed(TimeUnit.MILLISECONDS);
        
        long memUsedNearStart = -1;
        
        for (int i = 0; i < iterations; i++) {
            if (i % 100 == 0 || i<5) {
                long now = timer.elapsed(TimeUnit.MILLISECONDS);
                System.gc(); System.gc();
                String msg = testName+" iteration " + i + " at " + Time.makeTimeStringRounded(now) + " (delta "+Time.makeTimeStringRounded(now-last)+"), using "+
                    ((AbstractManagementContext)managementContext).getGarbageCollector().getUsageString();
                LOG.info(msg);
                if (i>=100 && memUsedNearStart<0) {
                    // set this the first time we've run 100 times (let that create a baseline with classes loaded etc)
                    memUsedNearStart = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
                }
                last = timer.elapsed(TimeUnit.MILLISECONDS);
            }
            iterationBody.run();
        }
        
        BrooklynStorage storage = ((ManagementContextInternal)managementContext).getStorage();
        Assert.assertTrue(storage.isMostlyEmpty(), "Not empty storage: "+storage);
        
        DataGrid dg = ((BrooklynStorageImpl)storage).getDataGrid();
        Set<String> keys = dg.getKeys();
        for (String key: keys) {
            ConcurrentMap<Object, Object> v = dg.getMap(key);
            if (v.isEmpty()) continue;
            // TODO currently we remember ApplicationUsage
            if (key.contains("usage-application")) {
                Assert.assertTrue(v.size() <= iterations, "Too many usage-application entries: "+v.size());
                continue;
            }
            
            Assert.fail("Non-empty key in datagrid: "+key+" ("+v+")");
        }

        ConcurrentMap<Object, TaskScheduler> schedulers = ((BasicExecutionManager)managementContext.getExecutionManager()).getSchedulerByTag();
        // TODO would like to assert this
//        Assert.assertTrue( schedulers.isEmpty(), "Not empty schedulers: "+schedulers);
        // but weaker form for now
        Assert.assertTrue( schedulers.size() <= iterations, "Not empty schedulers: "+schedulers);
        
        // memory leak detection only applies to subclasses who run lots of iterations
        if (checkMemoryLeaks())
            assertNoMemoryLeak(memUsedNearStart);
    }

    protected void assertNoMemoryLeak(long memUsedPreviously) {
        System.gc(); System.gc();
        long memUsedAfter = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        long memChange = memUsedAfter - memUsedPreviously;
        Assert.assertTrue(memChange < numIterations()*ACCEPTABLE_LEAK_PER_ITERATION + MEMORY_MARGIN_OF_ERROR, "Leaked too much memory: "+Strings.makeJavaSizeString(memChange));
    }
    
    protected void doTestStartAppThenThrowAway(String testName, final boolean stop) {
        doTestManyTimesAndAssertNoMemoryLeak(testName, new Runnable() {
            public void run() {
                loc = managementContext.getLocationManager().createLocation(LocationSpec.create(SimulatedLocation.class));
                app = newApp();
                app.start(ImmutableList.of(loc));

                if (stop)
                    app.stop();
                else
                    Entities.unmanage(app);
                managementContext.getLocationManager().unmanage(loc);
            }
        });
    }

    protected TestApplication newApp() {
        final TestApplication result = ApplicationBuilder.newManagedApp(TestApplication.class, managementContext);
        TestEntity entity = result.createAndManageChild(EntitySpec.create(TestEntity.class));
        result.subscribe(entity, TestEntity.NAME, new SensorEventListener<String>() {
            @Override public void onEvent(SensorEvent<String> event) {
                result.setAttribute(TestApplication.MY_ATTRIBUTE, event.getValue());
            }});
        entity.setAttribute(TestEntity.NAME, "myname");
        return result;
    }
}
