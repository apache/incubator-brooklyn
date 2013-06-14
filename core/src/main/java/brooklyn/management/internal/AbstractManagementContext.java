package brooklyn.management.internal;

import java.io.FileNotFoundException;
import java.net.URL;
import java.util.Collection;
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
import brooklyn.catalog.internal.CatalogDtoUtils;
import brooklyn.config.BrooklynProperties;
import brooklyn.config.StringConfigMap;
import brooklyn.entity.Effector;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractEffector;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.drivers.BasicEntityDriverManager;
import brooklyn.entity.drivers.EntityDriverManager;
import brooklyn.entity.drivers.downloads.BasicDownloadsManager;
import brooklyn.entity.drivers.downloads.DownloadResolverManager;
import brooklyn.entity.rebind.RebindManager;
import brooklyn.entity.rebind.RebindManagerImpl;
import brooklyn.internal.storage.BrooklynStorage;
import brooklyn.internal.storage.DataGrid;
import brooklyn.internal.storage.impl.BrooklynStorageImpl;
import brooklyn.internal.storage.impl.InmemoryDatagrid;
import brooklyn.location.LocationRegistry;
import brooklyn.location.basic.BasicLocationRegistry;
import brooklyn.management.ExecutionContext;
import brooklyn.management.SubscriptionContext;
import brooklyn.management.Task;
import brooklyn.util.GroovyJavaMethods;
import brooklyn.util.ResourceUtils;
import brooklyn.util.collections.MutableList;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.task.BasicExecutionContext;
import brooklyn.util.task.Tasks;
import brooklyn.util.text.Strings;

import com.google.common.base.Function;
import com.google.common.base.Throwables;

public abstract class AbstractManagementContext implements ManagementContextInternal {
    private static final Logger log = LoggerFactory.getLogger(AbstractManagementContext.class);
    
    private final AtomicLong totalEffectorInvocationCount = new AtomicLong();

    protected BrooklynProperties configMap;
    protected BasicLocationRegistry locationRegistry;
    protected volatile BasicBrooklynCatalog catalog;
    protected ClassLoader baseClassLoader;
    protected Iterable<URL> baseClassPathForScanning;

    // TODO leaking "this" reference; yuck
    private final RebindManager rebindManager = new RebindManagerImpl(this);
    
    protected volatile BrooklynGarbageCollector gc;

    private final EntityDriverManager entityDriverManager;
    
    private final DownloadResolverManager downloadsManager;

    private final DataGrid datagrid = new InmemoryDatagrid();

    private final BrooklynStorage storage = new BrooklynStorageImpl(datagrid);

    public AbstractManagementContext(BrooklynProperties brooklynProperties){
       this.configMap = brooklynProperties;
       this.entityDriverManager = new BasicEntityDriverManager();
       this.downloadsManager = BasicDownloadsManager.newDefault(configMap);
    }
    
    static {
        // ensure that if ResourceUtils is given an entity as context,
        // we use the catalog class loader (e.g. to resolve classpath URLs)
        ResourceUtils.addClassLoaderProvider(new Function<Object, ClassLoader>() {
            @Override 
            public ClassLoader apply(@Nullable Object input) {
                if (input instanceof EntityInternal) 
                    return apply(((EntityInternal)input).getManagementSupport());
                if (input instanceof EntityManagementSupport) 
                    return apply(((EntityManagementSupport)input).getManagementContext());
                if (input instanceof AbstractManagementContext) 
                    return ((AbstractManagementContext)input).getCatalog().getRootClassLoader();
                return null;
            }
        });
    }
    
    private volatile boolean running = true;
    
    public void terminate() {
        running = false;
        rebindManager.stop();
        
        // Don't unmanage everything; different entities get given their events at different times 
        // so can cause problems (e.g. a group finds out that a member is unmanaged, before the
        // group itself has been told that it is unmanaged).
    }
    
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

    public long getTotalEffectorInvocations() {
        return totalEffectorInvocationCount.get();
    }
    
    public ExecutionContext getExecutionContext(Entity e) { 
        return new BasicExecutionContext(MutableMap.of("tag", e), getExecutionManager());
    }
    
    public SubscriptionContext getSubscriptionContext(Entity e) {
        return new BasicSubscriptionContext(getSubscriptionManager(), e);
    }

    @Override
    public EntityDriverManager getEntityDriverFactory() {
        return getEntityDriverManager();
    }

    @Override
    public EntityDriverManager getEntityDriverManager() {
        return entityDriverManager;
    }

    @Override
    public DownloadResolverManager getEntityDownloadsManager() {
        return downloadsManager;
    }
    
    @Deprecated
    @Override
    public boolean isManaged(Entity e) {
        return getEntityManager().isManaged(e);
    }
    
