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
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.exceptions.Exceptions;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

public class ApplicationBuilderOverridingTest {

    private static final long TIMEOUT_MS = 10*1000;
    
    private Application app;
    private ExecutorService executor;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        executor = Executors.newCachedThreadPool();
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() {
        if (app != null) Entities.destroy(app);
        app = null;
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
        ManagementContext managementContext = Entities.newManagementContext();
        app = new ApplicationBuilder() {
            @Override public void doBuild() {}
        }.manage(managementContext);
        
        assertEquals(app.getManagementContext(), managementContext);
    }

    @Test
    public void testCreatesChildEntity() {
        final AtomicReference<TestEntity> expectedChild = new AtomicReference<TestEntity>();
        app = new ApplicationBuilder() {
            @Override public void doBuild() {
                expectedChild.set(addChild(TestEntity.Spec.newInstance()));
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
                Entity entity = addChild(TestEntity.Spec.newInstance());
                assertFalse(getManagementContext().getEntityManager().isManaged(entity));
            }
        }.manage();
        
        assertIsManaged(app);
        assertIsManaged(Iterables.get(app.getChildren(), 0));
    }

    @Test(expectedExceptions=IllegalStateException.class)
    public void testRentrantCallToManageForbidden() {
        app = new ApplicationBuilder() {
            @Override public void doBuild() {
                manage();
            }
        }.manage();
    }

    @Test(expectedExceptions=IllegalStateException.class)
    public void testMultipleCallsToManageForbidden() {
        ApplicationBuilder appBuilder = new ApplicationBuilder() {
            @Override public void doBuild() {
            }
        };
        appBuilder.manage();
        appBuilder.manage();
    }

    @Test(expectedExceptions=IllegalStateException.class)
    public void testCallToConfigureAfterManageForbidden() {
        ApplicationBuilder appBuilder = new ApplicationBuilder() {
            @Override public void doBuild() {
            }
        };
        appBuilder.manage();
        appBuilder.configure(ImmutableMap.of());
    }

    @Test(expectedExceptions=IllegalStateException.class)
    public void testCallToSetDisplayNameAfterManageForbidden() {
        ApplicationBuilder appBuilder = new ApplicationBuilder() {
            @Override public void doBuild() {
            }
        };
        appBuilder.manage();
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
            builder.manage();
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
