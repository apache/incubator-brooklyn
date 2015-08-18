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
package brooklyn.entity.basic;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.brooklyn.api.entity.Application;
import org.apache.brooklyn.api.entity.Effector;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntityType;
import org.apache.brooklyn.api.entity.Feed;
import org.apache.brooklyn.api.entity.Group;
import org.apache.brooklyn.api.entity.basic.EntityLocal;
import org.apache.brooklyn.api.entity.proxying.EntitySpec;
import org.apache.brooklyn.api.entity.rebind.RebindSupport;
import org.apache.brooklyn.api.event.AttributeSensor;
import org.apache.brooklyn.api.event.Sensor;
import org.apache.brooklyn.api.event.SensorEvent;
import org.apache.brooklyn.api.event.SensorEventListener;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.management.EntityManager;
import org.apache.brooklyn.api.management.ExecutionContext;
import org.apache.brooklyn.api.management.ManagementContext;
import org.apache.brooklyn.api.management.SubscriptionContext;
import org.apache.brooklyn.api.management.SubscriptionHandle;
import org.apache.brooklyn.api.management.Task;
import org.apache.brooklyn.api.mementos.EntityMemento;
import org.apache.brooklyn.api.policy.Enricher;
import org.apache.brooklyn.api.policy.EnricherSpec;
import org.apache.brooklyn.api.policy.EntityAdjunct;
import org.apache.brooklyn.api.policy.Policy;
import org.apache.brooklyn.api.policy.PolicySpec;
import org.apache.brooklyn.core.catalog.internal.CatalogUtils;
import org.apache.brooklyn.core.internal.BrooklynFeatureEnablement;
import org.apache.brooklyn.core.internal.BrooklynInitialization;
import org.apache.brooklyn.core.internal.storage.BrooklynStorage;
import org.apache.brooklyn.core.internal.storage.Reference;
import org.apache.brooklyn.core.internal.storage.impl.BasicReference;
import org.apache.brooklyn.core.management.internal.EffectorUtils;
import org.apache.brooklyn.core.management.internal.EntityManagementSupport;
import org.apache.brooklyn.core.management.internal.ManagementContextInternal;
import org.apache.brooklyn.core.management.internal.SubscriptionTracker;
import org.apache.brooklyn.core.policy.basic.AbstractEntityAdjunct;
import org.apache.brooklyn.core.policy.basic.AbstractPolicy;
import org.apache.brooklyn.core.policy.basic.AbstractEntityAdjunct.AdjunctTagSupport;
import org.apache.brooklyn.core.util.config.ConfigBag;
import org.apache.brooklyn.core.util.flags.FlagUtils;
import org.apache.brooklyn.core.util.flags.TypeCoercions;
import org.apache.brooklyn.core.util.task.DeferredSupplier;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.brooklyn.basic.AbstractBrooklynObject;

import brooklyn.config.BrooklynLogging;
import brooklyn.config.ConfigKey;
import brooklyn.config.ConfigKey.HasConfigKey;
import brooklyn.config.render.RendererHints;
import brooklyn.enricher.basic.AbstractEnricher;
import brooklyn.entity.basic.ServiceStateLogic.ServiceNotUpLogic;
import brooklyn.entity.rebind.BasicEntityRebindSupport;
import brooklyn.event.basic.AttributeMap;
import brooklyn.event.basic.AttributeSensorAndConfigKey;
import brooklyn.event.basic.BasicNotificationSensor;
import brooklyn.event.basic.Sensors;
import brooklyn.event.feed.AbstractFeed;
import brooklyn.event.feed.ConfigToAttributes;

import org.apache.brooklyn.location.basic.Locations;

import brooklyn.util.collections.MutableList;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.collections.MutableSet;
import brooklyn.util.collections.SetFromLiveMap;
import brooklyn.util.guava.Maybe;
import brooklyn.util.javalang.Equals;
import brooklyn.util.text.Strings;

import com.google.common.annotations.Beta;
import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.base.Objects.ToStringHelper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

/**
 * Default {@link Entity} implementation, which should be extended whenever implementing an entity.
 * <p>
 * Provides several common fields ({@link #displayName}, {@link #id}), and supports the core features of
 * an entity such as configuration keys, attributes, subscriptions and effector invocation.
 * <p>
 * If a sub-class is creating other entities, this should be done in an overridden {@link #init()}
 * method.
 * <p>
 * Note that config is typically inherited by children, whereas the fields and attributes are not.
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
 *   <li>Call {@link #addPolicy(Policy)} (for any policies defined in the {@link EntitySpec})
 *   <li>Call {@link #setParent(Entity)}, if a parent is specified in the {@link EntitySpec}
 * </ol>
 * <p>
 * The legacy (pre 0.5) mechanism for creating entities is for others to call the constructor directly.
 * This is now deprecated.
 */
public abstract class AbstractEntity extends AbstractBrooklynObject implements EntityLocal, EntityInternal {
    
    private static final Logger LOG = LoggerFactory.getLogger(AbstractEntity.class);
    
    static { BrooklynInitialization.initAll(); }
    
    public static final BasicNotificationSensor<Location> LOCATION_ADDED = new BasicNotificationSensor<Location>(
            Location.class, "entity.location.added", "Location dynamically added to entity");
    public static final BasicNotificationSensor<Location> LOCATION_REMOVED = new BasicNotificationSensor<Location>(
            Location.class, "entity.location.removed", "Location dynamically removed from entity");

    public static final BasicNotificationSensor<Sensor> SENSOR_ADDED = new BasicNotificationSensor<Sensor>(Sensor.class,
            "entity.sensor.added", "Sensor dynamically added to entity");
    public static final BasicNotificationSensor<Sensor> SENSOR_REMOVED = new BasicNotificationSensor<Sensor>(Sensor.class,
            "entity.sensor.removed", "Sensor dynamically removed from entity");

    public static final BasicNotificationSensor<String> EFFECTOR_ADDED = new BasicNotificationSensor<String>(String.class,
            "entity.effector.added", "Effector dynamically added to entity");
    public static final BasicNotificationSensor<String> EFFECTOR_REMOVED = new BasicNotificationSensor<String>(String.class,
            "entity.effector.removed", "Effector dynamically removed from entity");
    public static final BasicNotificationSensor<String> EFFECTOR_CHANGED = new BasicNotificationSensor<String>(String.class,
            "entity.effector.changed", "Effector dynamically changed on entity");

