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
package brooklyn.entity.rebind;

import static org.testng.Assert.assertEquals;

import java.net.URL;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;

import org.apache.brooklyn.management.Task;
import org.apache.brooklyn.management.ha.HighAvailabilityMode;
import org.apache.brooklyn.test.EntityTestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.Feed;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.event.AttributeSensor;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.http.BetterMockWebServer;
import brooklyn.util.repeat.Repeater;
import brooklyn.util.task.BasicExecutionManager;
import brooklyn.util.time.Duration;

import com.google.common.collect.Iterables;
import com.google.mockwebserver.MockResponse;

public class RebindFeedWithHaTest extends RebindTestFixtureWithApp {

    private static final Logger log = LoggerFactory.getLogger(RebindFeedWithHaTest.class);
    
    final static AttributeSensor<String> SENSOR_STRING = RebindFeedTest.SENSOR_STRING;
    final static AttributeSensor<Integer> SENSOR_INT = RebindFeedTest.SENSOR_INT;

    private BetterMockWebServer server;
    private URL baseUrl;
    
    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        server = BetterMockWebServer.newInstanceLocalhost();
        for (int i = 0; i < 100; i++) {
            server.enqueue(new MockResponse().setResponseCode(200).addHeader("content-type: application/json").setBody("{\"foo\":\"myfoo\"}"));
        }
        server.play();
        baseUrl = server.getUrl("/");
    }

    @AfterMethod(alwaysRun=true)
    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        if (server != null) server.shutdown();
    }

    @Override
    protected TestApplication createApp() {
        origManagementContext.getHighAvailabilityManager().changeMode(HighAvailabilityMode.MASTER);
        return super.createApp();
    }

    @Test
    public void testHttpFeedCleansUpAfterHaDisabledAndRunsAtFailover() throws Exception {
        TestEntity origEntity = origApp.createAndManageChild(EntitySpec.create(TestEntity.class).impl(RebindFeedTest.MyEntityWithHttpFeedImpl.class)
                .configure(RebindFeedTest.MyEntityWithHttpFeedImpl.BASE_URL, baseUrl));
        EntityTestUtils.assertAttributeEqualsEventually(origEntity, SENSOR_INT, (Integer)200);
        EntityTestUtils.assertAttributeEqualsEventually(origEntity, SENSOR_STRING, "{\"foo\":\"myfoo\"}");
        assertEquals(origEntity.feeds().getFeeds().size(), 1);
        origManagementContext.getRebindManager().forcePersistNow();

        List<Task<?>> tasksBefore = ((BasicExecutionManager)origManagementContext.getExecutionManager()).getAllTasks();
        log.info("tasks before disabling HA, "+tasksBefore.size()+": "+tasksBefore);
        Assert.assertFalse(tasksBefore.isEmpty());
        origManagementContext.getHighAvailabilityManager().changeMode(HighAvailabilityMode.DISABLED);
        origApp = null;
        
        Repeater.create().every(Duration.millis(20)).backoffTo(Duration.ONE_SECOND).limitTimeTo(Duration.THIRTY_SECONDS).until(new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                origManagementContext.getGarbageCollector().gcIteration();
                List<Task<?>> tasksAfter = ((BasicExecutionManager)origManagementContext.getExecutionManager()).getAllTasks();
                log.info("tasks after disabling HA, "+tasksAfter.size()+": "+tasksAfter);
                return tasksAfter.isEmpty();
            }
        }).runRequiringTrue();
        
        newManagementContext = createNewManagementContext();
        newApp = (TestApplication) RebindTestUtils.rebind((LocalManagementContext)newManagementContext, classLoader);

        TestEntity newEntity = (TestEntity) Iterables.getOnlyElement(newApp.getChildren());
        
        Collection<Feed> newFeeds = newEntity.feeds().getFeeds();
        assertEquals(newFeeds.size(), 1);
        
        // Expect the feed to still be polling
        newEntity.setAttribute(SENSOR_INT, null);
        newEntity.setAttribute(SENSOR_STRING, null);
        EntityTestUtils.assertAttributeEqualsEventually(newEntity, SENSOR_INT, (Integer)200);
        EntityTestUtils.assertAttributeEqualsEventually(newEntity, SENSOR_STRING, "{\"foo\":\"myfoo\"}");
    }

    @Test(groups="Integration", invocationCount=50)
    public void testHttpFeedCleansUpAfterHaDisabledAndRunsAtFailoverManyTimes() throws Exception {
        testHttpFeedCleansUpAfterHaDisabledAndRunsAtFailover();
    }
    
}
