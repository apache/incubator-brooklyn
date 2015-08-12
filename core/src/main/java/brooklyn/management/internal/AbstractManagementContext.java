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

import java.net.URI;
import java.net.URL;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.basic.BrooklynObject;

import org.apache.brooklyn.catalog.BrooklynCatalog;
import org.apache.brooklyn.catalog.CatalogItem;
import org.apache.brooklyn.management.ExecutionContext;
import org.apache.brooklyn.management.ManagementContext;
import org.apache.brooklyn.management.SubscriptionContext;
import org.apache.brooklyn.management.Task;
import org.apache.brooklyn.management.classloading.BrooklynClassLoadingContext;
import org.apache.brooklyn.management.entitlement.EntitlementManager;
import org.apache.brooklyn.management.ha.HighAvailabilityManager;

import brooklyn.catalog.internal.BasicBrooklynCatalog;
import brooklyn.catalog.internal.CatalogInitialization;
import brooklyn.catalog.internal.CatalogUtils;
import brooklyn.config.BrooklynProperties;
import brooklyn.config.StringConfigMap;
import brooklyn.entity.Effector;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractEntity;
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
import brooklyn.management.classloading.JavaBrooklynClassLoadingContext;
import brooklyn.management.entitlement.Entitlements;
import brooklyn.management.ha.HighAvailabilityManagerImpl;
import brooklyn.util.GroovyJavaMethods;
import brooklyn.util.ResourceUtils;
import brooklyn.util.collections.MutableList;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.guava.Maybe;
import brooklyn.util.task.BasicExecutionContext;
import brooklyn.util.task.Tasks;

