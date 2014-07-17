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
package brooklyn.management.internal;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.String.format;

import java.io.FileNotFoundException;
import java.net.URI;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.catalog.BrooklynCatalog;
import brooklyn.catalog.internal.BasicBrooklynCatalog;
import brooklyn.catalog.internal.CatalogClasspathDo.CatalogScanningModes;
import brooklyn.catalog.internal.CatalogDto;
import brooklyn.config.BrooklynProperties;
import brooklyn.config.StringConfigMap;
import brooklyn.entity.Effector;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.BrooklynTaskTags;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.drivers.BasicEntityDriverManager;
import brooklyn.entity.drivers.EntityDriverManager;
import brooklyn.entity.drivers.downloads.BasicDownloadsManager;
import brooklyn.entity.drivers.downloads.DownloadResolverManager;
import brooklyn.entity.rebind.RebindManager;
import brooklyn.entity.rebind.RebindManagerImpl;
import brooklyn.internal.storage.BrooklynStorage;
import brooklyn.internal.storage.DataGrid;
import brooklyn.internal.storage.DataGridFactory;
import brooklyn.internal.storage.impl.BrooklynStorageImpl;
import brooklyn.internal.storage.impl.inmemory.InMemoryDataGridFactory;
import brooklyn.location.LocationRegistry;
import brooklyn.location.basic.BasicLocationRegistry;
import brooklyn.management.ExecutionContext;
import brooklyn.management.ManagementContext;
import brooklyn.management.SubscriptionContext;
import brooklyn.management.Task;
import brooklyn.management.classloading.BrooklynClassLoadingContext;
import brooklyn.management.classloading.JavaBrooklynClassLoadingContext;
import brooklyn.management.entitlement.EntitlementManager;
import brooklyn.management.entitlement.Entitlements;
import brooklyn.management.ha.HighAvailabilityManager;
import brooklyn.management.ha.HighAvailabilityManagerImpl;
import brooklyn.util.GroovyJavaMethods;
import brooklyn.util.ResourceUtils;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.guava.Maybe;
import brooklyn.util.task.BasicExecutionContext;
import brooklyn.util.task.Tasks;
import brooklyn.util.text.Strings;

import com.google.common.base.Function;
import com.google.common.base.Throwables;

public abstract class AbstractManagementContext implements ManagementContextInternal {
    private static final Logger log = LoggerFactory.getLogger(AbstractManagementContext.class);

    private static DataGridFactory loadDataGridFactory(BrooklynProperties properties) {
        String clazzName = properties.getFirst(DataGridFactory.class.getName());
        if(clazzName == null){
            clazzName = InMemoryDataGridFactory.class.getName();
        }

        Class<?> clazz;
        try{
            //todo: which classloader should we use?
            clazz = LocalManagementContext.class.getClassLoader().loadClass(clazzName);
        }catch(ClassNotFoundException e){
            throw new IllegalStateException(format("Could not load class [%s]",clazzName),e);
        }

        Object instance;
        try {
            instance = clazz.newInstance();
        } catch (InstantiationException e) {
            throw new IllegalStateException(format("Could not instantiate class [%s]",clazzName),e);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(format("Could not instantiate class [%s]",clazzName),e);
        }

        if(!(instance instanceof DataGridFactory)){
            throw new IllegalStateException(format("Class [%s] not an instantiate of class [%s]",clazzName, DataGridFactory.class.getName()));
        }

        return (DataGridFactory)instance;
    }

    static {
        // ensure that if ResourceUtils is given an entity as context,
        // we use the catalog class loader (e.g. to resolve classpath URLs)
        ResourceUtils.addClassLoaderProvider(new Function<Object, BrooklynClassLoadingContext>() {
            @Override
            public BrooklynClassLoadingContext apply(@Nullable Object input) {
                // TODO for entities, this should get its originating catalog item's loader
                if (input instanceof EntityInternal)
                    return apply(((EntityInternal)input).getManagementSupport());
                
                if (input instanceof EntityManagementSupport)
                    return apply(((EntityManagementSupport)input).getManagementContext());
                if (input instanceof ManagementContext)
                    return JavaBrooklynClassLoadingContext.newDefault((ManagementContext) input);
                return null;
            }
        });
    }

    private final AtomicLong totalEffectorInvocationCount = new AtomicLong();

    protected BrooklynProperties configMap;
    protected BasicLocationRegistry locationRegistry;
    protected volatile BasicBrooklynCatalog catalog;
    protected ClassLoader baseClassLoader;
    protected Iterable<URL> baseClassPathForScanning;

    private final RebindManager rebindManager;
    private final HighAvailabilityManager highAvailabilityManager;
    
    protected volatile BrooklynGarbageCollector gc;

    private final EntityDriverManager entityDriverManager;
    protected DownloadResolverManager downloadsManager;

    protected EntitlementManager entitlementManager;
    
    private final BrooklynStorage storage;

    private volatile boolean running = true;

    protected Maybe<URI> uri = Maybe.absent();

    public AbstractManagementContext(BrooklynProperties brooklynProperties){
        this(brooklynProperties, null);
    }

