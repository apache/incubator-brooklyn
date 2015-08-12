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
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicReference;

import javax.ws.rs.core.MultivaluedMap;

import org.apache.brooklyn.test.EntityTestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.sun.jersey.core.util.MultivaluedMapImpl;

import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.entity.drivers.BasicEntityDriverManager;
import brooklyn.entity.drivers.ReflectiveEntityDriverFactory;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.trait.Startable;

import org.apache.brooklyn.management.EntityManager;
import org.apache.brooklyn.management.Task;
import org.apache.brooklyn.rest.resources.ServerResourceTest.StopLatchEntity;
import org.apache.brooklyn.rest.testing.BrooklynRestResourceTest;

import brooklyn.test.Asserts;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.exceptions.Exceptions;

public class ServerShutdownTest extends BrooklynRestResourceTest {
    private static final Logger log = LoggerFactory.getLogger(ServerResourceTest.class);

    // Need to initialise the ManagementContext before each test as it is destroyed.
    @Override
    @BeforeClass(alwaysRun = true)
    public void setUp() throws Exception {
    }

    @Override
    @AfterClass(alwaysRun = true)
    public void tearDown() throws Exception {
    }

    @Override
    @BeforeMethod(alwaysRun = true)
    public void setUpMethod() {
        setUpJersey();
        super.setUpMethod();
    }

    @AfterMethod(alwaysRun = true)
    public void tearDownMethod() {
        tearDownJersey();
        destroyManagementContext();
    }

    @Test
    public void testShutdown() throws Exception {
        assertTrue(getManagementContext().isRunning());
        assertFalse(shutdownListener.isRequested());

        MultivaluedMap<String, String> formData = new MultivaluedMapImpl();
        formData.add("requestTimeout", "0");
        formData.add("delayForHttpReturn", "0");
        client().resource("/v1/server/shutdown").entity(formData).post();

        Asserts.succeedsEventually(new Runnable() {
            @Override
            public void run() {
                assertTrue(shutdownListener.isRequested());
            }
        });
        Asserts.succeedsEventually(new Runnable() {
            @Override public void run() {
                assertFalse(getManagementContext().isRunning());
            }});
    }

    @Test
    public void testStopAppThenShutdownAndStopAppsWaitsForFirstStop() throws InterruptedException {
        ReflectiveEntityDriverFactory f = ((BasicEntityDriverManager)getManagementContext().getEntityDriverManager()).getReflectiveDriverFactory();
        f.addClassFullNameMapping("brooklyn.entity.basic.EmptySoftwareProcessDriver", "org.apache.brooklyn.rest.resources.ServerResourceTest$EmptySoftwareProcessTestDriver");

        // Second stop on SoftwareProcess could return early, while the first stop is still in progress
        // This causes the app to shutdown prematurely, leaking machines.
        EntityManager emgr = getManagementContext().getEntityManager();
        EntitySpec<TestApplication> appSpec = EntitySpec.create(TestApplication.class);
        TestApplication app = emgr.createEntity(appSpec);
        emgr.manage(app);
        EntitySpec<StopLatchEntity> latchEntitySpec = EntitySpec.create(StopLatchEntity.class);
        final StopLatchEntity entity = app.createAndManageChild(latchEntitySpec);
        app.start(ImmutableSet.of(app.newLocalhostProvisioningLocation()));
        EntityTestUtils.assertAttributeEquals(entity, Attributes.SERVICE_STATE_ACTUAL, Lifecycle.RUNNING);

        try {
            final Task<Void> firstStop = app.invoke(Startable.STOP, ImmutableMap.<String, Object>of());
            Asserts.succeedsEventually(new Runnable() {
                @Override
                public void run() {
                    assertTrue(entity.isBlocked());
                }
            });

            final AtomicReference<Exception> shutdownError = new AtomicReference<>();
            // Can't use ExecutionContext as it will be stopped on shutdown
            Thread shutdownThread = new Thread() {
                @Override
                public void run() {
                    try {
                        MultivaluedMap<String, String> formData = new MultivaluedMapImpl();
                        formData.add("stopAppsFirst", "true");
                        formData.add("shutdownTimeout", "0");
                        formData.add("requestTimeout", "0");
                        formData.add("delayForHttpReturn", "0");
                        client().resource("/v1/server/shutdown").entity(formData).post();
                    } catch (Exception e) {
                        log.error("Shutdown request error", e);
                        shutdownError.set(e);
                        throw Exceptions.propagate(e);
                    }
                }
            };
            shutdownThread.start();

            //shutdown must wait until the first stop completes (or time out)
            Asserts.succeedsContinually(new Runnable() {
                @Override
                public void run() {
                    assertFalse(firstStop.isDone());
                    assertEquals(getManagementContext().getApplications().size(), 1);
                    assertFalse(shutdownListener.isRequested());
                }
            });

            // NOTE test is not fully deterministic. Depending on thread scheduling this will
            // execute before or after ServerResource.shutdown does the app stop loop. This
            // means that the shutdown code might not see the app at all. In any case though
            // the test must succeed.
            entity.unblock();

            Asserts.succeedsEventually(new Runnable() {
                @Override
                public void run() {
                    assertTrue(firstStop.isDone());
                    assertTrue(shutdownListener.isRequested());
                    assertFalse(getManagementContext().isRunning());
                }
            });

            shutdownThread.join();
            assertNull(shutdownError.get(), "Shutdown request error, logged above");
        } finally {
            // Be sure we always unblock entity stop even in the case of an exception.
            // In the success path the entity is already unblocked above.
            entity.unblock();
        }
    }

}