    public static final BasicNotificationSensor<PolicyDescriptor> POLICY_ADDED = new BasicNotificationSensor<PolicyDescriptor>(PolicyDescriptor.class,
            "entity.policy.added", "Policy dynamically added to entity");
    public static final BasicNotificationSensor<PolicyDescriptor> POLICY_REMOVED = new BasicNotificationSensor<PolicyDescriptor>(PolicyDescriptor.class,
            "entity.policy.removed", "Policy dynamically removed from entity");

    public static final BasicNotificationSensor<Entity> CHILD_ADDED = new BasicNotificationSensor<Entity>(Entity.class,
            "entity.children.added", "Child dynamically added to entity");
    public static final BasicNotificationSensor<Entity> CHILD_REMOVED = new BasicNotificationSensor<Entity>(Entity.class,
            "entity.children.removed", "Child dynamically removed from entity");

    public static final BasicNotificationSensor<Group> GROUP_ADDED = new BasicNotificationSensor<Group>(Group.class,
            "entity.group.added", "Group dynamically added to entity");
    public static final BasicNotificationSensor<Group> GROUP_REMOVED = new BasicNotificationSensor<Group>(Group.class,
            "entity.group.removed", "Group dynamically removed from entity");
    
    static {
        RendererHints.register(Entity.class, RendererHints.displayValue(EntityFunctions.displayName()));
    }
        
    private boolean displayNameAutoGenerated = true;
    
    private Entity selfProxy;
    private volatile Application application;
    
    // TODO Because some things still don't use EntitySpec (e.g. the EntityFactory stuff for cluster/fabric),
    // then we need temp vals here. When setManagementContext is called, we'll switch these out for the read-deal;
    // i.e. for the values backed by storage
    private Reference<Entity> parent = new BasicReference<Entity>();
    private Set<Group> groups = Sets.newLinkedHashSet();
    private Set<Entity> children = Sets.newLinkedHashSet();
    private Reference<List<Location>> locations = new BasicReference<List<Location>>(ImmutableList.<Location>of()); // dups removed in addLocations
    private Reference<Long> creationTimeUtc = new BasicReference<Long>(System.currentTimeMillis());
    private Reference<String> displayName = new BasicReference<String>();
    private Reference<String> iconUrl = new BasicReference<String>();

    Map<String,Object> presentationAttributes = Maps.newLinkedHashMap();
    Collection<AbstractPolicy> policies = Lists.newCopyOnWriteArrayList();
    Collection<AbstractEnricher> enrichers = Lists.newCopyOnWriteArrayList();
    Collection<Feed> feeds = Lists.newCopyOnWriteArrayList();

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

    private final BasicConfigurationSupport config = new BasicConfigurationSupport();

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
    public AbstractEntity(@SuppressWarnings("rawtypes") Map flags, Entity parent) {
        super(checkConstructorFlags(flags, parent));

        // TODO Don't let `this` reference escape during construction
        entityType = new EntityDynamicType(this);
        
        if (isLegacyConstruction()) {
            AbstractBrooklynObject checkWeGetThis = configure(flags);
            assert this.equals(checkWeGetThis) : this+" configure method does not return itself; returns "+checkWeGetThis+" instead of "+this;

            boolean deferConstructionChecks = (flags.containsKey("deferConstructionChecks") && TypeCoercions.coerce(flags.get("deferConstructionChecks"), Boolean.class));
            if (!deferConstructionChecks) {
                FlagUtils.checkRequiredFields(this);
            }
        }
    }
    
    private static Map<?,?> checkConstructorFlags(Map flags, Entity parent) {
        if (flags==null) {
            throw new IllegalArgumentException("Flags passed to entity must not be null (try no-arguments or empty map)");
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
            LOG.warn("Use of deprecated \"flags.owner\" instead of \"flags.parent\" for entity");
            flags.put("parent", flags.get("owner"));
            flags.remove("owner");
        }
        return flags;
    }

    /**
     * @deprecated since 0.7.0; only used for legacy brooklyn types where constructor is called directly
     */
    @Override
    @Deprecated
    public AbstractEntity configure(Map flags) {
        if (!inConstruction && getManagementSupport().isDeployed()) {
            LOG.warn("bulk/flag configuration being made to {} after deployment: may not be supported in future versions ({})", 
                    new Object[] { this, flags });
        }
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
            displayName.set((String) flags.remove("displayName"));
            displayNameAutoGenerated = false;
        } else if (flags.get("name") != null) {
            displayName.set((String) flags.remove("name"));
            displayNameAutoGenerated = false;
        } else if (isLegacyConstruction()) {
            displayName.set(getClass().getSimpleName()+":"+Strings.maxlen(getId(), 4));
            displayNameAutoGenerated = true;
        }

        if (flags.get("iconUrl") != null) {
            iconUrl.set((String) flags.remove("iconUrl"));
        }
        
        // allow config keys, and fields, to be set from these flags if they have a SetFromFlag annotation
        // TODO the default values on flags are not used? (we should remove that support, since ConfigKeys gives a better way)
        FlagUtils.setFieldsFromFlags(flags, this);
        flags = FlagUtils.setAllConfigKeys(flags, this, false);
        
