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
package brooklyn.entity.proxying;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.Application;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.BasicApplication;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.basic.StartableApplication;
import brooklyn.management.ManagementContext;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.test.entity.LocalManagementContextForTests;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.exceptions.Exceptions;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

public class ApplicationBuilderOverridingTest {

    private static final long TIMEOUT_MS = 10*1000;
    
    private ManagementContext spareManagementContext;
    private Application app;
    private ExecutorService executor;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        spareManagementContext = new LocalManagementContextForTests();
        executor = Executors.newCachedThreadPool();
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() {
        if (app != null) Entities.destroyAll(app.getManagementContext());
        app = null;
        if (spareManagementContext != null) Entities.destroyAll(spareManagementContext);
        spareManagementContext = null;
    }

    @Test
    public void testUsesDefaultBasicApplicationClass() {
        app = new ApplicationBuilder() {
            @Override public void doBuild() {}
        }.manage();
        
        assertEquals(app.getEntityType().getName(), BasicApplication.class.getCanonicalName());
        assertIsProxy(app);
    }
    
    @Test
    public void testUsesSuppliedApplicationClass() {
        app = new ApplicationBuilder(EntitySpec.create(TestApplication.class)) {
            @Override public void doBuild() {}
        }.manage();
        
        assertEquals(app.getEntityType().getName(), TestApplication.class.getName());
    }

    @Test
    public void testUsesSuppliedManagementContext() {
        app = new ApplicationBuilder() {
            @Override public void doBuild() {}
        }.manage(spareManagementContext);
        
        assertEquals(app.getManagementContext(), spareManagementContext);
    }

    @Test
    public void testCreatesChildEntity() {
        final AtomicReference<TestEntity> expectedChild = new AtomicReference<TestEntity>();
        app = new ApplicationBuilder() {
            @Override public void doBuild() {
                expectedChild.set(addChild(EntitySpec.create(TestEntity.class)));
            }
        }.manage();
        
        assertIsProxy(expectedChild.get());
        assertEquals(ImmutableSet.copyOf(app.getChildren()), ImmutableSet.of(expectedChild.get()));
        assertEquals(expectedChild.get().getParent(), app);
    }

    @Test
    public void testAppHierarchyIsManaged() {
        app = new ApplicationBuilder() {
            @Override public void doBuild() {
                Entity entity = addChild(EntitySpec.create(TestEntity.class));
                assertFalse(getManagementContext().getEntityManager().isManaged(entity));
            }
        }.manage();
        
        assertIsManaged(app);
        assertIsManaged(Iterables.get(app.getChildren(), 0));
    }

    @Test(expectedExceptions=IllegalStateException.class)
    public void testRentrantCallToManageForbidden() {
        ManagementContext secondManagementContext = new LocalManagementContext();
        try {
            app = new ApplicationBuilder() {
                @Override public void doBuild() {
                    manage(spareManagementContext);
                }
            }.manage(secondManagementContext);
        } finally {
            Entities.destroyAll(secondManagementContext);
        }
    }

    @Test(expectedExceptions=IllegalStateException.class)
    public void testMultipleCallsToManageForbidden() {
        ApplicationBuilder appBuilder = new ApplicationBuilder() {
            @Override public void doBuild() {
            }
        };
        app = appBuilder.manage();
        
        appBuilder.manage(spareManagementContext);
    }

    @Test(expectedExceptions=IllegalStateException.class)
    public void testCallToConfigureAfterManageForbidden() {
        ApplicationBuilder appBuilder = new ApplicationBuilder() {
            @Override public void doBuild() {
            }
        };
        app = appBuilder.manage();
        appBuilder.configure(ImmutableMap.of());
    }

    @Test(expectedExceptions=IllegalStateException.class)
    public void testCallToSetDisplayNameAfterManageForbidden() {
        ApplicationBuilder appBuilder = new ApplicationBuilder() {
            @Override public void doBuild() {
            }
        };
        app = appBuilder.manage(spareManagementContext);
        appBuilder.appDisplayName("myname");
    }

    @Test
    public void testConcurrentCallToManageForbidden() throws Exception {
        final CountDownLatch inbuildLatch = new CountDownLatch(1);
        final CountDownLatch continueLatch = new CountDownLatch(1);
        final ApplicationBuilder builder = new ApplicationBuilder() {
            @Override public void doBuild() {
                try {
                    inbuildLatch.countDown();
                    continueLatch.await();
                } catch (InterruptedException e) {
                    throw Exceptions.propagate(e);
                }
            }
        };
        Future<StartableApplication> future = executor.submit(new Callable<StartableApplication>() {
            public StartableApplication call() {
                return builder.manage();
            }
        });
        
        inbuildLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        
        try {
            app = builder.manage(spareManagementContext);
            fail();
        } catch (IllegalStateException e) {
            // expected
        }
        
        continueLatch.countDown();
        app = future.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    private void assertIsProxy(Entity e) {
        assertFalse(e instanceof AbstractEntity, "e="+e+";e.class="+e.getClass());
        assertTrue(e instanceof EntityProxy, "e="+e+";e.class="+e.getClass());
    }
    
    private void assertIsManaged(Entity e) {
        assertTrue(((EntityInternal)e).getManagementSupport().isDeployed(), "e="+e);
    }
}
