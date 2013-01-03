package brooklyn.management.internal;

import java.io.FileNotFoundException;
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
import brooklyn.catalog.internal.CatalogDtoUtils;
import brooklyn.config.BrooklynProperties;
import brooklyn.config.ConfigKey;
import brooklyn.config.StringConfigMap;
import brooklyn.entity.Effector;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractEffector;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.basic.EffectorUtils;
import brooklyn.entity.basic.EntityReferences.EntityCollectionReference;
import brooklyn.entity.drivers.BasicEntityDriverFactory;
import brooklyn.entity.drivers.EntityDriverFactory;
import brooklyn.entity.rebind.RebindManager;
import brooklyn.entity.rebind.RebindManagerImpl;
import brooklyn.entity.trait.Startable;
import brooklyn.event.basic.BasicConfigKey.StringConfigKey;
import brooklyn.location.LocationRegistry;
import brooklyn.location.basic.BasicLocationRegistry;
import brooklyn.management.ExecutionContext;
import brooklyn.management.ManagementContext;
import brooklyn.management.SubscriptionContext;
import brooklyn.management.Task;
import brooklyn.management.internal.ManagementTransitionInfo.ManagementTransitionMode;
import brooklyn.util.GroovyJavaMethods;
import brooklyn.util.MutableList;
import brooklyn.util.MutableMap;
import brooklyn.util.ResourceUtils;
import brooklyn.util.task.BasicExecutionContext;
import brooklyn.util.task.Tasks;
import brooklyn.util.text.Strings;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.base.Throwables;

public abstract class AbstractManagementContext implements ManagementContext  {
    private static final Logger log = LoggerFactory.getLogger(AbstractManagementContext.class);
    public static final String EFFECTOR_TAG = "EFFECTOR";
    public static final String NON_TRANSIENT_TASK_TAG = "NON-TRANSIENT";

    public static final ConfigKey<String> BROOKLYN_CATALOG_URL = new StringConfigKey("brooklyn.catalog.url",
            "The URL of a catalog.xml descriptor; absent for default (~/.brooklyn/catalog.xml), " +
            "or empty for no URL (use default scanner)", "file://~/.brooklyn/catalog.xml");
    
    private final AtomicLong totalEffectorInvocationCount = new AtomicLong();

    protected BrooklynProperties configMap;
    protected BasicLocationRegistry locationRegistry;
    protected volatile BasicBrooklynCatalog catalog;
    protected ClassLoader baseClassLoader;
    protected Iterable<URL> baseClassPathForScanning;

    // TODO leaking "this" reference; yuck
    private final RebindManager rebindManager = new RebindManagerImpl(this);
    
    protected volatile BrooklynGarbageCollector gc;
    
    public AbstractManagementContext(BrooklynProperties brooklynProperties){
       this.configMap = brooklynProperties;
    }
    
