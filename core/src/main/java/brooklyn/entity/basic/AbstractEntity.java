package brooklyn.entity.basic;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.config.ConfigKey.HasConfigKey;
import brooklyn.enricher.basic.AbstractEnricher;
import brooklyn.entity.Application;
import brooklyn.entity.Effector;
import brooklyn.entity.Entity;
import brooklyn.entity.EntityType;
import brooklyn.entity.Group;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.proxying.InternalEntityFactory;
import brooklyn.entity.rebind.BasicEntityRebindSupport;
import brooklyn.entity.rebind.RebindSupport;
import brooklyn.event.AttributeSensor;
import brooklyn.event.Sensor;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.event.basic.AttributeMap;
import brooklyn.event.basic.AttributeSensorAndConfigKey;
import brooklyn.event.basic.BasicNotificationSensor;
import brooklyn.internal.storage.Reference;
import brooklyn.internal.storage.impl.BasicReference;
import brooklyn.location.Location;
import brooklyn.management.EntityManager;
import brooklyn.management.ExecutionContext;
import brooklyn.management.ManagementContext;
import brooklyn.management.SubscriptionContext;
import brooklyn.management.SubscriptionHandle;
import brooklyn.management.Task;
import brooklyn.management.internal.EffectorUtils;
import brooklyn.management.internal.EntityManagementSupport;
import brooklyn.management.internal.ManagementContextInternal;
import brooklyn.management.internal.SubscriptionTracker;
import brooklyn.mementos.EntityMemento;
import brooklyn.policy.Enricher;
import brooklyn.policy.Policy;
import brooklyn.policy.basic.AbstractPolicy;
import brooklyn.util.BrooklynLanguageExtensions;
import brooklyn.util.collections.MutableList;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.flags.FlagUtils;
import brooklyn.util.flags.SetFromFlag;
import brooklyn.util.task.DeferredSupplier;
import brooklyn.util.text.Identifiers;

import com.google.common.annotations.Beta;
import com.google.common.base.Objects;
import com.google.common.base.Objects.ToStringHelper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

/**
 * Default {@link Entity} implementation, which should be extended whenever implementing an entity.
 * <p>
 * Provides several common fields ({@link #name}, {@link #id}), and supports the core features of
 * an entity such as configuration keys, attributes, subscriptions and effector invocation.
 * <p>
 * If a sub-class is creating other entities, this should be done in an overridden {@link #init()}
 * method.
 * <p>
 * Note that config is typically inherited by children, whereas the fields and attributes are not.
 * <p>
 * Though currently Groovy code, this is very likely to change to pure Java in a future release of 
 * Brooklyn so Groovy'isms should not be relied on.
 * <p>
 * Sub-classes should have a no-argument constructor. When brooklyn creates an entity, it will:
 * <ol>
 *   <li>Construct the entity via the no-argument constructor
 *   <li>Call {@link #setDisplayName(String)}
 *   <li>Call {@link #setManagementContext(ManagementContextInternal)}
 *   <li>Call {@link #setProxy(Entity)}; the proxy should be used by everything else when referring 
 *       to this entity (except for drivers/policies that are attached to the entity, which can be  
 *       given a reference to this entity itself).
 *   <li>Call {@link #configure(Map)} and then {@link #setConfig(ConfigKey, Object)}
 *   <li>Call {@link #init()}
 *   <li>Call {@link #addPolicy()} (for any policies defined in the {@link EntitySpec})
 *   <li>Call {@link #setParent(Entity)}, if a parent is specified in the {@link EntitySpec}
 * </ol>
 * <p>
 * The legacy (pre 0.5) mechanism for creating entities is for others to call the constructor directly.
 * This is now deprecated.
 */
public abstract class AbstractEntity implements EntityLocal, EntityInternal {
    
    protected static final Logger LOG = LoggerFactory.getLogger(AbstractEntity.class);
    static { BrooklynLanguageExtensions.init(); }
    
    public static BasicNotificationSensor<Sensor> SENSOR_ADDED = new BasicNotificationSensor<Sensor>(Sensor.class,
            "entity.sensor.added", "Sensor dynamically added to entity");
    public static BasicNotificationSensor<Sensor> SENSOR_REMOVED = new BasicNotificationSensor<Sensor>(Sensor.class,
            "entity.sensor.removed", "Sensor dynamically removed from entity");

    public static BasicNotificationSensor<PolicyDescriptor> POLICY_ADDED = new BasicNotificationSensor<PolicyDescriptor>(PolicyDescriptor.class,
            "entity.policy.added", "Policy dynamically added to entity");
    public static BasicNotificationSensor<PolicyDescriptor> POLICY_REMOVED = new BasicNotificationSensor<PolicyDescriptor>(PolicyDescriptor.class,
            "entity.policy.removed", "Policy dynamically removed from entity");

