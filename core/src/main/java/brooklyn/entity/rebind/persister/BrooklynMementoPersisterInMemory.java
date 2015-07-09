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

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import brooklyn.basic.BrooklynObject;
import brooklyn.catalog.CatalogItem;
import brooklyn.entity.Entity;
import brooklyn.entity.Feed;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.proxying.EntityProxy;
import brooklyn.entity.rebind.BrooklynObjectType;
import brooklyn.entity.rebind.PersistenceExceptionHandler;
import brooklyn.entity.rebind.PersistenceExceptionHandlerImpl;
import brooklyn.entity.rebind.RebindExceptionHandler;
import brooklyn.entity.rebind.RebindExceptionHandlerImpl;
import brooklyn.entity.rebind.RebindManager;
import brooklyn.location.Location;
import brooklyn.management.ManagementContext;
import brooklyn.mementos.BrooklynCatalogMementoManifest;
import brooklyn.mementos.BrooklynMemento;
import brooklyn.mementos.BrooklynMementoManifest;
import brooklyn.mementos.BrooklynMementoRawData;
import brooklyn.policy.Enricher;
import brooklyn.policy.Policy;
import brooklyn.util.collections.MutableList;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.javalang.JavaClassNames;
import brooklyn.util.javalang.Reflections;
import brooklyn.util.os.Os;
import brooklyn.util.time.Duration;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.common.base.Throwables;

/**
 * @deprecated since 0.7.0 for production use {@link BrooklynMementoPersisterToObjectStore} instead; class be moved to tests
 * <code>
 * new BrooklynMementoPersisterToObjectStore(new InMemoryObjectStore(), classLoader)
 * </code>
 */
@Deprecated
public class BrooklynMementoPersisterInMemory extends AbstractBrooklynMementoPersister {

    private final ClassLoader classLoader;
    private final boolean checkPersistable;
    
    public BrooklynMementoPersisterInMemory(ClassLoader classLoader) {
        this(classLoader, true);
    }
    
    public BrooklynMementoPersisterInMemory(ClassLoader classLoader, boolean checkPersistable) {
        this.classLoader = checkNotNull(classLoader, "classLoader");
        this.checkPersistable = checkPersistable;
    }
    
    @VisibleForTesting
    @Override
    public void waitForWritesCompleted(long timeout, TimeUnit unit) throws InterruptedException, TimeoutException {
    }
    
    @VisibleForTesting
    @Override
    public void waitForWritesCompleted(Duration timeout) throws InterruptedException, TimeoutException {
        // TODO Could wait for concurrent checkpoint/delta, but don't need to for tests
        // because first waits for checkpoint/delta to have been called by RebindManagerImpl.
        return;
    }

    @Override
    public void checkpoint(BrooklynMemento newMemento, PersistenceExceptionHandler exceptionHandler) {
        super.checkpoint(newMemento, exceptionHandler);
        if (checkPersistable) reserializeMemento();
    }

    @Override
    public void delta(Delta delta, PersistenceExceptionHandler exceptionHandler) {
        super.delta(delta, exceptionHandler);
        if (checkPersistable) reserializeMemento();
    }
    
