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
package brooklyn.entity.rebind.transformer;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.io.File;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.rebind.PersistenceExceptionHandler;
import brooklyn.entity.rebind.PersistenceExceptionHandlerImpl;
import brooklyn.entity.rebind.RebindExceptionHandler;
import brooklyn.entity.rebind.RebindOptions;
import brooklyn.entity.rebind.RebindManager.RebindFailureMode;
import brooklyn.entity.rebind.RebindTestFixtureWithApp;
import brooklyn.entity.rebind.RebindTestUtils;
import brooklyn.entity.rebind.RecordingRebindExceptionHandler;
import brooklyn.entity.rebind.persister.BrooklynMementoPersisterToObjectStore;
import brooklyn.entity.rebind.persister.FileBasedObjectStore;
import brooklyn.entity.rebind.persister.PersistMode;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.management.ManagementContext;
import brooklyn.management.ha.HighAvailabilityMode;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.management.internal.ManagementContextInternal;
import brooklyn.mementos.BrooklynMementoRawData;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.guava.SerializablePredicate;
import brooklyn.util.os.Os;

import com.google.common.base.Objects;
import com.google.common.base.Predicate;

@SuppressWarnings("serial")
public class CompoundTransformerTest extends RebindTestFixtureWithApp {

    private static final Logger LOG = LoggerFactory.getLogger(CompoundTransformerTest.class);

    private File newMementoDir;
    
    @AfterMethod(alwaysRun=true)
    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        if (newMementoDir != null) FileBasedObjectStore.deleteCompletely(mementoDir);
    }

    @Test
    public void testNoopTransformation() throws Exception {
        CompoundTransformer transformer = CompoundTransformer.builder()
                .build();
        
        newApp = transformAndRebind(transformer);

        // Assert has expected config/fields
        assertEquals(newApp.getId(), origApp.getId());
    }
    
    @Test
    public void testRenameClass() throws Exception {
        ConfigKey<Object> CONF1 = new BasicConfigKey<Object>(Object.class, "test.conf1");
        
        origApp.setConfig(CONF1, new OrigType("myfieldval"));
        
        CompoundTransformer transformer = CompoundTransformer.builder()
                .renameClass(OrigType.class.getName(), RenamedType.class.getName())
                .build();

        newApp = transformAndRebind(transformer);

        Object newConfVal = newApp.getConfig(CONF1);
        assertEquals(((RenamedType)newConfVal).myfield, "myfieldval");
    }
    
    @Test
    public void testRenameAnonymousInnerClass() throws Exception {
        ConfigKey<Object> CONF1 = new BasicConfigKey<Object>(Object.class, "test.conf1");
        
        Predicate<Entity> origPredicate = idEqualTo(origApp.getId());
        origApp.setConfig(CONF1, origPredicate);
        
        CompoundTransformer transformer = CompoundTransformer.builder()
                .renameClass(origPredicate.getClass().getName(), RenamedIdEqualToPredicate.class.getName())
                .renameField(RenamedIdEqualToPredicate.class.getName(), "val$paramVal", "val")
                .build();

        newApp = transformAndRebind(transformer);

        RenamedIdEqualToPredicate newPredicate = (RenamedIdEqualToPredicate) newApp.getConfig(CONF1);
        assertTrue(newPredicate.apply(newApp));
    }
    
    protected TestApplication transformAndRebind(CompoundTransformer transformer) throws Exception {
        RebindTestUtils.waitForPersisted(origApp);
        BrooklynMementoRawData newRawData = transform(origManagementContext, transformer);
        newMementoDir = persist(newRawData);
        return rebind(newMementoDir);
    }
    
    protected BrooklynMementoRawData transform(ManagementContext mgmt, CompoundTransformer transformer) throws Exception {
        BrooklynMementoPersisterToObjectStore reader = (BrooklynMementoPersisterToObjectStore) mgmt.getRebindManager().getPersister();
        RebindExceptionHandler exceptionHandler = new RecordingRebindExceptionHandler(RebindFailureMode.FAIL_FAST, RebindFailureMode.FAIL_FAST);
        BrooklynMementoRawData result = transformer.transform(reader, exceptionHandler);
        
        LOG.info("Test "+getClass()+" transformed persisted state");
        return result;
    }
    
    protected File persist(BrooklynMementoRawData rawData) throws Exception {
        File newMementoDir = Os.newTempDir(getClass());
        
        FileBasedObjectStore objectStore = new FileBasedObjectStore(newMementoDir);
        objectStore.injectManagementContext(origManagementContext);
        objectStore.prepareForSharedUse(PersistMode.CLEAN, HighAvailabilityMode.DISABLED);

        BrooklynMementoPersisterToObjectStore persister = new BrooklynMementoPersisterToObjectStore(
                objectStore,
                ((ManagementContextInternal)origManagementContext).getBrooklynProperties(),
                origManagementContext.getCatalog().getRootClassLoader());
        persister.enableWriteAccess();

        PersistenceExceptionHandler exceptionHandler = PersistenceExceptionHandlerImpl.builder().build();
        persister.checkpoint(rawData, exceptionHandler);
        
        LOG.info("Test "+getClass()+" persisted raw data to "+newMementoDir);
        return newMementoDir;
    }

    protected TestApplication rebind(File newMementoDir) throws Exception {
        newManagementContext = RebindTestUtils.managementContextBuilder(newMementoDir, classLoader)
                .forLive(useLiveManagementContext())
                .buildUnstarted();

        return (TestApplication) RebindTestUtils.rebind(RebindOptions.create()
                .newManagementContext(newManagementContext)
                .classLoader(classLoader)
                .mementoDir(newMementoDir));
    }
    
    public static class OrigType {
        public String myfield;
        
        public OrigType(String myfield) {
            this.myfield = myfield;
        }
    }
    
    public static class RenamedType {
        public String myfield;
        
        public RenamedType(String myfield) {
            this.myfield = myfield;
        }
    }
    
    // Example method, similar to EntityPredicates where we want to move the annonymous inner class
    // to be a named inner class
    public static <T> Predicate<Entity> idEqualTo(final T paramVal) {
        return new SerializablePredicate<Entity>() {
            @Override
            public boolean apply(@Nullable Entity input) {
                return (input != null) && Objects.equal(input.getId(), paramVal);
            }
        };
    }

    private static class RenamedIdEqualToPredicate implements SerializablePredicate<Entity> {
        private String val;
        
        @SuppressWarnings("unused") //used by renames above
        RenamedIdEqualToPredicate(String val) {
            this.val = val;
        }
        @Override
        public boolean apply(@Nullable Entity input) {
            return (input != null) && Objects.equal(input.getId(), val);
        }
    }
}