    @SetFromFlag(value="id")
    private String id = Identifiers.makeRandomId(8);
    
    private boolean displayNameAutoGenerated = true;
    private String _displayName;
    
    private Entity selfProxy;
    private volatile Application application;
    
    // TODO Because some things still don't use EntitySpec (e.g. the EntityFactory stuff for cluster/fabric),
    // then we need temp vals here. When setManagementContext is called, we'll switch these out for the read-deal;
    // i.e. for the values backed by storage
    private Reference<Entity> parent = new BasicReference<Entity>();
    private Set<Group> groups = Sets.newLinkedHashSet();
    private Set<Entity> children = Sets.newLinkedHashSet();
    private Reference<List<Location>> locations = new BasicReference<List<Location>>(ImmutableList.<Location>of()); // dups removed in addLocations

    private final long creationTimeUtc;

    Map<String,Object> presentationAttributes = Maps.newLinkedHashMap();
    Collection<AbstractPolicy> policies = Lists.newCopyOnWriteArrayList();
    Collection<AbstractEnricher> enrichers = Lists.newCopyOnWriteArrayList();

    // FIXME we do not currently support changing parents, but to implement a cluster that can shrink we need to support at least
    // orphaning (i.e. removing ownership). This flag notes if the entity has previously had a parent, and if an attempt is made to
    // set a new parent an exception will be thrown.
    boolean previouslyOwned = false;

    /**
     * Whether we are still being constructed, in which case never warn in "assertNotYetOwned"
     */
    private boolean inConstruction = true;
    
    private final EntityDynamicType entityType;
    
    protected final EntityManagementSupport managementSupport = new EntityManagementSupport(this);

    /**
     * The config values of this entity. Updating this map should be done
     * via getConfig/setConfig.
     */
    // TODO Assigning temp value because not everything uses EntitySpec; see setManagementContext()
    private EntityConfigMap configsInternal = new EntityConfigMap(this, Maps.<ConfigKey<?>, Object>newLinkedHashMap());

    /**
     * The sensor-attribute values of this entity. Updating this map should be done
     * via getAttribute/setAttribute; it will automatically emit an attribute-change event.
     */
    // TODO Assigning temp value because not everything uses EntitySpec; see setManagementContext()
    private AttributeMap attributesInternal = new AttributeMap(this, Maps.<Collection<String>, Object>newLinkedHashMap());

    /**
     * For temporary data, e.g. timestamps etc for calculating real attribute values, such as when
     * calculating averages over time etc.
     * 
     * @deprecated since 0.6; use attributes
     */
    @Deprecated
    protected final Map<String,Object> tempWorkings = Maps.newLinkedHashMap();

    protected transient SubscriptionTracker _subscriptionTracker;

    private final boolean _legacyConstruction;
    
    public AbstractEntity() {
        this(Maps.newLinkedHashMap(), null);
    }

    /**
     * @deprecated since 0.5; instead use no-arg constructor with EntityManager().createEntity(spec)
     */
    @Deprecated
    public AbstractEntity(Map flags) {
        this(flags, null);
    }

    /**
     * @deprecated since 0.5; instead use no-arg constructor with EntityManager().createEntity(spec)
     */
    @Deprecated
    public AbstractEntity(Entity parent) {
        this(Maps.newLinkedHashMap(), parent);
    }

