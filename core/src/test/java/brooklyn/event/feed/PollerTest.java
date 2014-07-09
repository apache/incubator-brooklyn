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
package brooklyn.event.feed;

import static org.testng.Assert.assertTrue;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.BrooklynAppUnitTestSupport;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.test.Asserts;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.task.DynamicTasks;
import brooklyn.util.time.Duration;

public class PollerTest extends BrooklynAppUnitTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(PollerTest.class);

    private TestEntity entity;
    private Poller<Integer> poller;
    
    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        entity = app.createAndManageChild(EntitySpec.create(TestEntity.class));
        poller = new Poller<Integer>(entity, false);
    }
    
    @AfterMethod(alwaysRun=true)
    @Override
    public void tearDown() throws Exception {
        if (poller != null) poller.stop();
        super.tearDown();
    }
    
    @Test(groups={"Integration", "WIP"}) // because takes > 1 second
    public void testPollingSubTaskFailsOnceKeepsGoing() throws Exception {
        final AtomicInteger counter = new AtomicInteger();
        poller.scheduleAtFixedRate(
                new Callable<Integer>() {
                    @Override public Integer call() throws Exception {
                        int result = counter.incrementAndGet();
                        if (result % 2 == 0) {
                            DynamicTasks.queue("in-poll", new Runnable() {
                                public void run() {
                                    throw new IllegalStateException("Simulating error in sub-task for poll");
                                }});
                        }
                        return result;
                    }
                },
                new PollHandler<Integer>() {
                    @Override public boolean checkSuccess(Integer val) {
                        return true;
                    }
                    @Override public void onSuccess(Integer val) {
                        
                    }
                    @Override public void onFailure(Integer val) {
                    }
                    @Override
                    public void onException(Exception exception) {
                        LOG.info("Exception in test poller", exception);
                    }
                    @Override public String getDescription() {
                        return "mypollhandler";
                    }
                }, 
                new Duration(10, TimeUnit.MILLISECONDS));
        poller.start();
        
        Asserts.succeedsContinually(MutableMap.of("timeout", 2*1000, "period", 500), new Runnable() {
            int oldCounter = -1;
            @Override public void run() {
                assertTrue(counter.get() > oldCounter);
                oldCounter = counter.get();
            }
        });
    }
}