    @Deprecated
    @Override
    public void manage(Entity e) {
        getEntityManager().manage(e);
    }
    
    @Deprecated
    @Override
    public void unmanage(Entity e) {
        getEntityManager().unmanage(e);
    }

    @Deprecated
    @Override
    public synchronized Collection<Entity> getEntities() {
        return getEntityManager().getEntities();
    }
    
    @Deprecated
    @Override
    public Entity getEntity(String id) {
        return getEntityManager().getEntity(id);
    }

    protected abstract void manageIfNecessary(Entity entity, Object context);

    public <T> Task<T> invokeEffector(final Entity entity, final Effector<T> eff, @SuppressWarnings("rawtypes") final Map parameters) {
        return runAtEntity(
                MutableMap.builder()
                        .put("description", "invoking "+eff.getName()+" on "+entity+" ("+entity.getDisplayName()+")")
                        .put("displayName", eff.getName())
                        .put("tags", MutableList.of(EFFECTOR_TAG))
                        .build(), 
                entity, 
                new Callable<T>() {
                    public T call() {
                        return ((AbstractEffector<T>)eff).call(entity, parameters);
                    }});
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
    public <T> T invokeEffectorMethodSync(final Entity entity, final Effector<T> eff, final Object args) throws ExecutionException {
        try {
            Task<?> current = Tasks.current();
            if (current == null || !current.getTags().contains(entity) || !isManagedLocally(entity)) {
                manageIfNecessary(entity, eff.getName());
                // Wrap in a task if we aren't already in a task that is tagged with this entity
                Task<T> task = runAtEntity(
                        MutableMap.builder()
                                .put("description", "invoking "+eff.getName()+" on "+entity+" ("+entity.getDisplayName()+")")
                                .put("displayName", eff.getName())
                                .put("tags", MutableList.of(EFFECTOR_TAG))
                                .build(), 
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
            throw new ExecutionException("Error invoking "+eff+" on entity "+entity, e);
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
     */
    public abstract <T> Task<T> runAtEntity(@SuppressWarnings("rawtypes") Map flags, Entity entity, Callable<T> c);

    public abstract void addEntitySetListener(CollectionChangeListener<Entity> listener);

    public abstract void removeEntitySetListener(CollectionChangeListener<Entity> listener);
    
    @Override
    public StringConfigMap getConfig() {
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
                catalog = new BasicBrooklynCatalog(this, CatalogDtoUtils.newDtoFromUrl(catalogUrl));
                if (log.isDebugEnabled())
                    log.debug("Loaded catalog from "+catalogUrl+": "+catalog);
            }
        } catch (Exception e) {
            if (Throwables.getRootCause(e) instanceof FileNotFoundException) {
                Object nonDefaultUrl = getConfig().getRawConfig(BROOKLYN_CATALOG_URL);
                if (nonDefaultUrl!=null && !"".equals(nonDefaultUrl)) {
                    log.warn("Could not find catalog XML specified at "+nonDefaultUrl+"; using default (local classpath) catalog. Error was: "+e);
                } else {
                    if (log.isDebugEnabled())
                        log.debug("No default catalog file available; trying again using local classpath to populate catalog. Error was: "+e);
                }
            } else {
                log.warn("Error importing catalog XML at "+catalogUrl+"; using default (local classpath) catalog. Error was: "+e, e);                
            }
        }
        if (catalog==null) {
            // retry, either an error, or was blank
            catalog = new BasicBrooklynCatalog(this, CatalogDtoUtils.newDefaultLocalScanningDto(CatalogScanningModes.TYPES));
            if (log.isDebugEnabled())
                log.debug("Loaded default (local classpath) catalog: "+catalog);
        }
        catalog.getCatalog().load(this, null);
        
        this.catalog = catalog;
    }

    /** Optional class-loader that this management context should use as its base,
     * as the first-resort in the catalog, and for scanning (if scanning the default in the catalog).
     * In most instances the default classloader (ManagementContext.class.getClassLoader(), assuming
     * this was in the JARs used at boot time) is fine, and in those cases this method normally returns null.
     * (Surefire does some weird stuff, but the default classloader is fine for loading;
     * however it requires a custom base classpath to be set for scanning.)
     */
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
    public void setBaseClassPathForScanning(Iterable<URL> urls) {
        if (baseClassPathForScanning == urls) return;
        if (baseClassPathForScanning != null) throw new IllegalStateException("Cannot change base class path for scanning (in "+this+")");
        if (catalog != null) throw new IllegalStateException("Cannot set base class path for scanning after catalog has been loaded (in "+this+")");
        this.baseClassPathForScanning = urls;
    }
    /** @See {@link #setBaseClassPathForScanning(Iterable)} */
    public Iterable<URL> getBaseClassPathForScanning() {
        return baseClassPathForScanning;
    }
    
}