    // FIXME don't leak this reference in constructor - even to utils
    /**
     * @deprecated since 0.5; instead use no-arg constructor with EntityManager().createEntity(spec)
     */
    @Deprecated
    public AbstractEntity(Map flags, Entity parent) {
        creationTimeUtc = System.currentTimeMillis();
        
        if (flags==null) {
            throw new IllegalArgumentException("Flags passed to entity "+this+" must not be null (try no-arguments or empty map)");
        }
        if (flags.get("parent") != null && parent != null && flags.get("parent") != parent) {
            throw new IllegalArgumentException("Multiple parents supplied, "+flags.get("parent")+" and "+parent);
        }
        if (flags.get("owner") != null && parent != null && flags.get("owner") != parent) {
            throw new IllegalArgumentException("Multiple parents supplied with flags.parent, "+flags.get("owner")+" and "+parent);
        }
        if (flags.get("parent") != null && flags.get("owner") != null && flags.get("parent") != flags.get("owner")) {
            throw new IllegalArgumentException("Multiple parents supplied with flags.parent and flags.owner, "+flags.get("parent")+" and "+flags.get("owner"));
        }
        if (parent != null) {
            flags.put("parent", parent);
        }
        if (flags.get("owner") != null) {
            LOG.warn("Use of deprecated \"flags.owner\" instead of \"flags.parent\" for entity {}", this);
            flags.put("parent", flags.get("owner"));
            flags.remove("owner");
        }

        // TODO Don't let `this` reference escape during construction
        entityType = new EntityDynamicType(this);
        
        _legacyConstruction = !InternalEntityFactory.FactoryConstructionTracker.isConstructing();
        
        if (_legacyConstruction) {
            LOG.warn("Deprecated use of old-style entity construction for "+getClass().getName()+"; instead use EntityManager().createEntity(spec)");
            AbstractEntity checkWeGetThis = configure(flags);
            assert this.equals(checkWeGetThis) : this+" configure method does not return itself; returns "+checkWeGetThis+" instead";
        }
        
        inConstruction = false;
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
    
    @Override
    public boolean equals(Object o) {
        return o != null && (o == this || o == selfProxy);
    }
    
    protected boolean isLegacyConstruction() {
        return _legacyConstruction;
    }
    
    @Override
    public String getId() {
        return id;
    }
    
    public void setProxy(Entity proxy) {
        if (selfProxy != null) throw new IllegalStateException("Proxy is already set; cannot reset proxy for "+toString());
        selfProxy = checkNotNull(proxy, "proxy");
    }
    
    public Entity getProxy() {
        return selfProxy;
    }
    
    /**
     * Returns the proxy, or if not available (because using legacy code) then returns the real entity.
     * This method will be deleted in a future release; it will be kept while deprecated legacy code
     * still exists that creates entities without setting the proxy.
     */
    @Beta
    public Entity getProxyIfAvailable() {
        return getProxy()!=null ? getProxy() : this;
    }
    
    /** sets fields from flags; can be overridden if needed, subclasses should
     * set custom fields before _invoking_ this super
     * (and they nearly always should invoke the super)
     * <p>
     * note that it is usually preferred to use the SetFromFlag annotation on relevant fields
     * so they get set automatically by this method and overriding it is unnecessary
     *
     * @return this entity, for fluent style initialization
     */
    public AbstractEntity configure() {
        return configure(Maps.newLinkedHashMap());
    }
    
    @Override
    public AbstractEntity configure(Map flags) {
        assertNotYetManaged();
        // TODO use a config bag instead
//        ConfigBag bag = new ConfigBag().putAll(flags);
        
        // FIXME Need to set parent with proxy, rather than `this`
        Entity suppliedParent = (Entity) flags.remove("parent");
        if (suppliedParent != null) {
            suppliedParent.addChild(getProxyIfAvailable());
        }
        
        Map<ConfigKey,?> suppliedOwnConfig = (Map<ConfigKey, ?>) flags.remove("config");
        if (suppliedOwnConfig != null) {
            for (Map.Entry<ConfigKey, ?> entry : suppliedOwnConfig.entrySet()) {
                setConfigEvenIfOwned(entry.getKey(), entry.getValue());
            }
        }

        if (flags.get("displayName") != null) {
            _displayName = (String) flags.remove("displayName");
            displayNameAutoGenerated = false;
        } else if (flags.get("name") != null) {
            _displayName = (String) flags.remove("name");
            displayNameAutoGenerated = false;
        } else if (isLegacyConstruction()) {
            _displayName = getClass().getSimpleName()+":"+id.substring(0, 4);
            displayNameAutoGenerated = true;
        }
        
        // allow config keys, and fields, to be set from these flags if they have a SetFromFlag annotation
        // TODO use the setDefaultVals variant
        flags = FlagUtils.setFieldsFromFlags(flags, this);
        flags = FlagUtils.setAllConfigKeys(flags, this);
        
        // all config keys specified in map should be set as config
        for (Iterator<Map.Entry> fi = flags.entrySet().iterator(); fi.hasNext();) {
            Map.Entry entry = fi.next();
            Object k = entry.getKey();
            if (k instanceof HasConfigKey) k = ((HasConfigKey)k).getConfigKey();
            if (k instanceof ConfigKey) {
                setConfigEvenIfOwned((ConfigKey)k, entry.getValue());
                fi.remove();
            }
        }
        
        if (!flags.isEmpty()) {
            LOG.warn("Unsupported flags when configuring {}; ignoring: {}", this, flags);
        }

        return this;
    }
    
    /**
     * Sets a config key value, and returns this Entity instance for use in fluent-API style coding.
     */
    public <T> AbstractEntity configure(ConfigKey<T> key, T value) {
        setConfig(key, value);
        return this;
    }
    public <T> AbstractEntity configure(ConfigKey<T> key, String value) {
        setConfig((ConfigKey)key, value);
        return this;
    }
    public <T> AbstractEntity configure(HasConfigKey<T> key, T value) {
        setConfig(key, value);
        return this;
    }
    public <T> AbstractEntity configure(HasConfigKey<T> key, String value) {
        setConfig((ConfigKey)key, value);
        return this;
    }

    public void setManagementContext(ManagementContextInternal managementContext) {
        getManagementSupport().setManagementContext(managementContext);
        entityType.setName(getEntityTypeName());
        if (displayNameAutoGenerated) _displayName = getEntityType().getSimpleName()+":"+id.substring(0, 4);
        
        Entity oldParent = parent.get();
        Set<Group> oldGroups = groups;
        Set<Entity> oldChildren = children;
        List<Location> oldLocations = locations.get();
        EntityConfigMap oldConfig = configsInternal;
        AttributeMap oldAttribs = attributesInternal;
        
        parent = managementContext.getStorage().createReference(id+"-parent");
        groups = managementContext.getStorage().createSet(id+"-groups");
        children = managementContext.getStorage().createSet(id+"-children");
        locations = managementContext.getStorage().createNonConcurrentList(id+"-locations");
        
        if (oldParent != null) parent.set(oldParent);
        if (oldGroups != null) groups.addAll(oldGroups);
        if (oldChildren.size() > 0) children.addAll(oldChildren);
        if (oldLocations.size() > 0) locations.set(ImmutableList.copyOf(oldLocations));
        
        configsInternal = new EntityConfigMap(this, managementContext.getStorage().<ConfigKey<?>, Object>createMap(id+"-config"));
        if (oldConfig.getLocalConfig().size() > 0) {
            configsInternal.setLocalConfig(oldConfig.getLocalConfig());
        }
        refreshInheritedConfig();
        
        attributesInternal = new AttributeMap(this, managementContext.getStorage().<Collection<String>, Object>createMap(id+"-attributes"));
        if (oldAttribs.asRawMap().size() > 0) {
            for (Map.Entry<Collection<String>,Object> entry : oldAttribs.asRawMap().entrySet()) {
                attributesInternal.update(entry.getKey(), entry.getValue());
            }
        }
    }

    @Override
    public long getCreationTime() {
        return creationTimeUtc;
    }

    @Override
    public String getDisplayName() {
        return _displayName;
    }
    
    @Override
    public void setDisplayName(String newDisplayName) {
        _displayName = newDisplayName;
        displayNameAutoGenerated = false;
    }
    
    /**
     * Gets the entity type name, to be returned by {@code getEntityType().getName()}.
     * To be called by brooklyn internals only.
     * Can be overridden to customize the name.
     */
    protected String getEntityTypeName() {
        try {
            Class<?> typeClazz = getManagementContext().getEntityManager().getEntityTypeRegistry().getEntityTypeOf(getClass());
            String typeName = typeClazz.getCanonicalName();
            if (typeName == null) typeName = typeClazz.getName();
            return typeName;
        } catch (IllegalArgumentException e) {
            String typeName = getClass().getCanonicalName();
            if (typeName == null) typeName = getClass().getName();
            LOG.warn("Entity type interface not found for entity "+this+"; instead using "+typeName);
            return typeName;
        }
    }
    
    /**
     * Called by framework (in new-style entities) after configuring, setting parent, etc,
     * but before a reference to this entity is shared with other entities.
     * 
     * To preserve backwards compatibility for if the entity is constructed directly, one
     * can add to the start method the code below, but that means it will be called after
     * references to this entity have been shared with other entities.
     * <pre>
     * {@code
     * if (isLegacyConstruction()) {
     *     init();
     * }
     * }
     * </pre>
     */
    public void init() {
        // no-op
    }
    
    /**
     * Adds this as a child of the given entity; registers with application if necessary.
     */
    @Override
    public AbstractEntity setParent(Entity entity) {
        if (parent.get() != null) {
            // If we are changing to the same parent...
            if (parent.get().equals(entity)) return this;
            // If we have a parent but changing to orphaned...
            if (entity==null) { clearParent(); return this; }
            
            // We have a parent and are changing to another parent...
            throw new UnsupportedOperationException("Cannot change parent of "+this+" from "+parent+" to "+entity+" (parent change not supported)");
        }
        // If we have previously had a parent and are trying to change to another one...
        if (previouslyOwned && entity != null)
            throw new UnsupportedOperationException("Cannot set a parent of "+this+" because it has previously had a parent");
        // We don't have a parent, never have and are changing to having a parent...

        //make sure there is no loop
        if (this.equals(entity)) throw new IllegalStateException("entity "+this+" cannot own itself");
        //this may be expensive, but preferable to throw before setting the parent!
        if (Entities.isDescendant(this, entity))
            throw new IllegalStateException("loop detected trying to set parent of "+this+" as "+entity+", which is already a descendent");
        
        parent.set(entity);
        //previously tested entity!=null but that should be guaranteed?
        entity.addChild(getProxyIfAvailable());
        refreshInheritedConfig();
        previouslyOwned = true;
        
        getApplication();
        
        return this;
    }

    @Override
    public void clearParent() {
        if (parent.get() == null) return;
        Entity oldParent = parent.get();
        parent.set(null);
        if (oldParent != null) oldParent.removeChild(getProxyIfAvailable());
    }
    
    /**
     * Adds the given entity as a child of this parent <em>and</em> sets this entity as the parent of the child;
     * returns argument passed in, for convenience.
     */
    @Override
    public <T extends Entity> T addChild(T child) {
        checkNotNull(child, "child must not be null (for entity %s)", this);
        synchronized (children) {
            if (Entities.isAncestor(this, child)) throw new IllegalStateException("loop detected trying to add child "+child+" to "+this+"; it is already an ancestor");
            child.setParent(getProxyIfAvailable());
            children.add(child);
            
            getManagementSupport().getEntityChangeListener().onChildrenChanged();
        }
        return child;
    }

    /**
     * Creates an entity using the given spec, and adds it as a child of this entity.
     * 
     * @see #addChild(Entity)
     * @see EntityManager#createEntity(EntitySpec)
     * 
     * @throws IllegalArgumentException If {@code spec.getParent()} is set and is different from this entity
     */
    @Override
    public <T extends Entity> T addChild(EntitySpec<T> spec) {
        if (spec.getParent() != null && !this.equals(spec.getParent())) {
            throw new IllegalArgumentException("Attempt to create child of "+this+" with entity spec "+spec+
                " failed because spec has different parent: "+spec.getParent());
        }
        return addChild(getEntityManager().createEntity(spec));
    }
    
    @Override
    public boolean removeChild(Entity child) {
        synchronized (children) {
            boolean changed = children.remove(child);
            child.clearParent();
            
            if (changed) {
                getManagementSupport().getEntityChangeListener().onChildrenChanged();
            }
            return changed;
        }
    }

    /**
     * Adds this as a member of the given group, registers with application if necessary
     */
    @Override
    public void addGroup(Group e) {
        groups.add(e);
        getApplication();
    }

    @Override
    public Entity getParent() {
        return parent.get();
    }

    @Override
    public Collection<Entity> getChildren() {
        return ImmutableList.copyOf(children);
    }
    
    @Override
    public Collection<Group> getGroups() { 
        return ImmutableList.copyOf(groups);
    }

    /**
     * Returns the application, looking it up if not yet known (registering if necessary)
     */
    @Override
    public Application getApplication() {
        if (application != null) return application;
        Entity parent = getParent();
        Application app = (parent != null) ? parent.getApplication() : null;
        if (app != null) {
            if (getManagementSupport().isFullyManaged())
                // only do this once fully managed, in case root app becomes parented
                setApplication(app);
        }
        return app;
    }

    // FIXME Can this really be deleted? Overridden by AbstractApplication; needs careful review
    /** @deprecated since 0.4.0 should not be needed / leaked outwith brooklyn internals / mgmt support? */
    protected synchronized void setApplication(Application app) {
        if (application != null) {
            if (application.getId() != app.getId()) {
                throw new IllegalStateException("Cannot change application of entity (attempted for "+this+" from "+getApplication()+" to "+app);
            }
        }
        this.application = app;
    }

    @Override
    public String getApplicationId() {
        Application app = getApplication();
        return (app == null) ? null : app.getId();
    }

    @Override
    public synchronized ManagementContext getManagementContext() {
        return getManagementSupport().getManagementContext();
    }

    protected EntityManager getEntityManager() {
        return getManagementContext().getEntityManager();
    }
    
    @Override
    public EntityType getEntityType() {
        return entityType.getSnapshot();
    }

    /** returns the dynamic type corresponding to the type of this entity instance */
    public EntityDynamicType getMutableEntityType() {
        return entityType;
    }
    
    @Override
    public Collection<Location> getLocations() {
        synchronized (locations) {
            return ImmutableList.copyOf(locations.get());
        }
    }

    @Override
    public void addLocations(Collection<? extends Location> newLocations) {
        synchronized (locations) {
            List<Location> oldLocations = locations.get();
            Set<Location> truelyNewLocations = Sets.newLinkedHashSet(newLocations);
            truelyNewLocations.removeAll(oldLocations);
            if (truelyNewLocations.size() > 0) {
                locations.set(ImmutableList.<Location>builder().addAll(oldLocations).addAll(truelyNewLocations).build());
            }
        }
        getManagementSupport().getEntityChangeListener().onLocationsChanged();
    }

    @Override
    public void removeLocations(Collection<? extends Location> removedLocations) {
        synchronized (locations) {
            List<Location> oldLocations = locations.get();
            locations.set(MutableList.<Location>builder().addAll(oldLocations).removeAll(removedLocations).buildImmutable());
        }
        getManagementSupport().getEntityChangeListener().onLocationsChanged();
    }
    
    @Override
    public void clearLocations() {
        synchronized (locations) {
            locations.set(ImmutableList.<Location>of());
        }
        getManagementSupport().getEntityChangeListener().onLocationsChanged();
    }

    public Location firstLocation() {
        synchronized (locations) {
            return Iterables.get(locations.get(), 0);
        }
    }
    
    /**
     * Should be invoked at end-of-life to clean up the item.
     */
    @Override
    public void destroy() {
        // TODO we need some way of deleting stale items
    }

    @Override
    public <T> T getAttribute(AttributeSensor<T> attribute) {
        return attributesInternal.getValue(attribute);
    }

    public <T> T getAttributeByNameParts(List<String> nameParts) {
        return (T) attributesInternal.getValue(nameParts);
    }
    
    @Override
    public <T> T setAttribute(AttributeSensor<T> attribute, T val) {
        T result = attributesInternal.update(attribute, val);
        if (result == null) {
            // could be this is a new sensor
            entityType.addSensorIfAbsent(attribute);
        }
        
        getManagementSupport().getEntityChangeListener().onAttributeChanged(attribute);
        return result;
    }

    @Override
    public <T> T setAttributeWithoutPublishing(AttributeSensor<T> attribute, T val) {
        T result = attributesInternal.updateWithoutPublishing(attribute, val);
        if (result == null) {
            // could be this is a new sensor
            entityType.addSensorIfAbsentWithoutPublishing(attribute);
        }
        
        getManagementSupport().getEntityChangeListener().onAttributeChanged(attribute);
        return result;
    }

    public void removeAttribute(AttributeSensor<?> attribute) {
        attributesInternal.remove(attribute);
        entityType.removeSensor(attribute);
    }

    /** sets the value of the given attribute sensor from the config key value herein,
     * if the config key resolves to a non-null value as a sensor
     * <p>
     * returns old value */
    @Override
    public <T> T setAttribute(AttributeSensorAndConfigKey<?,T> configuredSensor) {
        T v = getAttribute(configuredSensor);
        if (v!=null) return v;
        v = configuredSensor.getAsSensorValue(this);
        if (v!=null) return setAttribute(configuredSensor, v);
        return null;
    }

    @Override
    public <T> T getConfig(ConfigKey<T> key) {
        return configsInternal.getConfig(key);
    }
    
    @Override
    public <T> T getConfig(HasConfigKey<T> key) {
        return configsInternal.getConfig(key);
    }
    
    @Override
    public <T> T getConfig(HasConfigKey<T> key, T defaultValue) {
        return configsInternal.getConfig(key, defaultValue);
    }
    
    //don't use groovy defaults for defaultValue as that doesn't implement the contract; we need the above
    @Override
    public <T> T getConfig(ConfigKey<T> key, T defaultValue) {
        return configsInternal.getConfig(key, defaultValue);
    }

    /**
     * @deprecated since 0.6; use assertNotYetManaged
     */
    @Deprecated
    protected void assertNotYetOwned() {
        assertNotYetManaged();
    }
    
    protected void assertNotYetManaged() {
        if (!inConstruction && getManagementSupport().isDeployed()) {
            LOG.warn("configuration being made to {} after deployment; may not be supported in future versions", this);
        }
        //throw new IllegalStateException("Cannot set configuration "+key+" on active entity "+this)
    }

    @Override
    public <T> T setConfig(ConfigKey<T> key, T val) {
        assertNotYetManaged();
        return (T) configsInternal.setConfig(key, val);
    }

    @Override
    public <T> T setConfig(ConfigKey<T> key, Task<T> val) {
        assertNotYetManaged();
        return (T) configsInternal.setConfig(key, val);
    }

    public <T> T setConfig(ConfigKey<T> key, DeferredSupplier val) {
        assertNotYetManaged();
        return (T) configsInternal.setConfig(key, val);
    }

    @Override
    public <T> T setConfig(HasConfigKey<T> key, T val) {
        return setConfig(key.getConfigKey(), val);
    }

    @Override
    public <T> T setConfig(HasConfigKey<T> key, Task<T> val) {
        return (T) setConfig(key.getConfigKey(), val);
    }

    public <T> T setConfig(HasConfigKey<T> key, DeferredSupplier val) {
        return setConfig(key.getConfigKey(), val);
    }

    public <T> T setConfigEvenIfOwned(ConfigKey<T> key, T val) {
        return (T) configsInternal.setConfig(key, val);
    }

    public <T> T setConfigEvenIfOwned(HasConfigKey<T> key, T val) {
        return setConfigEvenIfOwned(key.getConfigKey(), val);
    }

    protected void setConfigIfValNonNull(ConfigKey key, Object val) {
        if (val != null) setConfig(key, val);
    }
    
    protected void setConfigIfValNonNull(HasConfigKey key, Object val) {
        if (val != null) setConfig(key, val);
    }

    @Override
    public void refreshInheritedConfig() {
        if (getParent() != null) {
            configsInternal.setInheritedConfig(((EntityInternal)getParent()).getAllConfig());
        } else {
            configsInternal.clearInheritedConfig();
        }

        refreshInheritedConfigOfChildren();
    }

    void refreshInheritedConfigOfChildren() {
        for (Entity it : getChildren()) {
            ((EntityInternal)it).refreshInheritedConfig();
        }
    }

    @Override
    public EntityConfigMap getConfigMap() {
        return configsInternal;
    }
    
    @Override
    public Map<ConfigKey<?>,Object> getAllConfig() {
        return configsInternal.getAllConfig();
    }

    @Override
    public Map<AttributeSensor, Object> getAllAttributes() {
        Map<AttributeSensor, Object> result = Maps.newLinkedHashMap();
        Map<String, Object> attribs = attributesInternal.asMap();
        for (Map.Entry<String,Object> entry : attribs.entrySet()) {
            AttributeSensor attribKey = (AttributeSensor) entityType.getSensor(entry.getKey());
            if (attribKey == null) {
                LOG.warn("When retrieving all attributes of {}, ignoring attribute {} because no matching AttributeSensor found", this, entry.getKey());
            } else {
                result.put(attribKey, entry.getValue());
            }
        }
        return result;
    }

    /** @see EntityLocal#subscribe */
    @Override
    public <T> SubscriptionHandle subscribe(Entity producer, Sensor<T> sensor, SensorEventListener<? super T> listener) {
        return getSubscriptionTracker().subscribe(producer, sensor, listener);
    }

    /** @see EntityLocal#subscribeToChildren */
    @Override
    public <T> SubscriptionHandle subscribeToChildren(Entity parent, Sensor<T> sensor, SensorEventListener<? super T> listener) {
        return getSubscriptionTracker().subscribeToChildren(parent, sensor, listener);
    }

    /** @see EntityLocal#subscribeToMembers */
    @Override
    public <T> SubscriptionHandle subscribeToMembers(Group group, Sensor<T> sensor, SensorEventListener<? super T> listener) {
        return getSubscriptionTracker().subscribeToMembers(group, sensor, listener);
    }

    /**
     * Unsubscribes the given producer.
     *
     * @see SubscriptionContext#unsubscribe(SubscriptionHandle)
     */
    @Override
    public boolean unsubscribe(Entity producer) {
        return getSubscriptionTracker().unsubscribe(producer);
    }

    /**
     * Unsubscribes the given handle.
     *
     * @see SubscriptionContext#unsubscribe(SubscriptionHandle)
     */
    @Override
    public boolean unsubscribe(Entity producer, SubscriptionHandle handle) {
        return getSubscriptionTracker().unsubscribe(producer, handle);
    }

    @Override
    public synchronized SubscriptionContext getSubscriptionContext() {
        return getManagementSupport().getSubscriptionContext();
    }

    protected synchronized SubscriptionTracker getSubscriptionTracker() {
        if (_subscriptionTracker == null) {
            _subscriptionTracker = new SubscriptionTracker(getSubscriptionContext());
        }
        return _subscriptionTracker;
    }
    
    @Override
    public synchronized ExecutionContext getExecutionContext() {
        return getManagementSupport().getExecutionContext();
    }

    /** Default String representation is simplified name of class, together with selected fields. */
    @Override
    public String toString() {
        return toStringHelper().toString();
    }
    
    /**
     * Override this to add to the toString(), e.g. {@code return super.toStringHelper().add("port", port);}
     *
     * Cannot be used in combination with overriding the deprecated toStringFieldsToInclude.
     */
    protected ToStringHelper toStringHelper() {
        return Objects.toStringHelper(this).omitNullValues().add("id", getId());
//            make output more concise by suppressing display name
//            .add("name", getDisplayName());
    }
    
    
    // -------- POLICIES --------------------

    @Override
    public Collection<Policy> getPolicies() {
        return ImmutableList.<Policy>copyOf(policies);
    }

    @Override
    public void addPolicy(Policy policy) {
        policies.add((AbstractPolicy)policy);
        ((AbstractPolicy)policy).setEntity(this);
        
        getManagementSupport().getEntityChangeListener().onPoliciesChanged();
        emit(AbstractEntity.POLICY_ADDED, new PolicyDescriptor(policy));
    }

    @Override
    public boolean removePolicy(Policy policy) {
        ((AbstractPolicy)policy).destroy();
        boolean changed = policies.remove(policy);
        
        if (changed) {
            getManagementSupport().getEntityChangeListener().onPoliciesChanged();
            emit(AbstractEntity.POLICY_REMOVED, new PolicyDescriptor(policy));
        }
        return changed;
    }
    
    @Override
    public boolean removeAllPolicies() {
        boolean changed = false;
        for (Policy policy : policies) {
            removePolicy(policy);
            changed = true;
        }
        
        if (changed) {
            getManagementSupport().getEntityChangeListener().onPoliciesChanged();
        }
        
        return changed;
    }
    
    @Override
    public Collection<Enricher> getEnrichers() {
        return ImmutableList.<Enricher>copyOf(enrichers);
    }

    @Override
    public void addEnricher(Enricher enricher) {
        enrichers.add((AbstractEnricher) enricher);
        ((AbstractEnricher)enricher).setEntity(this);
    }

    @Override
    public boolean removeEnricher(Enricher enricher) {
        ((AbstractEnricher)enricher).destroy();
        return enrichers.remove(enricher);
    }

    @Override
    public boolean removeAllEnrichers() {
        boolean changed = false;
        for (AbstractEnricher enricher : enrichers) {
            changed = removeEnricher(enricher) || changed;
        }
        return changed;
    }
    
    // -------- SENSORS --------------------

    @Override
    public <T> void emit(Sensor<T> sensor, T val) {
        if (sensor instanceof AttributeSensor) {
            LOG.warn("Strongly discouraged use of emit with attribute sensor "+sensor+" "+val+"; use setAttribute instead!",
                new Throwable("location of discouraged attribute "+sensor+" emit"));
        }
        if (val instanceof SensorEvent) {
            LOG.warn("Strongly discouraged use of emit with sensor event as value "+sensor+" "+val+"; value should be unpacked!",
                new Throwable("location of discouraged event "+sensor+" emit"));
        }
        if (LOG.isDebugEnabled()) LOG.debug("Emitting sensor notification {} value {} on {}", new Object[] {sensor.getName(), val, this});
        emitInternal(sensor, val);
    }
    
    public <T> void emitInternal(Sensor<T> sensor, T val) {
        SubscriptionContext subsContext = getSubscriptionContext();
        if (subsContext != null) subsContext.publish(sensor.newEvent(getProxyIfAvailable(), val));
    }

    // -------- EFFECTORS --------------

    /** Convenience for finding named effector in {@link #getEffectors()} {@link Map}. */
    public <T> Effector<T> getEffector(String effectorName) {
        return (Effector<T>) entityType.getEffector(effectorName);
    }

    /** Invoke an {@link Effector} directly. */
    public <T> Task<T> invoke(Effector<T> eff) {
        return invoke(MutableMap.of(), eff);
    }
    
    public <T> Task<T> invoke(Map parameters, Effector<T> eff) {
        return invoke(eff, parameters);
    }

    /**
     * Additional form supplied for when the parameter map needs to be made explicit.
     *
     * @see #invoke(Effector)
     */
    @Override
    public <T> Task<T> invoke(Effector<T> eff, Map<String,?> parameters) {
        return EffectorUtils.invokeEffectorAsync(this, eff, parameters);
    }

    /**
     * Invoked by {@link EntityManagementSupport} when this entity is becoming managed (i.e. it has a working
     * management context, but before the entity is visible to other entities).
     */
    public void onManagementStarting() {
        if (isLegacyConstruction()) {
            entityType.setName(getEntityTypeName());
            if (displayNameAutoGenerated) _displayName = getEntityType().getSimpleName()+":"+id.substring(0, 4);
        }
    }
    
    /**
     * Invoked by {@link EntityManagementSupport} when this entity is fully managed and visible to other entities
     * through the management context.
     */
    public void onManagementStarted() {}
    
    // FIXME Really deprecated? I don't want folk to have to override createManagementSupport for simple use-cases
    /**
     * Invoked by {@link ManagementContext} when this entity becomes managed at a particular management node,
     * including the initial management started and subsequent management node master-change for this entity.
     * @deprecated since 0.4.0 override EntityManagementSupport.onManagementStarting if customization needed
     */
    public void onManagementBecomingMaster() {}
    
    // FIXME Really deprecated? I don't want folk to have to override createManagementSupport for simple use-cases
    /**
     * Invoked by {@link ManagementContext} when this entity becomes mastered at a particular management node,
     * including the final management end and subsequent management node master-change for this entity.
     * @deprecated since 0.4.0 override EntityManagementSupport.onManagementStopping if customization needed
     */
    public void onManagementNoLongerMaster() {}

    /** For use by management plane, to invalidate all fields (e.g. when an entity is changing to being proxied) */
    public void invalidateReferences() {
        // TODO move this to EntityMangementSupport,
        // when hierarchy fields can also be moved there
        parent.set(null);
        application = null;
        children.clear();
        groups.clear();
    }
    
    @Override
    public EntityManagementSupport getManagementSupport() {
        return managementSupport;
    }

    @Override
    public RebindSupport<EntityMemento> getRebindSupport() {
        return new BasicEntityRebindSupport(this);
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        if (!getManagementSupport().wasDeployed())
            LOG.warn("Entity "+this+" was never deployed -- explicit call to manage(Entity) required.");
    }
}