    private void reserializeMemento() {
        // To confirm always serializable
        try {
            File tempDir = Os.newTempDir(JavaClassNames.cleanSimpleClassName(this));
            try {
                // TODO Duplicate code for LookupContext in RebindManager
                BrooklynMementoPersisterToMultiFile persister = new BrooklynMementoPersisterToMultiFile(tempDir , classLoader);
                RebindExceptionHandler rebindExceptionHandler = RebindExceptionHandlerImpl.builder()
                        .danglingRefFailureMode(RebindManager.RebindFailureMode.FAIL_AT_END)
                        .rebindFailureMode(RebindManager.RebindFailureMode.FAIL_AT_END)
                        .build();
                PersistenceExceptionHandler persistenceExceptionHandler = PersistenceExceptionHandlerImpl.builder().build();
                persister.checkpoint(memento, persistenceExceptionHandler);
                final BrooklynMementoManifest manifest = persister.loadMementoManifest(null, rebindExceptionHandler);
                LookupContext dummyLookupContext = new LookupContext() {
                    @Override
                    public ManagementContext lookupManagementContext() {
                        return null;
                    }
                    @Override public Entity lookupEntity(String id) {
                        List<Class<?>> types = MutableList.<Class<?>>builder()
                                .add(Entity.class, EntityInternal.class, EntityProxy.class)
                                //TODO Doesn't support loading from catalog item osgi bundles
                                .add(loadClass(manifest.getEntityIdToManifest().get(id).getType()))
                                .build();
                        return (Entity) java.lang.reflect.Proxy.newProxyInstance(
                                classLoader,
                                types.toArray(new Class<?>[types.size()]),
                                new InvocationHandler() {
                                    @Override public Object invoke(Object proxy, Method m, Object[] args) throws Throwable {
                                        return m.invoke(this, args);
                                    }
                                });
                    }
                    @Override public Location lookupLocation(String id) {
                        Class<?> clazz = loadClass(manifest.getLocationIdToManifest().get(id).getType());
                        return (Location) invokeConstructor(clazz, new Object[0], new Object[] {MutableMap.of()});
                    }
                    @Override public Policy lookupPolicy(String id) {
                        Class<?> clazz = loadClass(manifest.getPolicyIdToManifest().get(id).getType());
                        return (Policy) invokeConstructor(clazz, new Object[0], new Object[] {MutableMap.of()});
                    }
                    @Override public Enricher lookupEnricher(String id) {
                        Class<?> clazz = loadClass(manifest.getEnricherIdToManifest().get(id).getType());
                        return (Enricher) invokeConstructor(clazz, new Object[0], new Object[] {MutableMap.of()});
                    }
                    @Override public Feed lookupFeed(String id) {
                        Class<?> clazz = loadClass(manifest.getFeedIdToManifest().get(id).getType());
                        return (Feed) invokeConstructor(clazz, new Object[0], new Object[] {MutableMap.of()});
                    }
                    @Override public CatalogItem<?, ?> lookupCatalogItem(String id) {
                        Class<?> clazz = loadClass(manifest.getCatalogItemMemento(id).getType());
                        return (CatalogItem<?,?>) invokeConstructor(clazz, new Object[0]);
                    }
                    
                    @Override
                    public BrooklynObject lookup(BrooklynObjectType type, String id) {
                        switch (type) {
                        case CATALOG_ITEM: return lookupCatalogItem(id);
                        case ENRICHER: return lookupEnricher(id);
                        case ENTITY: return lookupEntity(id);
                        case FEED: return lookupFeed(id);
                        case LOCATION: return lookupLocation(id);
                        case POLICY: return lookupPolicy(id);
                        case UNKNOWN: return null;
                        }
                        throw new IllegalStateException("Unexpected type "+type+" / id "+id);
                    }
                    @Override
                    public BrooklynObject peek(BrooklynObjectType type, String id) {
                        return lookup(type, id);
                    }

                    private Class<?> loadClass(String name) {
                        try {
                            return classLoader.loadClass(name);
                        } catch (ClassNotFoundException e) {
                            throw Exceptions.propagate(e);
                        }
                    }
                    private <T> T invokeConstructor(Class<T> clazz, Object[]... possibleArgs) {
                        for (Object[] args : possibleArgs) {
                            try {
                                Optional<T> v = Reflections.invokeConstructorWithArgs(clazz, args, true);
                                if (v.isPresent()) {
                                    return v.get();
                                }
                            } catch (Exception e) {
                                throw Exceptions.propagate(e);
                            }
                        }
                        throw new IllegalStateException("Cannot instantiate instance of type "+clazz+"; expected constructor signature not found");
                    }
                };

                // Not actually reconstituting, because need to use a real lookupContext to reconstitute all the entities
                persister.loadMemento(null, dummyLookupContext, rebindExceptionHandler);
            } finally {
                Os.deleteRecursively(tempDir);
            }
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public BrooklynCatalogMementoManifest loadCatalogMementos(
            BrooklynMementoRawData mementoData,
            RebindExceptionHandler exceptionHandler) {
        // TODO Auto-generated method stub
        return null;
    }
}