    public AbstractManagementContext(BrooklynProperties brooklynProperties, DataGridFactory datagridFactory) {
        this.configMap = brooklynProperties;
        this.entityDriverManager = new BasicEntityDriverManager();
        this.downloadsManager = BasicDownloadsManager.newDefault(configMap);
        if (datagridFactory == null) {
            datagridFactory = loadDataGridFactory(brooklynProperties);
        }
        DataGrid datagrid = datagridFactory.newDataGrid(this);
         
        this.storage = new BrooklynStorageImpl(datagrid);
        this.rebindManager = new RebindManagerImpl(this); // TODO leaking "this" reference; yuck
        this.highAvailabilityManager = new HighAvailabilityManagerImpl(this); // TODO leaking "this" reference; yuck
        
        this.entitlementManager = Entitlements.newManager(ResourceUtils.create(getBaseClassLoader()), brooklynProperties);
    }

    @Override
    public void terminate() {
        running = false;
        highAvailabilityManager.stop();
        rebindManager.stop();
        storage.terminate();
        // Don't unmanage everything; different entities get given their events at different times 
        // so can cause problems (e.g. a group finds out that a member is unmanaged, before the
        // group itself has been told that it is unmanaged).
    }
    
    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public BrooklynStorage getStorage() {
        return storage;
    }
    
    @Override
    public RebindManager getRebindManager() {
        return rebindManager;
    }

    @Override
    public HighAvailabilityManager getHighAvailabilityManager() {
        return highAvailabilityManager;
    }

    @Override
    public long getTotalEffectorInvocations() {
        return totalEffectorInvocationCount.get();
    }
    
    @Override
    public ExecutionContext getExecutionContext(Entity e) {
        // BEC is a thin wrapper around EM so fine to create a new one here
        return new BasicExecutionContext(MutableMap.of("tag", BrooklynTaskTags.tagForContextEntity(e)), getExecutionManager());
    }
    
    @Override
    public SubscriptionContext getSubscriptionContext(Entity e) {
        // BSC is a thin wrapper around SM so fine to create a new one here
        return new BasicSubscriptionContext(getSubscriptionManager(), e);
    }

    @Override
    public EntityDriverManager getEntityDriverManager() {
        return entityDriverManager;
    }

    @Override
    public DownloadResolverManager getEntityDownloadsManager() {
        return downloadsManager;
    }
    
    @Override
    public EntitlementManager getEntitlementManager() {
        return entitlementManager;
    }
    
    protected abstract void manageIfNecessary(Entity entity, Object context);

    @Override
    public <T> Task<T> invokeEffector(final Entity entity, final Effector<T> eff, @SuppressWarnings("rawtypes") final Map parameters) {
        return runAtEntity(entity, eff, parameters);
    }
    
    protected <T> T invokeEffectorMethodLocal(Entity entity, Effector<T> eff, Object args) {
        assert isManagedLocally(entity) : "cannot invoke effector method at "+this+" because it is not managed here";
        totalEffectorInvocationCount.incrementAndGet();
        Object[] transformedArgs = EffectorUtils.prepareArgsForEffector(eff, args);
        return GroovyJavaMethods.invokeMethodOnMetaClass(entity, eff.getName(), transformedArgs);
    }

    /**
     * Method for entity to make effector happen with correct semantics (right place, right task context),
     * when a method is called on that entity.
     * @throws ExecutionException 
     */
    @Override
    public <T> T invokeEffectorMethodSync(final Entity entity, final Effector<T> eff, final Object args) throws ExecutionException {
        try {
            Task<?> current = Tasks.current();
            if (current == null || !entity.equals(BrooklynTaskTags.getContextEntity(current)) || !isManagedLocally(entity)) {
                manageIfNecessary(entity, eff.getName());
                // Wrap in a task if we aren't already in a task that is tagged with this entity
                Task<T> task = runAtEntity( EffectorUtils.getTaskFlagsForEffectorInvocation(entity, eff),
                        entity, 
                        new Callable<T>() {
                            public T call() {
                                return invokeEffectorMethodLocal(entity, eff, args);
                            }});
                return task.get();
            } else {
                return invokeEffectorMethodLocal(entity, eff, args);
            }
        } catch (Exception e) {
            // don't need to attach any message or warning because the Effector impl hierarchy does that (see calls to EffectorUtils.handleException)
            throw new ExecutionException(e);
        }
    }

    /**
     * Whether the master entity record is local, and sensors and effectors can be properly accessed locally.
     */ 
    public abstract boolean isManagedLocally(Entity e);
    
    /**
     * Causes the indicated runnable to be run at the right location for the given entity.
     *
     * Returns the actual task (if it is local) or a proxy task (if it is remote);
     * if management for the entity has not yet started this may start it.
     * 
     * @deprecated since 0.6.0 use effectors (or support {@code runAtEntity(Entity, Task)} if something else is needed);
     * (Callable with Map flags is too open-ended, bothersome to support, and not used much) 
     */
    @Deprecated
    public abstract <T> Task<T> runAtEntity(@SuppressWarnings("rawtypes") Map flags, Entity entity, Callable<T> c);

