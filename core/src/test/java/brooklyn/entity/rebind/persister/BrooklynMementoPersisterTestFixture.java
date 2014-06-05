package brooklyn.entity.rebind.persister;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.enricher.Enrichers;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.rebind.RebindContextImpl;
import brooklyn.entity.rebind.RebindContextLookupContext;
import brooklyn.entity.rebind.RebindManager.RebindFailureMode;
import brooklyn.entity.rebind.RebindTestUtils;
import brooklyn.entity.rebind.RecordingRebindExceptionHandler;
import brooklyn.location.Location;
import brooklyn.location.LocationSpec;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.management.ManagementContext;
import brooklyn.mementos.BrooklynMemento;
import brooklyn.mementos.BrooklynMementoPersister;
import brooklyn.policy.Enricher;
import brooklyn.policy.Policy;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;
import brooklyn.test.policy.TestPolicy;

import com.google.common.collect.Iterables;

/**
 * @author Andrea Turli
 */
public abstract class BrooklynMementoPersisterTestFixture {

    protected ClassLoader classLoader = getClass().getClassLoader();
    protected BrooklynMementoPersister persister;
    protected TestApplication app;
    protected Entity entity;
    protected Location location;
    protected ManagementContext localManagementContext;
    protected Enricher enricher;
    protected Policy policy;
    
    protected PersistenceObjectStore objectStore;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        localManagementContext = newPersistingManagementContext();
        if (persister==null) {
            persister = localManagementContext.getRebindManager().getPersister();
        }
        if (objectStore==null && persister instanceof BrooklynMementoPersisterToObjectStore) {
            objectStore = ((BrooklynMementoPersisterToObjectStore)persister).getObjectStore();
        }
        app = ApplicationBuilder.newManagedApp(EntitySpec.create(TestApplication.class), localManagementContext);
        location =  localManagementContext.getLocationManager()
            .createLocation(LocationSpec.create(SshMachineLocation.class)
                .configure("address", "localhost"));
        entity = app.createAndManageChild(EntitySpec.create(TestEntity.class).location(location));
        enricher = app.addEnricher(Enrichers.builder().propagatingAll().from(entity).build());
        app.addPolicy(policy = new TestPolicy());
    }

    protected abstract ManagementContext newPersistingManagementContext();

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (localManagementContext != null) Entities.destroyAll(localManagementContext);
        if (app != null) Entities.destroyAll(app.getManagementContext());
        if (persister != null) persister.stop();
        if (objectStore!=null) objectStore.deleteCompletely();
        persister = null;
    }

    protected BrooklynMemento loadMemento() throws IOException, InterruptedException, TimeoutException {
        RebindTestUtils.waitForPersisted(localManagementContext);
        
        RecordingRebindExceptionHandler failFast = new RecordingRebindExceptionHandler(RebindFailureMode.FAIL_FAST, RebindFailureMode.FAIL_FAST);
        RebindContextLookupContext lookupContext = new RebindContextLookupContext(new RebindContextImpl(classLoader), failFast);
        
        BrooklynMemento reloadedMemento = persister.loadMemento(lookupContext, failFast);
        return reloadedMemento;
    }
    
    @Test
    public void testCheckPointAndLoadMementoUsingFileBasedObjectStore() throws IOException, TimeoutException, InterruptedException {
        BrooklynMemento reloadedMemento = loadMemento();
        
        assertNotNull(reloadedMemento);
        assertTrue(Iterables.contains(reloadedMemento.getEntityIds(), entity.getId()));
        assertEquals(Iterables.getOnlyElement(reloadedMemento.getLocationIds()), location.getId());
        // FIXME when policies and enrichers working, re-enable these.
//        assertEquals(Iterables.getOnlyElement(reloadedMemento.getPolicyIds()), policy.getId());
//        assertEquals(Iterables.getOnlyElement(reloadedMemento.getEnricherIds()), enricher.getId());
    }

    @Test(dependsOnMethods = "testCheckPointAndLoadMementoUsingFileBasedObjectStore")
    public void testDeltaAndLoadMementoUsingFileBasedObjectStore() throws TimeoutException, InterruptedException, IOException {
        Entities.destroy(entity);

        BrooklynMemento reloadedMemento = loadMemento();
        
        assertNotNull(reloadedMemento);
        assertFalse(Iterables.contains(reloadedMemento.getEntityIds(), entity.getId()));
        assertEquals(Iterables.getOnlyElement(reloadedMemento.getLocationIds()), location.getId());
    }
}
