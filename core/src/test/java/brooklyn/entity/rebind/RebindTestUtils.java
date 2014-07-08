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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.collections.Maps;

import brooklyn.config.BrooklynProperties;
import brooklyn.entity.Application;
import brooklyn.entity.Entity;
import brooklyn.entity.rebind.Dumpers.Pointer;
import brooklyn.entity.rebind.dto.MementosGenerators;
import brooklyn.entity.rebind.persister.BrooklynMementoPersisterToObjectStore;
import brooklyn.entity.rebind.persister.FileBasedObjectStore;
import brooklyn.entity.rebind.persister.PersistMode;
import brooklyn.entity.rebind.persister.PersistenceObjectStore;
import brooklyn.entity.trait.Identifiable;
import brooklyn.location.Location;
import brooklyn.management.ManagementContext;
import brooklyn.management.ha.HighAvailabilityMode;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.management.internal.ManagementContextInternal;
import brooklyn.mementos.BrooklynMemento;
import brooklyn.test.entity.LocalManagementContextForTests;
import brooklyn.util.javalang.Serializers;
import brooklyn.util.javalang.Serializers.ObjectReplacer;
import brooklyn.util.time.Duration;

import com.google.common.collect.Iterables;

public class RebindTestUtils {

    private static final Logger LOG = LoggerFactory.getLogger(RebindTestUtils.class);

    private static final Duration TIMEOUT = Duration.seconds(20);

	public static <T> T serializeAndDeserialize(T memento) throws Exception {
        ObjectReplacer replacer = new ObjectReplacer() {
            private final Map<Pointer, Object> replaced = Maps.newLinkedHashMap();

            @Override public Object replace(Object toserialize) {
                if (toserialize instanceof Location || toserialize instanceof Entity) {
                    Pointer pointer = new Pointer(((Identifiable)toserialize).getId());
                    replaced.put(pointer, toserialize);
                    return pointer;
                }
                return toserialize;
            }
            @Override public Object resolve(Object todeserialize) {
                if (todeserialize instanceof Pointer) {
                    return checkNotNull(replaced.get(todeserialize), todeserialize);
                }
                return todeserialize;
            }
        };

    	try {
    	    return Serializers.reconstitute(memento, replacer);
    	} catch (Exception e) {
    	    try {
    	        Dumpers.logUnserializableChains(memento, replacer);
    	        //Dumpers.deepDumpSerializableness(memento);
    	    } catch (Throwable t) {
    	        LOG.warn("Error logging unserializable chains for memento "+memento+" (propagating original exception)", t);
    	    }
    	    throw e;
    	}
    }
    
	public static void deleteMementoDir(String path) {
	    deleteMementoDir(new File(path));
	}

    public static void deleteMementoDir(File f) {
        FileBasedObjectStore.deleteCompletely(f);
    }

    public static void checkMementoSerializable(Application app) throws Exception {
        BrooklynMemento memento = MementosGenerators.newBrooklynMemento(app.getManagementContext());
        checkMementoSerializable(memento);
    }

    public static void checkMementoSerializable(BrooklynMemento memento) throws Exception {
        serializeAndDeserialize(memento);
    }

    public static LocalManagementContext newPersistingManagementContext(File mementoDir, ClassLoader classLoader) {
        return managementContextBuilder(mementoDir, classLoader).buildStarted();
    }
    public static LocalManagementContext newPersistingManagementContext(File mementoDir, ClassLoader classLoader, long persistPeriodMillis) {
        return managementContextBuilder(mementoDir, classLoader)
                .persistPeriodMillis(persistPeriodMillis)
                .buildStarted();
    }
    
    public static LocalManagementContext newPersistingManagementContextUnstarted(File mementoDir, ClassLoader classLoader) {
        return managementContextBuilder(mementoDir, classLoader).buildUnstarted();
    }

    public static ManagementContextBuilder managementContextBuilder(File mementoDir, ClassLoader classLoader) {
        return new ManagementContextBuilder(classLoader, mementoDir);
    }
    public static ManagementContextBuilder managementContextBuilder(ClassLoader classLoader, File mementoDir) {
        return new ManagementContextBuilder(classLoader, mementoDir);
    }
    public static ManagementContextBuilder managementContextBuilder(ClassLoader classLoader, PersistenceObjectStore objectStore) {
        return new ManagementContextBuilder(classLoader, objectStore);
    }

    public static class ManagementContextBuilder {
        final ClassLoader classLoader;
        BrooklynProperties properties;
        PersistenceObjectStore objectStore;
        Duration persistPeriod = Duration.millis(100);
        boolean forLive;
        
        ManagementContextBuilder(File mementoDir, ClassLoader classLoader) {
            this(classLoader, new FileBasedObjectStore(mementoDir));
        }
        ManagementContextBuilder(ClassLoader classLoader, File mementoDir) {
            this(classLoader, new FileBasedObjectStore(mementoDir));
        }
        ManagementContextBuilder(ClassLoader classLoader, PersistenceObjectStore objStore) {
            this.classLoader = checkNotNull(classLoader, "classLoader");
            this.objectStore = checkNotNull(objStore, "objStore");
        }
        
        public ManagementContextBuilder persistPeriodMillis(long persistPeriodMillis) {
            checkArgument(persistPeriodMillis > 0, "persistPeriodMillis must be greater than 0; was "+persistPeriodMillis);
            return persistPeriod(Duration.millis(persistPeriodMillis));
        }
        public ManagementContextBuilder persistPeriod(Duration persistPeriod) {
            checkNotNull(persistPeriod);
            this.persistPeriod = persistPeriod;
            return this;
        }

        public ManagementContextBuilder properties(BrooklynProperties properties) {
            this.properties = checkNotNull(properties, "properties");
            return this;
        }