    protected abstract <T> Task<T> runAtEntity(final Entity entity, final Effector<T> eff, @SuppressWarnings("rawtypes") final Map parameters);

    @Override
    public abstract void addEntitySetListener(CollectionChangeListener<Entity> listener);

    @Override
    public abstract void removeEntitySetListener(CollectionChangeListener<Entity> listener);
    
    @Override
    public StringConfigMap getConfig() {
        return configMap;
    }

    @Override
    public BrooklynProperties getBrooklynProperties() {
        return configMap;
    }

    @Override
    public synchronized LocationRegistry getLocationRegistry() {
        if (locationRegistry==null) locationRegistry = new BasicLocationRegistry(this);
        return locationRegistry;
    }

    @Override
    public BrooklynCatalog getCatalog() {
        if (catalog==null) loadCatalog();
        return catalog;
    }

    protected synchronized void loadCatalog() {
        if (catalog!=null) return;
        
        BasicBrooklynCatalog catalog = null;
        String catalogUrl = getConfig().getConfig(BROOKLYN_CATALOG_URL);
        
        try {
            if (!Strings.isEmpty(catalogUrl)) {
                catalog = new BasicBrooklynCatalog(this, CatalogDto.newDtoFromUrl(catalogUrl));
                if (log.isDebugEnabled())
                    log.debug("Loading catalog from "+catalogUrl+": "+catalog);
            }
        } catch (Exception e) {
            if (Throwables.getRootCause(e) instanceof FileNotFoundException) {
                Maybe<Object> nonDefaultUrl = getConfig().getConfigRaw(BROOKLYN_CATALOG_URL, true);
                if (nonDefaultUrl.isPresentAndNonNull() && !"".equals(nonDefaultUrl.get())) {
                    log.warn("Could not find catalog XML specified at "+nonDefaultUrl+"; using default (local classpath) catalog. Error was: "+e);
                } else {
                    if (log.isDebugEnabled())
                        log.debug("No default catalog file available at "+catalogUrl+"; trying again using local classpath to populate catalog. Error was: "+e);
                }
            } else {
                log.warn("Error importing catalog XML at "+catalogUrl+"; using default (local classpath) catalog. Error was: "+e, e);                
            }
        }
        if (catalog==null) {
            // retry, either an error, or was blank
            catalog = new BasicBrooklynCatalog(this, CatalogDto.newDefaultLocalScanningDto(CatalogScanningModes.ANNOTATIONS));
            if (log.isDebugEnabled())
                log.debug("Loaded default (local classpath) catalog: "+catalog);
        }
        catalog.load();
        
        this.catalog = catalog;
    }

    /** Optional class-loader that this management context should use as its base,
     * as the first-resort in the catalog, and for scanning (if scanning the default in the catalog).
     * In most instances the default classloader (ManagementContext.class.getClassLoader(), assuming
     * this was in the JARs used at boot time) is fine, and in those cases this method normally returns null.
     * (Surefire does some weird stuff, but the default classloader is fine for loading;
     * however it requires a custom base classpath to be set for scanning.)
     */
    @Override
    public ClassLoader getBaseClassLoader() {
        return baseClassLoader;
    }
    
    /** See {@link #getBaseClassLoader()}.  Only settable once and must be invoked before catalog is loaded. */
    public void setBaseClassLoader(ClassLoader cl) {
        if (baseClassLoader==cl) return;
        if (baseClassLoader!=null) throw new IllegalStateException("Cannot change base class loader (in "+this+")");
        if (catalog!=null) throw new IllegalStateException("Cannot set base class after catalog has been loaded (in "+this+")");
        this.baseClassLoader = cl;
    }
    
    /** Optional mechanism for setting the classpath which should be scanned by the catalog, if the catalog
     * is scanning the default classpath.  Usually it infers the right thing, but some classloaders
     * (e.g. surefire) do funny things which the underlying org.reflections.Reflectiosn library can't see in to. 
     * <p>
     * Only settable once, before catalog is loaded.
     * <p>
     * ClasspathHelper.forJavaClassPath() is often a good argument to pass. */
    @Override
    public void setBaseClassPathForScanning(Iterable<URL> urls) {
        if (baseClassPathForScanning == urls) return;
        if (baseClassPathForScanning != null) throw new IllegalStateException("Cannot change base class path for scanning (in "+this+")");
        if (catalog != null) throw new IllegalStateException("Cannot set base class path for scanning after catalog has been loaded (in "+this+")");
        this.baseClassPathForScanning = urls;
    }
    /** 
     * @see #setBaseClassPathForScanning(Iterable)
     */
    @Override
    public Iterable<URL> getBaseClassPathForScanning() {
        return baseClassPathForScanning;
    }

    public BrooklynGarbageCollector getGarbageCollector() {
        return gc;
    }

    @Override
    public void setManagementNodeUri(URI uri) {
        this.uri = Maybe.of(checkNotNull(uri, "uri"));
    }

    @Override
    public Maybe<URI> getManagementNodeUri() {
        return uri;
    }
}