        // finally all config keys specified in map should be set as config
        // TODO use a config bag and remove the ones set above in the code below
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
            LOG.warn("Unsupported flags when configuring {}; storing: {}", this, flags);
            configsInternal.addToLocalBag(flags);
        }

        return this;
    }

    @Override
    public int hashCode() {
        return getId().hashCode();
    }
    
    @Override
    public boolean equals(Object o) {
        return (o == this || o == selfProxy) || 
                (o instanceof Entity && Objects.equal(getId(), ((Entity)o).getId()));
    }
    
    /** internal use only */ @Beta
    public void setProxy(Entity proxy) {
        if (selfProxy != null) 
            throw new IllegalStateException("Proxy is already set; cannot reset proxy for "+toString());
        resetProxy(proxy);
    }
    /** internal use only */ @Beta
    public void resetProxy(Entity proxy) {
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
    
    /**
     * Sets a config key value, and returns this Entity instance for use in fluent-API style coding.
     * 
     * @deprecated since 0.7.0; see {@link #config()}, such as {@code config().set(key, value)}
     */
    @Deprecated
    public <T> AbstractEntity configure(ConfigKey<T> key, T value) {
        setConfig(key, value);
        return this;
    }
    
    /**
     * @deprecated since 0.7.0; see {@link #config()}, such as {@code config().set(key, value)}
     */
    @SuppressWarnings("unchecked")
    @Deprecated
    public <T> AbstractEntity configure(ConfigKey<T> key, String value) {
        config().set((ConfigKey)key, value);
        return this;
    }
    
    /**
     * @deprecated since 0.7.0; see {@link #config()}, such as {@code config().set(key, value)}
     */
    @Deprecated
    public <T> AbstractEntity configure(HasConfigKey<T> key, T value) {
        config().set(key, value);
        return this;
    }
    
    /**
     * @deprecated since 0.7.0; see {@link #config()}, such as {@code config().set(key, value)}
     */
    @SuppressWarnings("unchecked")
    @Deprecated
    public <T> AbstractEntity configure(HasConfigKey<T> key, String value) {
        config().set((ConfigKey)key, value);
        return this;
    }

    public void setManagementContext(ManagementContextInternal managementContext) {
        super.setManagementContext(managementContext);
        getManagementSupport().setManagementContext(managementContext);
        entityType.setName(getEntityTypeName());
        if (displayNameAutoGenerated) displayName.set(getEntityType().getSimpleName()+":"+Strings.maxlen(getId(), 4));

        if (BrooklynFeatureEnablement.isEnabled(BrooklynFeatureEnablement.FEATURE_USE_BROOKLYN_LIVE_OBJECTS_DATAGRID_STORAGE)) {
            Entity oldParent = parent.get();
            Set<Group> oldGroups = groups;
            Set<Entity> oldChildren = children;
            List<Location> oldLocations = locations.get();
            EntityConfigMap oldConfig = configsInternal;
            AttributeMap oldAttribs = attributesInternal;
            long oldCreationTimeUtc = creationTimeUtc.get();
            String oldDisplayName = displayName.get();
            String oldIconUrl = iconUrl.get();

            parent = managementContext.getStorage().getReference(getId()+"-parent");
            groups = SetFromLiveMap.create(managementContext.getStorage().<Group,Boolean>getMap(getId()+"-groups"));
            children = SetFromLiveMap.create(managementContext.getStorage().<Entity,Boolean>getMap(getId()+"-children"));
            locations = managementContext.getStorage().getNonConcurrentList(getId()+"-locations");
            creationTimeUtc = managementContext.getStorage().getReference(getId()+"-creationTime");
            displayName = managementContext.getStorage().getReference(getId()+"-displayName");
            iconUrl = managementContext.getStorage().getReference(getId()+"-iconUrl");

            // Only override stored defaults if we have actual values. We might be in setManagementContext
            // because we are reconstituting an existing entity in a new brooklyn management-node (in which
            // case believe what is already in the storage), or we might be in the middle of creating a new 
            // entity. Normally for a new entity (using EntitySpec creation approach), this will get called
            // before setting the parent etc. However, for backwards compatibility we still support some
            // things calling the entity's constructor directly.
            if (oldParent != null) parent.set(oldParent);
            if (oldGroups.size() > 0) groups.addAll(oldGroups);
            if (oldChildren.size() > 0) children.addAll(oldChildren);
            if (oldLocations.size() > 0) locations.set(ImmutableList.copyOf(oldLocations));
            if (creationTimeUtc.isNull()) creationTimeUtc.set(oldCreationTimeUtc);
            if (displayName.isNull()) {
                displayName.set(oldDisplayName);
            } else {
                displayNameAutoGenerated = false;
            }
            if (iconUrl.isNull()) iconUrl.set(oldIconUrl);

            configsInternal = new EntityConfigMap(this, managementContext.getStorage().<ConfigKey<?>, Object>getMap(getId()+"-config"));
            if (oldConfig.getLocalConfig().size() > 0) {
                configsInternal.setLocalConfig(oldConfig.getLocalConfig());
            }
            config().refreshInheritedConfig();

            attributesInternal = new AttributeMap(this, managementContext.getStorage().<Collection<String>, Object>getMap(getId()+"-attributes"));
            if (oldAttribs.asRawMap().size() > 0) {
                for (Map.Entry<Collection<String>,Object> entry : oldAttribs.asRawMap().entrySet()) {
                    attributesInternal.update(entry.getKey(), entry.getValue());
                }
            }
        }
    }

    @Override
    public Map<String, String> toMetadataRecord() {
        return ImmutableMap.of();
    }

    @Override
    public long getCreationTime() {
        return creationTimeUtc.get();
    }

    @Override
    public String getDisplayName() {
        return displayName.get();
    }
    
    @Override
    public String getIconUrl() {
        return iconUrl.get();
    }
    
    @Override
    public void setDisplayName(String newDisplayName) {
        displayName.set(newDisplayName);
        displayNameAutoGenerated = false;
        getManagementSupport().getEntityChangeListener().onChanged();
    }
    
    /** allows subclasses to set the default display name to use if none is provided */
    protected void setDefaultDisplayName(String displayNameIfDefault) {
        if (displayNameAutoGenerated) {
            displayName.set(displayNameIfDefault);
        }
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
            LOG.debug("Entity type interface not found for entity "+this+"; instead using "+typeName+" as entity type name");
            return typeName;
        }
    }
    
    /**
     * Adds this as a child of the given entity; registers with application if necessary.
     */
    @Override
    public AbstractEntity setParent(Entity entity) {
        if (!parent.isNull()) {
            // If we are changing to the same parent...
            if (parent.contains(entity)) return this;
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
        entity.addChild(getProxyIfAvailable());
        config().refreshInheritedConfig();
        previouslyOwned = true;
        
        getApplication();
        
        return this;
    }

    @Override
    public void clearParent() {
        if (parent.isNull()) return;
        Entity oldParent = parent.get();
        parent.clear();
        if (oldParent != null) {
            if (!Entities.isNoLongerManaged(oldParent)) 
                oldParent.removeChild(getProxyIfAvailable());
        }
    }
    
    /**
     * Adds the given entity as a child of this parent <em>and</em> sets this entity as the parent of the child;
     * returns argument passed in, for convenience.
     * <p>
     * The child is NOT managed, even if the parent is already managed at this point
     * (e.g. the child is added *after* the parent's {@link AbstractEntity#init()} is invoked)
     * and so will need an explicit <code>getEntityManager().manage(childReturnedFromThis)</code> call.
     * <i>These semantics are currently under review.</i>
     */
    @Override
    public <T extends Entity> T addChild(T child) {
        checkNotNull(child, "child must not be null (for entity %s)", this);
        CatalogUtils.setCatalogItemIdOnAddition(this, child);
        
        boolean changed;
        synchronized (children) {
            if (Entities.isAncestor(this, child)) throw new IllegalStateException("loop detected trying to add child "+child+" to "+this+"; it is already an ancestor");
            child.setParent(getProxyIfAvailable());
            changed = children.add(child);
            
            getManagementSupport().getEntityChangeListener().onChildrenChanged();
        }
        
        // TODO not holding synchronization lock while notifying risks out-of-order if addChild+removeChild called in rapid succession.
        // But doing notification in synchronization block may risk deadlock?
        if (changed) {
            emit(AbstractEntity.CHILD_ADDED, child);
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
        if (spec.getParent()==null) {
            spec = EntitySpec.create(spec).parent(this);
        }
        if (!this.equals(spec.getParent())) {
            throw new IllegalArgumentException("Attempt to create child of "+this+" with entity spec "+spec+
                " failed because spec has different parent: "+spec.getParent());
        }
        return addChild(getEntityManager().createEntity(spec));
    }
    
    @Override
    public boolean removeChild(Entity child) {
        boolean changed;
        synchronized (children) {
            changed = children.remove(child);
            child.clearParent();
            
            if (changed) {
                getManagementSupport().getEntityChangeListener().onChildrenChanged();
            }
        }
        
        if (changed) {
            emit(AbstractEntity.CHILD_REMOVED, child);
        }
        return changed;
    }

    @Override
    public void addGroup(Group group) {
        boolean changed = groups.add(group);
        getApplication();
        
        if (changed) {
            emit(AbstractEntity.GROUP_ADDED, group);
        }
    }

    @Override
    public void removeGroup(Group group) {
        boolean changed = groups.remove(group);
        getApplication();
        
        if (changed) {
            emit(AbstractEntity.GROUP_REMOVED, group);
        }
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
    public ManagementContext getManagementContext() {
        // NB Sept 2014 - removed synch keyword above due to deadlock;
        // it also synchs in ManagementSupport.getManagementContext();
        // no apparent reason why it was here also
        return getManagementSupport().getManagementContext();
    }

    protected EntityManager getEntityManager() {
        return getManagementContext().getEntityManager();
    }
    
    @Override
    public EntityType getEntityType() {
        if (entityType==null) return null;
        return entityType.getSnapshot();
    }

    @Override
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
            
            for (Location loc : truelyNewLocations) {
                emit(AbstractEntity.LOCATION_ADDED, loc);
            }
        }
        
        if (getManagementSupport().isDeployed()) {
            for (Location newLocation : newLocations) {
                // Location is now reachable, so manage it
                // TODO will not be required in future releases when creating locations always goes through LocationManager.createLocation(LocationSpec).
                Locations.manage(newLocation, getManagementContext());
            }
        }
        getManagementSupport().getEntityChangeListener().onLocationsChanged();
    }

    @Override
    public void removeLocations(Collection<? extends Location> removedLocations) {
        synchronized (locations) {
            List<Location> oldLocations = locations.get();
            Set<Location> trulyRemovedLocations = Sets.intersection(ImmutableSet.copyOf(removedLocations), ImmutableSet.copyOf(oldLocations));
            locations.set(MutableList.<Location>builder().addAll(oldLocations).removeAll(removedLocations).buildImmutable());
            
            for (Location loc : trulyRemovedLocations) {
                emit(AbstractEntity.LOCATION_REMOVED, loc);
            }
        }
        
        // TODO Not calling `Entities.unmanage(removedLocation)` because this location might be shared with other entities.
        // Relying on abstractLocation.removeChildLocation unmanaging it, but not ideal as top-level locations will stick
        // around forever, even if not referenced.
        // Same goes for AbstractEntity#clearLocations().
        
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
    }

    @Override
    public <T> T getAttribute(AttributeSensor<T> attribute) {
        return attributesInternal.getValue(attribute);
    }

    @SuppressWarnings("unchecked")
    public <T> T getAttributeByNameParts(List<String> nameParts) {
        return (T) attributesInternal.getValue(nameParts);
    }
    
    static Set<String> WARNED_READ_ONLY_ATTRIBUTES = Collections.synchronizedSet(MutableSet.<String>of());
    
    @Override
    public <T> T setAttribute(AttributeSensor<T> attribute, T val) {
        if (LOG.isTraceEnabled())
            LOG.trace(""+this+" setAttribute "+attribute+" "+val);
        
        if (Boolean.TRUE.equals(getManagementSupport().isReadOnlyRaw())) {
            T oldVal = getAttribute(attribute);
            if (Equals.approximately(val, oldVal)) {
                // ignore, probably an enricher resetting values or something on init
            } else {
                String message = this+" setting "+attribute+" = "+val+" (was "+oldVal+") in read only mode; will have very little effect"; 
                if (!getManagementSupport().isDeployed()) {
                    if (getManagementSupport().wasDeployed()) message += " (no longer deployed)"; 
                    else message += " (not yet deployed)";
                }
                if (WARNED_READ_ONLY_ATTRIBUTES.add(attribute.getName())) {
                    LOG.warn(message + " (future messages for this sensor logged at trace)");
                } else if (LOG.isTraceEnabled()) {
                    LOG.trace(message);
                }
            }
        }
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
        if (LOG.isTraceEnabled())
            LOG.trace(""+this+" setAttributeWithoutPublishing "+attribute+" "+val);
        
        T result = attributesInternal.updateWithoutPublishing(attribute, val);
        if (result == null) {
            // could be this is a new sensor
            entityType.addSensorIfAbsentWithoutPublishing(attribute);
        }
        
        getManagementSupport().getEntityChangeListener().onAttributeChanged(attribute);
        return result;
    }

    @Beta
    @Override
    public <T> T modifyAttribute(AttributeSensor<T> attribute, Function<? super T, Maybe<T>> modifier) {
        if (LOG.isTraceEnabled())
            LOG.trace(""+this+" modifyAttribute "+attribute+" "+modifier);
        
        if (Boolean.TRUE.equals(getManagementSupport().isReadOnlyRaw())) {
            String message = this+" modifying "+attribute+" = "+modifier+" in read only mode; will have very little effect"; 
            if (!getManagementSupport().isDeployed()) {
                if (getManagementSupport().wasDeployed()) message += " (no longer deployed)"; 
                else message += " (not yet deployed)";
            }
            if (WARNED_READ_ONLY_ATTRIBUTES.add(attribute.getName())) {
                LOG.warn(message + " (future messages for this sensor logged at trace)");
            } else if (LOG.isTraceEnabled()) {
                LOG.trace(message);
            }
        }
        T result = attributesInternal.modify(attribute, modifier);
        if (result == null) {
            // could be this is a new sensor
            entityType.addSensorIfAbsent(attribute);
        }
        
        // TODO Conditionally set onAttributeChanged, only if was modified
        getManagementSupport().getEntityChangeListener().onAttributeChanged(attribute);
        return result;
    }

    @Override
    public void removeAttribute(AttributeSensor<?> attribute) {
        if (LOG.isTraceEnabled())
            LOG.trace(""+this+" removeAttribute "+attribute);
        
        attributesInternal.remove(attribute);
        entityType.removeSensor(attribute);
    }

    /** sets the value of the given attribute sensor from the config key value herein
     * if the attribtue sensor is not-set or null
     * <p>
     * returns old value 
     * @deprecated on interface since 0.5.0; use {@link ConfigToAttributes#apply(EntityLocal, AttributeSensorAndConfigKey)} */
    public <T> T setAttribute(AttributeSensorAndConfigKey<?,T> configuredSensor) {
        T v = getAttribute(configuredSensor);
        if (v!=null) return v;
        v = configuredSensor.getAsSensorValue(this);
        if (v!=null) return setAttribute(configuredSensor, v);
        return null;
    }

    @Override
    public Map<AttributeSensor, Object> getAllAttributes() {
        Map<AttributeSensor, Object> result = Maps.newLinkedHashMap();
        Map<String, Object> attribs = attributesInternal.asMap();
        for (Map.Entry<String,Object> entry : attribs.entrySet()) {
            AttributeSensor<?> attribKey = (AttributeSensor<?>) entityType.getSensor(entry.getKey());
            if (attribKey == null) {
                // Most likely a race: e.g. persister thread calling getAllAttributes; writer thread
                // has written attribute value and is in process of calling entityType.addSensorIfAbsent(attribute)
                // Just use a synthetic AttributeSensor, rather than ignoring value.
                // TODO If it's not a race, then don't log.warn every time!
                LOG.warn("When retrieving all attributes of {}, no AttributeSensor for attribute {} (creating synthetic)", this, entry.getKey());
                attribKey = Sensors.newSensor(Object.class, entry.getKey());
            }
            result.put(attribKey, entry.getValue());
        }
        return result;
    }

    
    // -------- CONFIGURATION --------------

    @Override 
    @Beta
    // the concrete type rather than an interface is returned because Groovy subclasses
    // complain (incorrectly) if we return ConfigurationSupportInternal
    // TODO revert to ConfigurationSupportInternal when groovy subclasses work without this (eg new groovy version)
    public BasicConfigurationSupport config() {
        return config;
    }

    /**
     * Direct use of this class is strongly discouraged. It will become private in a future release,
     * once {@link #config()} is reverted to return {@link ConfigurationSupportInternal} instead of
     * {@link BasicConfigurationSupport}.
     */
    @Beta
    // TODO revert to private when config() is reverted to return ConfigurationSupportInternal
    protected class BasicConfigurationSupport implements ConfigurationSupportInternal {

        @Override
        public <T> T get(ConfigKey<T> key) {
            return configsInternal.getConfig(key);
        }

        @Override
        public <T> T get(HasConfigKey<T> key) {
            return get(key.getConfigKey());
        }

        @Override
        public <T> T set(ConfigKey<T> key, T val) {
            return setConfigInternal(key, val);
        }

        @Override
        public <T> T set(HasConfigKey<T> key, T val) {
            return set(key.getConfigKey(), val);
        }

        @Override
        public <T> T set(ConfigKey<T> key, Task<T> val) {
            return setConfigInternal(key, val);
        }

        @Override
        public <T> T set(HasConfigKey<T> key, Task<T> val) {
            return set(key.getConfigKey(), val);
        }

        @Override
        public ConfigBag getBag() {
            return configsInternal.getAllConfigBag();
        }

        @Override
        public ConfigBag getLocalBag() {
            return configsInternal.getLocalConfigBag();
        }

        @Override
        public Maybe<Object> getRaw(ConfigKey<?> key) {
            return configsInternal.getConfigRaw(key, true);
        }

        @Override
        public Maybe<Object> getRaw(HasConfigKey<?> key) {
            return getRaw(key.getConfigKey());
        }

        @Override
        public Maybe<Object> getLocalRaw(ConfigKey<?> key) {
            return configsInternal.getConfigRaw(key, false);
        }

        @Override
        public Maybe<Object> getLocalRaw(HasConfigKey<?> key) {
            return getLocalRaw(key.getConfigKey());
        }

        @Override
        public void addToLocalBag(Map<String, ?> vals) {
            configsInternal.addToLocalBag(vals);
        }

        @Override
        public void removeFromLocalBag(String key) {
            configsInternal.removeFromLocalBag(key);
        }

        @Override
        public void refreshInheritedConfig() {
            if (getParent() != null) {
                configsInternal.setInheritedConfig(((EntityInternal)getParent()).getAllConfig(), ((EntityInternal)getParent()).config().getBag());
            } else {
                configsInternal.clearInheritedConfig();
            }

            refreshInheritedConfigOfChildren();
        }
        
        @Override
        public void refreshInheritedConfigOfChildren() {
            for (Entity it : getChildren()) {
                ((EntityInternal)it).config().refreshInheritedConfig();
            }
        }
        
        @SuppressWarnings("unchecked")
        private <T> T setConfigInternal(ConfigKey<T> key, Object val) {
            if (!inConstruction && getManagementSupport().isDeployed()) {
                // previously we threw, then warned, but it is still quite common;
                // so long as callers don't expect miracles, it should be fine.
                // i (Alex) think the way to be stricter about this (if that becomes needed) 
                // would be to introduce a 'mutable' field on config keys
                LOG.debug("configuration being made to {} after deployment: {} = {}; change may not be visible in other contexts", 
                        new Object[] { this, key, val });
            }
            T result = (T) configsInternal.setConfig(key, val);
            
            getManagementSupport().getEntityChangeListener().onConfigChanged(key);
            return result;

        }
    }
    
    @Override
    public <T> T getConfig(ConfigKey<T> key) {
        return config().get(key);
    }
    
    @Override
    public <T> T getConfig(HasConfigKey<T> key) {
        return config().get(key);
    }
    
    @Override
    @Deprecated
    public <T> T getConfig(HasConfigKey<T> key, T defaultValue) {
        return configsInternal.getConfig(key, defaultValue);
    }
    
    //don't use groovy defaults for defaultValue as that doesn't implement the contract; we need the above
    @Override
    @Deprecated
    public <T> T getConfig(ConfigKey<T> key, T defaultValue) {
        return configsInternal.getConfig(key, defaultValue);
    }
    
    @Override
    @Deprecated
    public Maybe<Object> getConfigRaw(ConfigKey<?> key, boolean includeInherited) {
        return (includeInherited) ? config().getRaw(key) : config().getLocalRaw(key);
    }
    
    @Override
    @Deprecated
    public Maybe<Object> getConfigRaw(HasConfigKey<?> key, boolean includeInherited) {
        return (includeInherited) ? config().getRaw(key) : config().getLocalRaw(key);
    }

    @Override
    @Deprecated
    public <T> T setConfig(ConfigKey<T> key, T val) {
        return config().set(key, val);
    }

    @Override
    @Deprecated
    public <T> T setConfig(ConfigKey<T> key, Task<T> val) {
        return config().set(key, val);
    }

    /**
     * @deprecated since 0.7.0; use {@code config().set(key, task)}, with {@link Task} instead of {@link DeferredSupplier}
     */
    @Deprecated
    public <T> T setConfig(ConfigKey<T> key, DeferredSupplier val) {
        return config.setConfigInternal(key, val);
    }

    @Override
    @Deprecated
    public <T> T setConfig(HasConfigKey<T> key, T val) {
        return config().set(key, val);
    }

    @Override
    @Deprecated
    public <T> T setConfig(HasConfigKey<T> key, Task<T> val) {
        return (T) config().set(key, val);
    }

    /**
     * @deprecated since 0.7.0; use {@code config().set(key, task)}, with {@link Task} instead of {@link DeferredSupplier}
     */
    @Deprecated
    public <T> T setConfig(HasConfigKey<T> key, DeferredSupplier val) {
        return setConfig(key.getConfigKey(), val);
    }

    @SuppressWarnings("unchecked")
    public <T> T setConfigEvenIfOwned(ConfigKey<T> key, T val) {
        return (T) configsInternal.setConfig(key, val);
    }

    public <T> T setConfigEvenIfOwned(HasConfigKey<T> key, T val) {
        return setConfigEvenIfOwned(key.getConfigKey(), val);
    }

    /**
     * @deprecated since 0.7.0; use {@code if (val != null) config().set(key, val)}
     */
    @Deprecated
    @SuppressWarnings({ "unchecked", "rawtypes" })
    protected void setConfigIfValNonNull(ConfigKey key, Object val) {
        if (val != null) config().set(key, val);
    }

    /**
     * @deprecated since 0.7.0; use {@code if (val != null) config().set(key, val)}
     */
    @Deprecated
    @SuppressWarnings({ "unchecked", "rawtypes" })
    protected void setConfigIfValNonNull(HasConfigKey key, Object val) {
        if (val != null) config().set(key, val);
    }

    /**
     * @deprecated since 0.7.0; see {@code config().refreshInheritedConfig()}
     */
    @Override
    @Deprecated
    public void refreshInheritedConfig() {
        config().refreshInheritedConfig();
    }

    /**
     * @deprecated since 0.7.0; see {@code config().refreshInheritedConfigOfChildren()}
     */
    @Deprecated
    void refreshInheritedConfigOfChildren() {
        config().refreshInheritedConfigOfChildren();
    }

    @Override
    @Deprecated
    public EntityConfigMap getConfigMap() {
        return configsInternal;
    }
    
    @Override
    @Deprecated
    public Map<ConfigKey<?>,Object> getAllConfig() {
        return configsInternal.getAllConfig();
    }

    @Beta
    @Override
    @Deprecated
    public ConfigBag getAllConfigBag() {
        return config().getBag();
    }

    @Beta
    @Override
    @Deprecated
    public ConfigBag getLocalConfigBag() {
        return config().getLocalBag();
    }

    
    // -------- SUBSCRIPTIONS --------------

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
    
    // -------- INITIALIZATION --------------

    /**
     * Default entity initialization, just calls {@link #initEnrichers()}.
     */
    public void init() {
        super.init();
        initEnrichers();
    }
    
    /**
     * By default, adds enrichers to populate {@link Attributes#SERVICE_UP} and {@link Attributes#SERVICE_STATE_ACTUAL}
     * based on {@link Attributes#SERVICE_NOT_UP_INDICATORS}, 
     * {@link Attributes#SERVICE_STATE_EXPECTED} and {@link Attributes#SERVICE_PROBLEMS}
     * (doing nothing if these sensors are not used).
     * <p>
     * Subclasses may go further and populate the {@link Attributes#SERVICE_NOT_UP_INDICATORS} 
     * and {@link Attributes#SERVICE_PROBLEMS} from children and members or other sources.
     */
    // these enrichers do nothing unless Attributes.SERVICE_NOT_UP_INDICATORS are used
    // and/or SERVICE_STATE_EXPECTED 
    protected void initEnrichers() {
        addEnricher(ServiceNotUpLogic.newEnricherForServiceUpIfNotUpIndicatorsEmpty());
        addEnricher(ServiceStateLogic.newEnricherForServiceStateFromProblemsAndUp());
    }
    
    // -------- POLICIES --------------------

    @Override
    public Collection<Policy> getPolicies() {
        return ImmutableList.<Policy>copyOf(policies);
    }

    @Override
    public void addPolicy(Policy policy) {
        Policy old = findApparentlyEqualAndWarnIfNotSameUniqueTag(policies, policy);
        if (old!=null) {
            LOG.debug("Removing "+old+" when adding "+policy+" to "+this);
            removePolicy(old);
        }
        
        CatalogUtils.setCatalogItemIdOnAddition(this, policy);
        policies.add((AbstractPolicy)policy);
        ((AbstractPolicy)policy).setEntity(this);
        
        getManagementSupport().getEntityChangeListener().onPolicyAdded(policy);
        emit(AbstractEntity.POLICY_ADDED, new PolicyDescriptor(policy));
    }

    @Override
    public <T extends Policy> T addPolicy(PolicySpec<T> spec) {
        T policy = getManagementContext().getEntityManager().createPolicy(spec);
        addPolicy(policy);
        return policy;
    }

    @Override
    public <T extends Enricher> T addEnricher(EnricherSpec<T> spec) {
        T enricher = getManagementContext().getEntityManager().createEnricher(spec);
        addEnricher(enricher);
        return enricher;
    }

    @Override
    public boolean removePolicy(Policy policy) {
        ((AbstractPolicy)policy).destroy();
        boolean changed = policies.remove(policy);
        
        if (changed) {
            getManagementSupport().getEntityChangeListener().onPolicyRemoved(policy);
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
        return changed;
    }
    
    @Override
    public Collection<Enricher> getEnrichers() {
        return ImmutableList.<Enricher>copyOf(enrichers);
    }

    @Override
    public void addEnricher(Enricher enricher) {
        Enricher old = findApparentlyEqualAndWarnIfNotSameUniqueTag(enrichers, enricher);
        if (old!=null) {
            LOG.debug("Removing "+old+" when adding "+enricher+" to "+this);
            removeEnricher(old);
        }
        
        CatalogUtils.setCatalogItemIdOnAddition(this, enricher);
        enrichers.add((AbstractEnricher) enricher);
        ((AbstractEnricher)enricher).setEntity(this);
        
        getManagementSupport().getEntityChangeListener().onEnricherAdded(enricher);
        // TODO Could add equivalent of AbstractEntity.POLICY_ADDED for enrichers; no use-case for that yet
    }
    
    private <T extends EntityAdjunct> T findApparentlyEqualAndWarnIfNotSameUniqueTag(Collection<? extends T> items, T newItem) {
        T oldItem = findApparentlyEqual(items, newItem, true);
        
        if (oldItem!=null) {
            String oldItemTag = oldItem.getUniqueTag();
            String newItemTag = newItem.getUniqueTag();
            if (oldItemTag!=null || newItemTag!=null) {
                if (Objects.equal(oldItemTag, newItemTag)) {
                    // if same tag, return old item for replacing without comment
                    return oldItem;
                }
                // if one has a tag bug not the other, and they are apparently equal,
                // transfer the tag across
                T tagged = oldItemTag!=null ? oldItem : newItem;
                T tagless = oldItemTag!=null ? newItem : oldItem;
                LOG.warn("Apparently equal items "+oldItem+" and "+newItem+"; but one has a unique tag "+tagged.getUniqueTag()+"; applying to the other");
                ((AdjunctTagSupport)tagless.tags()).setUniqueTag(tagged.getUniqueTag());
            }
            
            if (isRebinding()) {
                LOG.warn("Adding to "+this+", "+newItem+" appears identical to existing "+oldItem+"; will replace. "
                    + "Underlying addition should be modified so it is not added twice during rebind or unique tag should be used to indicate it is identical.");
                return oldItem;
            } else {
                LOG.warn("Adding to "+this+", "+newItem+" appears identical to existing "+oldItem+"; may get removed on rebind. "
                    + "Underlying addition should be modified so it is not added twice.");
                return null;
            }
        } else {
            return null;
        }
    }
    private <T extends EntityAdjunct> T findApparentlyEqual(Collection<? extends T> itemsCopy, T newItem, boolean transferUniqueTag) {
        // TODO workaround for issue where enrichers/feeds/policies can get added multiple times on rebind,
        // if it's added in onBecomingManager or connectSensors; 
        // the right fix will be more disciplined about how/where these are added;
        // furthermore unique tags should be preferred;
        // when they aren't supplied, a reflection equals is done ignoring selected fields,
        // which is okay but not great ... and if it misses something (e.g. because an 'equals' isn't implemented)
        // then you can get a new instance on every rebind
        // (and currently these aren't readily visible, except looking at the counts or in persisted state) 
        Class<?> beforeEntityAdjunct = newItem.getClass();
        while (beforeEntityAdjunct.getSuperclass()!=null && !beforeEntityAdjunct.getSuperclass().equals(AbstractEntityAdjunct.class))
            beforeEntityAdjunct = beforeEntityAdjunct.getSuperclass();
        
        String newItemTag = newItem.getUniqueTag();
        for (T oldItem: itemsCopy) {
            String oldItemTag = oldItem.getUniqueTag();
            if (oldItemTag!=null && newItemTag!=null) { 
                if (oldItemTag.equals(newItemTag)) {
                    return oldItem;
                } else {
                    continue;
                }
            }
            // either does not have a unique tag, do deep equality
            if (oldItem.getClass().equals(newItem.getClass())) {
                if (EqualsBuilder.reflectionEquals(oldItem, newItem, false,
                        // internal admin in 'beforeEntityAdjunct' should be ignored
                        beforeEntityAdjunct,
                        // known fields which shouldn't block equality checks:
                        // from aggregator
                        "transformation",
                        // from averager
                        "values", "timestamps", "lastAverage",
                        // from some feeds
                        "poller",
                        "pollerStateMutex"
                        )) {
                    
                    return oldItem;
                }
            }
        }
        return null;
    }

    @Override
    public boolean removeEnricher(Enricher enricher) {
        ((AbstractEnricher)enricher).destroy();
        boolean changed = enrichers.remove(enricher);
        
        if (changed) {
            getManagementSupport().getEntityChangeListener().onEnricherRemoved(enricher);
        }
        return changed;

    }

    @Override
    public boolean removeAllEnrichers() {
        boolean changed = false;
        for (AbstractEnricher enricher : enrichers) {
            changed = removeEnricher(enricher) || changed;
        }
        return changed;
    }
    
    // -------- FEEDS --------------------

    /**
     * Convenience, which calls {@link EntityInternal#feeds()} and {@link FeedSupport#addFeed(Feed)}.
     */
    @Override
    public <T extends Feed> T addFeed(T feed) {
        return feeds().addFeed(feed);
    }

    @Override
    public FeedSupport feeds() {
        return new BasicFeedSupport();
    }
    
    @Override
    @Deprecated
    public FeedSupport getFeedSupport() {
        return feeds();
    }
    
    protected class BasicFeedSupport implements FeedSupport {
        @Override
        public Collection<Feed> getFeeds() {
            return ImmutableList.<Feed>copyOf(feeds);
        }

        @Override
        public <T extends Feed> T addFeed(T feed) {
            Feed old = findApparentlyEqualAndWarnIfNotSameUniqueTag(feeds, feed);
            if (old != null) {
                if (old == feed) {
                    if (!BrooklynFeatureEnablement.isEnabled(BrooklynFeatureEnablement.FEATURE_FEED_REGISTRATION_PROPERTY)) {
                        LOG.debug("Feed " + feed + " already added, not adding a second time.");
                    } // else expected to be added a second time through addFeed, ignore
                    return feed;
                } else {
                    // Different feed object with (seemingly) same functionality, remove previous one, will stop it.
                    LOG.debug("Removing "+old+" when adding "+feed+" to "+this);
                    removeFeed(old);
                }
            }
            
            CatalogUtils.setCatalogItemIdOnAddition(AbstractEntity.this, feed);
            feeds.add(feed);
            if (!AbstractEntity.this.equals(((AbstractFeed)feed).getEntity()))
                ((AbstractFeed)feed).setEntity(AbstractEntity.this);

            getManagementContext().getRebindManager().getChangeListener().onManaged(feed);
            getManagementSupport().getEntityChangeListener().onFeedAdded(feed);
            // TODO Could add equivalent of AbstractEntity.POLICY_ADDED for feeds; no use-case for that yet

            return feed;
        }

        @Override
        public boolean removeFeed(Feed feed) {
            feed.stop();
            boolean changed = feeds.remove(feed);
            
            if (changed) {
                getManagementContext().getRebindManager().getChangeListener().onUnmanaged(feed);
                getManagementSupport().getEntityChangeListener().onFeedRemoved(feed);
            }
            return changed;
        }

        @Override
        public boolean removeAllFeeds() {
            boolean changed = false;
            for (Feed feed : feeds) {
                changed = removeFeed(feed) || changed;
            }
            return changed;
        }
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
        BrooklynLogging.log(LOG, BrooklynLogging.levelDebugOrTraceIfReadOnly(this),
            "Emitting sensor notification {} value {} on {}", sensor.getName(), val, this);
        emitInternal(sensor, val);
    }
    
    public <T> void emitInternal(Sensor<T> sensor, T val) {
        if (getManagementSupport().isNoLongerManaged())
            throw new IllegalStateException("Entity "+this+" is no longer managed, when trying to publish "+sensor+" "+val);

        SubscriptionContext subsContext = getSubscriptionContext();
        if (subsContext != null) subsContext.publish(sensor.newEvent(getProxyIfAvailable(), val));
    }

    // -------- EFFECTORS --------------

    /** Convenience for finding named effector in {@link EntityType#getEffectors()} {@link Map}. */
    public Effector<?> getEffector(String effectorName) {
        return entityType.getEffector(effectorName);
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
     * management context, but before the entity is visible to other entities), including during a rebind.
     */
    public void onManagementStarting() {
        if (isLegacyConstruction()) {
            entityType.setName(getEntityTypeName());
            if (displayNameAutoGenerated) displayName.set(getEntityType().getSimpleName()+":"+Strings.maxlen(getId(), 4));
        }
    }
    
    /**
     * Invoked by {@link EntityManagementSupport} when this entity is fully managed and visible to other entities
     * through the management context.
     */
    public void onManagementStarted() {}
    
    /**
     * Invoked by {@link ManagementContext} when this entity becomes managed at a particular management node,
     * including the initial management started and subsequent management node master-change for this entity.
     * @deprecated since 0.4.0 override EntityManagementSupport.onManagementStarted if customization needed
     */
    public void onManagementBecomingMaster() {}
    
    /**
     * Invoked by {@link ManagementContext} when this entity becomes mastered at a particular management node,
     * including the final management end and subsequent management node master-change for this entity.
     * @deprecated since 0.4.0 override EntityManagementSupport.onManagementStopped if customization needed
     */
    public void onManagementNoLongerMaster() {}

    /**
     * Invoked by {@link EntityManagementSupport} when this entity is fully unmanaged.
     * <p>
     * Note that the activies possible here (when unmanaged) are limited, 
     * and that this event may be caused by either a brooklyn node itself being demoted
     * (so the entity is managed elsewhere) or by a controlled shutdown.
     */
    public void onManagementStopped() {
        if (getManagementContext().isRunning()) {
            BrooklynStorage storage = ((ManagementContextInternal)getManagementContext()).getStorage();
            storage.remove(getId()+"-parent");
            storage.remove(getId()+"-groups");
            storage.remove(getId()+"-children");
            storage.remove(getId()+"-locations");
            storage.remove(getId()+"-creationTime");
            storage.remove(getId()+"-displayName");
            storage.remove(getId()+"-config");
            storage.remove(getId()+"-attributes");
        }
    }
    
    /** For use by management plane, to invalidate all fields (e.g. when an entity is changing to being proxied) */
    public void invalidateReferences() {
        // TODO Just rely on GC of this entity instance, to get rid of the children map etc.
        //      Don't clear it, as it's persisted.
        // TODO move this to EntityMangementSupport,
        application = null;
    }
    
    @Override
    public EntityManagementSupport getManagementSupport() {
        return managementSupport;
    }

    @Override
    public void requestPersist() {
        getManagementSupport().getEntityChangeListener().onChanged();
    }

    /**
     * As described in {@link EntityInternal#getRebindSupport()}...
     * Users are strongly discouraged to call or override this method.
     * It is for internal calls only, relating to persisting/rebinding entities.
     * This method may change (or be removed) in a future release without notice.
     */
    @Override
    @Beta
    public RebindSupport<EntityMemento> getRebindSupport() {
        return new BasicEntityRebindSupport(this);
    }

    @Override
    protected void onTagsChanged() {
        super.onTagsChanged();
        getManagementSupport().getEntityChangeListener().onTagsChanged();
    }
}