        public ManagementContextBuilder forLive(boolean val) {
            this.forLive = val;
            return this;
        }

        public LocalManagementContext buildUnstarted() {
            LocalManagementContext unstarted;
            if (forLive) {
                if (properties != null) {
                    unstarted = new LocalManagementContext(properties);
                } else {
                    unstarted = new LocalManagementContext();
                }
            } else {
                if (properties != null) {
                    unstarted = new LocalManagementContextForTests(properties);
                } else {
                    unstarted = new LocalManagementContextForTests();
                }
            }
            
            objectStore.injectManagementContext(unstarted);
            objectStore.prepareForSharedUse(PersistMode.AUTO, HighAvailabilityMode.DISABLED);
            BrooklynMementoPersisterToObjectStore newPersister = new BrooklynMementoPersisterToObjectStore(
                    objectStore, 
                    unstarted.getBrooklynProperties(), 
                    classLoader);
            ((RebindManagerImpl) unstarted.getRebindManager()).setPeriodicPersistPeriod(persistPeriod);
            unstarted.getRebindManager().setPersister(newPersister, PersistenceExceptionHandlerImpl.builder().build());
            return unstarted;
        }

        public LocalManagementContext buildStarted() {
            LocalManagementContext unstarted = buildUnstarted();
            unstarted.getHighAvailabilityManager().disabled();
            unstarted.getRebindManager().start();
            return unstarted;
        }

    }

    public static Application rebind(File mementoDir, ClassLoader classLoader) throws Exception {
        return rebind(mementoDir, classLoader, null);
    }

    public static Application rebind(File mementoDir, ClassLoader classLoader, RebindExceptionHandler exceptionHandler) throws Exception {
        Collection<Application> newApps = rebindAll(mementoDir, classLoader);
        if (newApps.isEmpty()) throw new IllegalStateException("Application could not be rebinded; serialization probably failed");
        return Iterables.getFirst(newApps, null);
    }

    public static Collection<Application> rebindAll(File mementoDir, ClassLoader classLoader) throws Exception {
        return rebindAll(mementoDir, classLoader, null);
    }

    public static Collection<Application> rebindAll(File mementoDir, ClassLoader classLoader, RebindExceptionHandler exceptionHandler) throws Exception {
        LOG.info("Rebinding app, using directory "+mementoDir);

        LocalManagementContext newManagementContext = newPersistingManagementContextUnstarted(mementoDir, classLoader);
        List<Application> newApps;
        if (exceptionHandler == null) {
            newApps = newManagementContext.getRebindManager().rebind(classLoader);
        } else {
            newApps = newManagementContext.getRebindManager().rebind(classLoader, exceptionHandler);
        }
        if (newApps.isEmpty()) throw new IllegalStateException("Application could not be rebinded; serialization probably failed");
        newManagementContext.getRebindManager().start();
        return newApps;
    }

    public static Application rebind(ManagementContext newManagementContext, File mementoDir, ClassLoader classLoader) throws Exception {
        return rebind(newManagementContext, mementoDir, classLoader, null);
    }

    public static Application rebind(ManagementContext newManagementContext, File mementoDir, ClassLoader classLoader, RebindExceptionHandler exceptionHandler) throws Exception {
        PersistenceObjectStore objectStore = new FileBasedObjectStore(mementoDir);
        objectStore.injectManagementContext(newManagementContext);
        objectStore.prepareForSharedUse(PersistMode.AUTO, HighAvailabilityMode.DISABLED);
        return rebind(newManagementContext, mementoDir, classLoader, exceptionHandler, objectStore);
    }
    public static Application rebind(ManagementContext newManagementContext, File mementoDir,
                                     ClassLoader classLoader, RebindExceptionHandler exceptionHandler, PersistenceObjectStore objectStore) throws Exception {
        return Iterables.getFirst(rebindAll(newManagementContext, mementoDir, classLoader, exceptionHandler, objectStore), null);
    }
    public static Collection<Application> rebindAll(ManagementContext newManagementContext, File mementoDir, ClassLoader classLoader) throws Exception {
        return rebindAll(newManagementContext, mementoDir, classLoader, null, new FileBasedObjectStore(mementoDir));
    }

    public static Collection<Application> rebindAll(ManagementContext newManagementContext, File mementoDir, ClassLoader classLoader, RebindExceptionHandler exceptionHandler, PersistenceObjectStore objectStore) throws Exception {
        LOG.info("Rebinding app, using directory "+mementoDir);

        BrooklynMementoPersisterToObjectStore newPersister = new BrooklynMementoPersisterToObjectStore(
                objectStore,
                ((ManagementContextInternal)newManagementContext).getBrooklynProperties(),
                classLoader);
        newManagementContext.getRebindManager().setPersister(newPersister, PersistenceExceptionHandlerImpl.builder().build());
        List<Application> newApps;
        if (exceptionHandler == null) {
            newApps = newManagementContext.getRebindManager().rebind(classLoader);
        } else {
            newApps = newManagementContext.getRebindManager().rebind(classLoader, exceptionHandler);
        }
        newManagementContext.getRebindManager().start();
        return newApps;
    }

    public static void waitForPersisted(Application origApp) throws InterruptedException, TimeoutException {
        waitForPersisted(origApp.getManagementContext());
    }

    public static void waitForPersisted(ManagementContext managementContext) throws InterruptedException, TimeoutException {
        managementContext.getRebindManager().waitForPendingComplete(TIMEOUT);
    }

    public static void checkCurrentMementoSerializable(Application app) throws Exception {
        BrooklynMemento memento = MementosGenerators.newBrooklynMemento(app.getManagementContext());
        serializeAndDeserialize(memento);
    }
}