import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;

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
        ResourceUtils.addClassLoaderProvider(new Function<Object, BrooklynClassLoadingContext>() {
            @Override
            public BrooklynClassLoadingContext apply(@Nullable Object input) {
                if (input instanceof EntityInternal) {
                    EntityInternal internal = (EntityInternal)input;
                    if (internal.getCatalogItemId() != null) {
                        CatalogItem<?, ?> item = CatalogUtils.getCatalogItemOptionalVersion(internal.getManagementContext(), internal.getCatalogItemId());
                        if (item != null) {
                            return CatalogUtils.newClassLoadingContext(internal.getManagementContext(), item);
                        } else {
                            log.error("Can't find catalog item " + internal.getCatalogItemId() +
                                    " used for instantiating entity " + internal +
                                    ". Falling back to application classpath.");
                        }
                    }
                    return apply(internal.getManagementSupport());
                }
                
                if (input instanceof EntityManagementSupport)
                    return apply(((EntityManagementSupport)input).getManagementContext());
                if (input instanceof ManagementContext)
                    return JavaBrooklynClassLoadingContext.create((ManagementContext) input);
                return null;
            }
        });
    }

    private final AtomicLong totalEffectorInvocationCount = new AtomicLong();

    protected BrooklynProperties configMap;
    protected BasicLocationRegistry locationRegistry;
    protected final BasicBrooklynCatalog catalog;
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
    protected boolean startupComplete = false;
    protected final List<Throwable> errors = Collections.synchronizedList(MutableList.<Throwable>of()); 

    protected Maybe<URI> uri = Maybe.absent();
    protected CatalogInitialization catalogInitialization;

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

        this.catalog = new BasicBrooklynCatalog(this);
        
        this.storage = new BrooklynStorageImpl(datagrid);
        this.rebindManager = new RebindManagerImpl(this); // TODO leaking "this" reference; yuck
        this.highAvailabilityManager = new HighAvailabilityManagerImpl(this); // TODO leaking "this" reference; yuck
        
        this.entitlementManager = Entitlements.newManager(this, brooklynProperties);
    }

    @Override
    public void terminate() {
        highAvailabilityManager.stop();
        running = false;
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
    public boolean isStartupComplete() {
        return startupComplete;
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
        // BEC is a thin wrapper around EM so fine to create a new one here; but make sure it gets the real entity
        if (e instanceof AbstractEntity) {
            return new BasicExecutionContext(MutableMap.of("tag", BrooklynTaskTags.tagForContextEntity(e)), getExecutionManager());
        } else {
            return ((EntityInternal)e).getManagementSupport().getExecutionContext();
        }
    }

    @Override
    public ExecutionContext getServerExecutionContext() {
        // BEC is a thin wrapper around EM so fine to create a new one here
        return new BasicExecutionContext(MutableMap.of("tag", BrooklynTaskTags.BROOKLYN_SERVER_TASK_TAG), getExecutionManager());
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
                Task<T> task = runAtEntity( EffectorUtils.getTaskFlagsForEffectorInvocation(entity, eff, 
                            ConfigBag.newInstance().configureStringKey("args", args)),
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
     * @deprecated since 0.6.0 use effectors (or support {@code runAtEntity(Entity, Effector, Map)} if something else is needed);
     * (Callable with Map flags is too open-ended, bothersome to support, and not used much) 
     */
    @Deprecated
    public abstract <T> Task<T> runAtEntity(@SuppressWarnings("rawtypes") Map flags, Entity entity, Callable<T> c);

    /** Runs the given effector in the right place for the given entity.
     * The task is immediately submitted in the background, but also recorded in the queueing context (if present)
     * so it appears as a child, but marked inessential so it does not fail the parent task, who will ordinarily
     * call {@link Task#get()} on the object and may do their own failure handling. 
     */
    protected abstract <T> Task<T> runAtEntity(final Entity entity, final Effector<T> eff, @SuppressWarnings("rawtypes") final Map parameters);

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
        if (!getCatalogInitialization().hasRunAnyInitialization()) {
            // catalog init is needed; normally this will be done from start sequence,
            // but if accessed early -- and in tests -- we will load it here
            getCatalogInitialization().injectManagementContext(this);
            getCatalogInitialization().populateUnofficial(catalog);
        }
        return catalog;
    }
    
    @Override
    public ClassLoader getCatalogClassLoader() {
        // catalog does not have to be initialized
        return catalog.getRootClassLoader();
    }

    /**
     * Optional class-loader that this management context should use as its base,
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
     * (e.g. surefire) do funny things which the underlying org.reflections.Reflections library can't see in to.
     * <p>
     * This should normally be invoked early in the server startup.  Setting it after the catalog is loaded will not
     * take effect without an explicit internal call to do so.  Once set, it can be changed prior to catalog loading
     * but it cannot be <i>changed</i> once the catalog is loaded.
     * <p>
     * ClasspathHelper.forJavaClassPath() is often a good argument to pass, and is used internally in some places
     * when no items are found on the catalog. */
    @Override
    public void setBaseClassPathForScanning(Iterable<URL> urls) {
        if (Objects.equal(baseClassPathForScanning, urls)) return;
        if (baseClassPathForScanning != null) {
            if (catalog==null)
                log.warn("Changing scan classpath to "+urls+" from "+baseClassPathForScanning);
            else
                throw new IllegalStateException("Cannot change base class path for scanning (in "+this+")");
        }
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
    
    private Object catalogInitMutex = new Object();
    @Override
    public CatalogInitialization getCatalogInitialization() {
        synchronized (catalogInitMutex) {
            if (catalogInitialization!=null) return catalogInitialization;
            CatalogInitialization ci = new CatalogInitialization();
            setCatalogInitialization(ci);
            return ci;
        }
    }
    
    @Override
    public void setCatalogInitialization(CatalogInitialization catalogInitialization) {
        synchronized (catalogInitMutex) {
            Preconditions.checkNotNull(catalogInitialization, "initialization must not be null");
            if (this.catalogInitialization!=null && this.catalogInitialization != catalogInitialization)
                throw new IllegalStateException("Changing catalog init from "+this.catalogInitialization+" to "+catalogInitialization+"; changes not permitted");
            catalogInitialization.injectManagementContext(this);
            this.catalogInitialization = catalogInitialization;
        }
    }
    
    public BrooklynObject lookup(String id) {
        return lookup(id, BrooklynObject.class);
    }
    
    @SuppressWarnings("unchecked")
    public <T extends BrooklynObject> T lookup(String id, Class<T> type) {
        Object result;
        result = getEntityManager().getEntity(id);
        if (result!=null && type.isInstance(result)) return (T)result;
        
        result = getLocationManager().getLocation(id);
        if (result!=null && type.isInstance(result)) return (T)result;

        // TODO policies, enrichers, feeds
        return null;
    }

    @Override
    public List<Throwable> errors() {
        return errors;
    }
    
}