    static {
        // ensure that if ResourceUtils is given an entity as context,
        // we use the catalog class loader (e.g. to resolve classpath URLs)
        ResourceUtils.addClassLoaderProvider(new Function<Object, ClassLoader>() {
            @Override 
            public ClassLoader apply(@Nullable Object input) {
                if (input instanceof AbstractEntity) 
                    return apply(((AbstractEntity)input).getManagementSupport());
                if (input instanceof EntityManagementSupport) 
                    return apply(((EntityManagementSupport)input).getManagementContext(true));
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

    private final EntityDriverFactory entityDriverFactory = new BasicEntityDriverFactory();

    @Override
    public EntityDriverFactory getEntityDriverFactory() {
        return entityDriverFactory;
    }
    
    public boolean isManaged(Entity e) {
        return (running && getEntity(e.getId())!=null);
    }
    
    /**
     * Begins management for the given entity and its children, recursively.
     *
     * depending on the implementation of the management context,
     * this might push it out to one or more remote management nodes.
     */
    public void manage(Entity e) {
        if (isManaged(e)) {
//            if (log.isDebugEnabled()) {
                log.warn(""+this+" redundant call to start management of entity (and descendants of) "+e+"; skipping", 
                    new Throwable("source of duplicate management of "+e));
//            }
            return;
        }
        
        final ManagementTransitionInfo info = new ManagementTransitionInfo(this, ManagementTransitionMode.NORMAL);
        recursively(e, new Predicate<AbstractEntity>() { public boolean apply(AbstractEntity it) {
            preManageNonRecursive(it);
            it.getManagementSupport().onManagementStarting(info); 
            return manageNonRecursive(it);
        } });
        
        recursively(e, new Predicate<AbstractEntity>() { public boolean apply(AbstractEntity it) {
            it.getManagementSupport().onManagementStarted(info);
            it.setBeingManaged();
            rebindManager.getChangeListener().onManaged(it);
            return true; 
        } });
    }
    
    protected void recursively(Entity e, Predicate<AbstractEntity> action) {
        action.apply( (AbstractEntity)e );
        EntityCollectionReference<?> ref = ((AbstractEntity)e).getChildrenReference();
        for (String ei: ref.getIds()) {
            Entity entity = ref.peek(ei);
            if (entity==null) entity = getEntity(ei);
            if (entity==null) {
                log.warn("Unable to resolve entity "+ei+" when recursing for management");
            } else {
                recursively( entity, action );
            }
        }
    }

    /**
     * Whether the entity is in the process of being managed.
     */
    protected abstract boolean isPreManaged(Entity e);
    
    /**
     * Implementor-supplied internal method.
     * <p>
     * Should ensure that the entity is now known about, but should not be accessible from other entities yet.
     */
    protected abstract boolean preManageNonRecursive(Entity e);

    /**
     * Implementor-supplied internal method.
     * <p>
     * Should ensure that the entity is now managed somewhere, and known about in all the lists.
     * Returns true if the entity has now become managed; false if it was already managed (anything else throws exception)
     */
    protected abstract boolean manageNonRecursive(Entity e);

    /**
     * Causes the given entity and its children, recursively, to be removed from the management plane
     * (for instance because the entity is no longer relevant)
     */
    public void unmanage(Entity e) {
        if (shouldSkipUnmanagement(e)) return;
        
        final ManagementTransitionInfo info = new ManagementTransitionInfo(this, ManagementTransitionMode.NORMAL);
        recursively(e, new Predicate<AbstractEntity>() { public boolean apply(AbstractEntity it) {
            if (shouldSkipUnmanagement(it)) return false;
            it.getManagementSupport().onManagementStopping(info); 
            return true;
        } });
        
        recursively(e, new Predicate<AbstractEntity>() { public boolean apply(AbstractEntity it) {
            if (shouldSkipUnmanagement(it)) return false;
            boolean result = unmanageNonRecursive(it);            
            it.getManagementSupport().onManagementStopped(info);
            rebindManager.getChangeListener().onUnmanaged(it);
            if (gc != null) gc.onUnmanaged(it);
            return result; 
        } });
    }
    
    protected boolean shouldSkipUnmanagement(Entity e) {
        if (e==null) {
            log.warn(""+this+" call to unmanage null entity; skipping",  
                new IllegalStateException("source of null unmanagement call to "+this));
            return true;
        }
        if (!isManaged(e)) {
            log.warn("{} call to stop management of unknown entity (already unmanaged?) {}; skipping, and all descendants", this, e);
            return true;
        }
        return false;
    }

    /**
     * Implementor-supplied internal method.
     * <p>
     * Should ensure that the entity is no longer managed anywhere, remove from all lists.
     * Returns true if the entity has been removed from management; if it was not previously managed (anything else throws exception) 
     */
    protected abstract boolean unmanageNonRecursive(Entity e);

    public <T> Task<T> invokeEffector(final Entity entity, final Effector<T> eff, @SuppressWarnings("rawtypes") final Map parameters) {
        return runAtEntity(
                MutableMap.builder()
                        .put("description", "invoking "+eff.getName()+" on "+entity.getDisplayName())
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
     * activates management when effector invoked, warning unless context is acceptable
     * (currently only acceptable context is "start")
     */
    protected void manageIfNecessary(Entity entity, Object context) {
        if (!running) {
            return; // TODO Still a race for terminate being called, and then isManaged below returning false
        } else if (((AbstractEntity)entity).hasEverBeenManaged()) {
            return;
        } else if (isManaged(entity)) {
            return;
        } else if (isPreManaged(entity)) {
            return;
        } else {
            Entity rootUnmanaged = entity;
            while (true) {
                Entity candidateUnmanagedParent = rootUnmanaged.getParent();
                if (candidateUnmanagedParent == null || isManaged(candidateUnmanagedParent) || isPreManaged(candidateUnmanagedParent))
                    break;
                rootUnmanaged = candidateUnmanagedParent;
            }
            if (context == Startable.START.getName())
                log.info("Activating local management for {} on start", rootUnmanaged);
            else
                log.warn("Activating local management for {} due to effector invocation on {}: {}", new Object[]{rootUnmanaged, entity, context});
            manage(rootUnmanaged);
        }
    }

    /**
     * Method for entity to make effector happen with correct semantics (right place, right task context),
     * when a method is called on that entity.
     * @throws ExecutionException 
     */
    protected <T> T invokeEffectorMethodSync(final Entity entity, final Effector<T> eff, final Object args) throws ExecutionException {
        try {
            Task<?> current = Tasks.current();
            if (current == null || !current.getTags().contains(entity) || !isManagedLocally(entity)) {
                manageIfNecessary(entity, eff.getName());
                // Wrap in a task if we aren't already in a task that is tagged with this entity
                Task<T> task = runAtEntity(
                        MutableMap.builder()
                                .put("description", "invoking "+eff.getName()+" on "+entity.getDisplayName())
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
