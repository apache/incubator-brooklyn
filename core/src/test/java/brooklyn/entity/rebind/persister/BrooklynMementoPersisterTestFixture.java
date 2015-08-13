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
package brooklyn.entity.rebind.persister;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.proxying.EntitySpec;
import org.apache.brooklyn.api.entity.rebind.PersistenceExceptionHandler;
import org.apache.brooklyn.api.entity.rebind.RebindManager.RebindFailureMode;
import org.apache.brooklyn.management.ManagementContext;
import org.apache.brooklyn.mementos.BrooklynMemento;
import org.apache.brooklyn.mementos.BrooklynMementoPersister;
import org.apache.brooklyn.mementos.BrooklynMementoRawData;
import org.apache.brooklyn.policy.Enricher;
import org.apache.brooklyn.policy.Policy;
import org.apache.brooklyn.test.entity.TestApplication;
import org.apache.brooklyn.test.entity.TestEntity;
import org.testng.SkipException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.enricher.Enrichers;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.rebind.PersistenceExceptionHandlerImpl;
import brooklyn.entity.rebind.RebindContextImpl;
import brooklyn.entity.rebind.RebindTestUtils;
import brooklyn.entity.rebind.RecordingRebindExceptionHandler;
import org.apache.brooklyn.location.Location;
import org.apache.brooklyn.location.LocationSpec;
import org.apache.brooklyn.location.basic.SshMachineLocation;
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
        if (persister != null) persister.stop(false);
        if (objectStore!=null) objectStore.deleteCompletely();
        persister = null;
    }

    protected BrooklynMemento loadMemento() throws Exception {
        RebindTestUtils.waitForPersisted(localManagementContext);
        
        RecordingRebindExceptionHandler failFast = new RecordingRebindExceptionHandler(RebindFailureMode.FAIL_FAST, RebindFailureMode.FAIL_FAST);
        RebindContextImpl rebindContext = new RebindContextImpl(localManagementContext, failFast, classLoader);
        // here we force these two to be reegistered in order to resolve the enricher and policy
        // (normally rebind will do that after loading the manifests, but in this test we are just looking at persistence/manifest)
        rebindContext.registerEntity(app.getId(), app);
        rebindContext.registerEntity(entity.getId(), entity);
        
        BrooklynMemento reloadedMemento = persister.loadMemento(null, rebindContext.lookup(), failFast);
        return reloadedMemento;
    }
    
    protected BrooklynMementoRawData loadRawMemento(BrooklynMementoPersisterToObjectStore persister) throws Exception {
        RebindTestUtils.waitForPersisted(localManagementContext);
        
        RecordingRebindExceptionHandler failFast = new RecordingRebindExceptionHandler(RebindFailureMode.FAIL_FAST, RebindFailureMode.FAIL_FAST);
        BrooklynMementoRawData rawMemento = persister.loadMementoRawData(failFast);
        return rawMemento;
    }
    
    @Test
    public void testCheckPointAndLoadMemento() throws Exception {
        BrooklynMemento reloadedMemento = loadMemento();
        
        assertNotNull(reloadedMemento);
        assertTrue(Iterables.contains(reloadedMemento.getEntityIds(), entity.getId()));
        assertEquals(Iterables.getOnlyElement(reloadedMemento.getLocationIds()), location.getId());
        assertEquals(Iterables.getOnlyElement(reloadedMemento.getPolicyIds()), policy.getId());
        assertTrue(reloadedMemento.getEnricherIds().contains(enricher.getId()));
    }

    @Test
    public void testDeleteAndLoadMemento() throws Exception {
        Entities.destroy(entity);

        BrooklynMemento reloadedMemento = loadMemento();
        
        assertNotNull(reloadedMemento);
        assertFalse(Iterables.contains(reloadedMemento.getEntityIds(), entity.getId()));
        assertEquals(Iterables.getOnlyElement(reloadedMemento.getLocationIds()), location.getId());
    }
    
    @Test
    public void testLoadAndCheckpointRawMemento() throws Exception {
        if (persister instanceof BrooklynMementoPersisterToObjectStore) {
            // Test loading
            BrooklynMementoRawData rawMemento = loadRawMemento((BrooklynMementoPersisterToObjectStore)persister);
            assertNotNull(rawMemento);
            assertTrue(Iterables.contains(rawMemento.getEntities().keySet(), entity.getId()));
            assertEquals(Iterables.getOnlyElement(rawMemento.getLocations().keySet()), location.getId());
            assertEquals(Iterables.getOnlyElement(rawMemento.getPolicies().keySet()), policy.getId());
            assertTrue(rawMemento.getEnrichers().keySet().contains(enricher.getId()));
            
            // And test persisting
            PersistenceExceptionHandler exceptionHandler = PersistenceExceptionHandlerImpl.builder().build();
            ((BrooklynMementoPersisterToObjectStore) persister).checkpoint(rawMemento, exceptionHandler);
        } else {
            throw new SkipException("Persister "+persister+" not a "+BrooklynMementoPersisterToObjectStore.class.getSimpleName());
        }
    }
}
