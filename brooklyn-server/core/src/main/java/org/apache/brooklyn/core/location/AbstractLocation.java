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
package org.apache.brooklyn.core.location;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.apache.brooklyn.util.JavaGroovyEquivalents.groovyTruth;
import static org.apache.brooklyn.util.groovy.GroovyJavaMethods.elvis;

import java.io.Closeable;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.Group;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.location.LocationSpec;
import org.apache.brooklyn.api.mgmt.ExecutionContext;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.api.mgmt.SubscriptionContext;
import org.apache.brooklyn.api.mgmt.SubscriptionHandle;
import org.apache.brooklyn.api.mgmt.Task;
import org.apache.brooklyn.api.mgmt.rebind.RebindSupport;
import org.apache.brooklyn.api.mgmt.rebind.mementos.LocationMemento;
import org.apache.brooklyn.api.objs.Configurable;
import org.apache.brooklyn.api.sensor.Sensor;
import org.apache.brooklyn.api.sensor.SensorEventListener;
import org.apache.brooklyn.config.ConfigInheritance;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.config.ConfigKey.HasConfigKey;
import org.apache.brooklyn.core.BrooklynFeatureEnablement;
import org.apache.brooklyn.core.config.BasicConfigKey;
import org.apache.brooklyn.core.config.ConfigConstraints;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.internal.storage.BrooklynStorage;
import org.apache.brooklyn.core.internal.storage.Reference;
import org.apache.brooklyn.core.internal.storage.impl.BasicReference;
import org.apache.brooklyn.core.location.geo.HasHostGeoInfo;
import org.apache.brooklyn.core.location.geo.HostGeoInfo;
import org.apache.brooklyn.core.location.internal.LocationDynamicType;
import org.apache.brooklyn.core.location.internal.LocationInternal;
import org.apache.brooklyn.core.mgmt.internal.LocalLocationManager;
import org.apache.brooklyn.core.mgmt.internal.ManagementContextInternal;
import org.apache.brooklyn.core.mgmt.internal.SubscriptionTracker;
import org.apache.brooklyn.core.mgmt.rebind.BasicLocationRebindSupport;
import org.apache.brooklyn.core.objs.AbstractBrooklynObject;
import org.apache.brooklyn.core.objs.AbstractConfigurationSupportInternal;
import org.apache.brooklyn.util.collections.SetFromLiveMap;
import org.apache.brooklyn.util.core.config.ConfigBag;
import org.apache.brooklyn.util.core.flags.FlagUtils;
import org.apache.brooklyn.util.core.flags.TypeCoercions;
import org.apache.brooklyn.util.core.task.DeferredSupplier;
import org.apache.brooklyn.util.core.task.Tasks;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.guava.Maybe;
import org.apache.brooklyn.util.stream.Streams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.Beta;
import com.google.common.base.Objects;
import com.google.common.base.Objects.ToStringHelper;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.reflect.TypeToken;

/**
 * A basic implementation of the {@link Location} interface.
 *
 * This provides an implementation which works according to the requirements of
 * the interface documentation, and is ready to be extended to make more specialized locations.
 * 
 * Override {@link #configure(Map)} to add special initialization logic.
 */
public abstract class AbstractLocation extends AbstractBrooklynObject implements LocationInternal, HasHostGeoInfo, Configurable {
    
    /** @deprecated since 0.7.0 shouldn't be public */
    @Deprecated
    public static final Logger LOG = LoggerFactory.getLogger(AbstractLocation.class);

    public static final ConfigKey<Location> PARENT_LOCATION = new BasicConfigKey<Location>(Location.class, "parentLocation");

    public static final ConfigKey<Boolean> TEMPORARY_LOCATION = ConfigKeys.newBooleanConfigKey("temporaryLocation",
            "Indicates that the location is a temporary location that has been created to test connectivity, and that" +
            "the location's events should not be recorded by usage listeners", false);

    private final AtomicBoolean configured = new AtomicBoolean();
    
    private Reference<Long> creationTimeUtc = new BasicReference<Long>(System.currentTimeMillis());
    
    // _not_ set from flag; configured explicitly in configure, because we also need to update the parent's list of children
    private Reference<Location> parent = new BasicReference<Location>();
    
    // NB: all accesses should be synchronized
    private Set<Location> children = Sets.newLinkedHashSet();

    private Reference<String> name = new BasicReference<String>();
    private boolean displayNameAutoGenerated = true;

    private Reference<HostGeoInfo> hostGeoInfo = new BasicReference<HostGeoInfo>();

    private BasicConfigurationSupport config = new BasicConfigurationSupport();
    
    private BasicSubscriptionSupport subscriptions = new BasicSubscriptionSupport();
    
    private ConfigBag configBag = new ConfigBag();

    /** not for direct access; refer to as 'subscriptionTracker' via getter so that it is initialized */
    protected transient SubscriptionTracker _subscriptionTracker;

    private volatile boolean managed;

    private boolean inConstruction;

    private Reference<Map<Class<?>, Object>> extensions = new BasicReference<Map<Class<?>, Object>>(Maps.<Class<?>, Object>newConcurrentMap());

    private final LocationDynamicType locationType;

    /**
     * Construct a new instance of an AbstractLocation.
     */
    public AbstractLocation() {
        this(Maps.newLinkedHashMap());
    }
    
    /**
     * Construct a new instance of an AbstractLocation.
     *
     * The properties map recognizes the following keys:
     * <ul>
     * <li>name - a name for the location
     * <li>parentLocation - the parent {@link Location}
     * </ul>
     * 
     * Other common properties (retrieved via get/findLocationProperty) include:
     * <ul>
     * <li>latitude
     * <li>longitude
     * <li>displayName
     * <li>iso3166 - list of iso3166-2 code strings
     * <li>timeZone
     * <li>abbreviatedName
     * </ul>
     */
    public AbstractLocation(Map<?,?> properties) {
        super(properties);
        inConstruction = true;
        
        // When one calls getConfig(key), we want to use the default value specified on *this* location
        // if it overrides the default config, by using the type object 
        locationType = new LocationDynamicType(this);
        
        if (isLegacyConstruction()) {
            AbstractLocation checkWeGetThis = configure(properties);
            assert this.equals(checkWeGetThis) : this+" configure method does not return itself; returns "+checkWeGetThis+" instead of "+this;

            boolean deferConstructionChecks = (properties.containsKey("deferConstructionChecks") && TypeCoercions.coerce(properties.get("deferConstructionChecks"), Boolean.class));
            if (!deferConstructionChecks) {
                FlagUtils.checkRequiredFields(this);
            }
        }
        
        inConstruction = false;
    }

    protected void assertNotYetManaged() {
        if (!inConstruction && Locations.isManaged(this)) {
            LOG.warn("Configuration being made to {} after deployment; may not be supported in future versions", this);
        }
        //throw new IllegalStateException("Cannot set configuration "+key+" on active location "+this)
    }

    public void setManagementContext(ManagementContextInternal managementContext) {
        super.setManagementContext(managementContext);
        if (displayNameAutoGenerated && getId() != null) name.set(getClass().getSimpleName()+":"+getId().substring(0, Math.min(getId().length(),4)));

        if (BrooklynFeatureEnablement.isEnabled(BrooklynFeatureEnablement.FEATURE_USE_BROOKLYN_LIVE_OBJECTS_DATAGRID_STORAGE)) {
            Location oldParent = parent.get();
            Set<Location> oldChildren = children;
            Map<String, Object> oldConfig = configBag.getAllConfig();
            Long oldCreationTimeUtc = creationTimeUtc.get();
            String oldDisplayName = name.get();
            HostGeoInfo oldHostGeoInfo = hostGeoInfo.get();

            parent = managementContext.getStorage().getReference(getId()+"-parent");
            children = SetFromLiveMap.create(managementContext.getStorage().<Location,Boolean>getMap(getId()+"-children"));
            creationTimeUtc = managementContext.getStorage().getReference(getId()+"-creationTime");
            hostGeoInfo = managementContext.getStorage().getReference(getId()+"-hostGeoInfo");
            name = managementContext.getStorage().getReference(getId()+"-displayName");

            // Only override stored defaults if we have actual values. We might be in setManagementContext
            // because we are reconstituting an existing entity in a new brooklyn management-node (in which
            // case believe what is already in the storage), or we might be in the middle of creating a new 
            // entity. Normally for a new entity (using EntitySpec creation approach), this will get called
            // before setting the parent etc. However, for backwards compatibility we still support some
            // things calling the entity's constructor directly.
            if (oldParent != null) parent.set(oldParent);
            if (oldChildren.size() > 0) children.addAll(oldChildren);
            if (creationTimeUtc.isNull()) creationTimeUtc.set(oldCreationTimeUtc);
            if (hostGeoInfo.isNull()) hostGeoInfo.set(oldHostGeoInfo);
            if (name.isNull()) {
                name.set(oldDisplayName);
            } else {
                displayNameAutoGenerated = false;
            }

            configBag = ConfigBag.newLiveInstance(managementContext.getStorage().<String,Object>getMap(getId()+"-config"));
            if (oldConfig.size() > 0) {
                configBag.putAll(oldConfig);
            }
        }
    }

    /**
     * @deprecated since 0.7.0; only used for legacy brooklyn types where constructor is called directly;
     * see overridden method for more info
     */
    @SuppressWarnings("serial")
    @Override
    @Deprecated
    public AbstractLocation configure(Map<?,?> properties) {
        assertNotYetManaged();
        
        boolean firstTime = !configured.getAndSet(true);
            
        configBag.putAll(properties);
        
        if (properties.containsKey(PARENT_LOCATION.getName())) {
            // need to ensure parent's list of children is also updated
            setParent(configBag.get(PARENT_LOCATION));
            
            // don't include parentLocation in configBag, as breaks rebind
            configBag.remove(PARENT_LOCATION);
        }

        // NB: flag-setting done here must also be done in BasicLocationRebindSupport 
        FlagUtils.setFieldsFromFlagsWithBag(this, properties, configBag, firstTime);
        FlagUtils.setAllConfigKeys(this, configBag, false);

        if (properties.containsKey("displayName")) {
            name.set((String) removeIfPossible(properties, "displayName"));
            displayNameAutoGenerated = false;
        } else if (properties.containsKey("name")) {
            name.set((String) removeIfPossible(properties, "name"));
            displayNameAutoGenerated = false;
        } else if (isLegacyConstruction()) {
            name.set(getClass().getSimpleName()+":"+getId().substring(0, Math.min(getId().length(),4)));
            displayNameAutoGenerated = true;
        }

        // TODO Explicitly dealing with iso3166 here because want custom splitter rule comma-separated string.
        // Is there a better way to do it (e.g. more similar to latitude, where configKey+TypeCoercion is enough)?
        if (groovyTruth(properties.get("iso3166"))) {
            Object rawCodes = removeIfPossible(properties, "iso3166");
            Set<String> codes;
            if (rawCodes instanceof CharSequence) {
                codes = ImmutableSet.copyOf(Splitter.on(",").trimResults().split((CharSequence)rawCodes));
            } else {
                codes = TypeCoercions.coerce(rawCodes, new TypeToken<Set<String>>() {});
            }
            configBag.put(LocationConfigKeys.ISO_3166, codes);
        }
        
        return this;
    }

    // TODO ensure no callers rely on 'remove' semantics, and don't remove;
    // or perhaps better use a config bag so we know what is used v unused
    private static Object removeIfPossible(Map<?,?> map, Object key) {
        try {
            return map.remove(key);
        } catch (Exception e) {
            return map.get(key);
        }
    }
    
    public boolean isManaged() {
        return getManagementContext() != null && managed;
    }

    public void onManagementStarted() {
        if (displayNameAutoGenerated) name.set(getClass().getSimpleName()+":"+getId().substring(0, Math.min(getId().length(),4)));
        this.managed = true;
    }
    
    public void onManagementStopped() {
        this.managed = false;
        if (getManagementContext().isRunning()) {
            BrooklynStorage storage = ((ManagementContextInternal)getManagementContext()).getStorage();
            storage.remove(getId()+"-parent");
            storage.remove(getId()+"-children");
            storage.remove(getId()+"-creationTime");
            storage.remove(getId()+"-hostGeoInfo");
            storage.remove(getId()+"-displayName");
            storage.remove(getId()+"-config");
        }
    }
    
    @Override
    public String getDisplayName() {
        return name.get();
    }
    
    protected boolean isDisplayNameAutoGenerated() {
        return displayNameAutoGenerated;
    }
    
    @Override
    public Location getParent() {
        return parent.get();
    }
    
    @Override
    public Collection<Location> getChildren() {
        synchronized (children) {
            return ImmutableList.copyOf(children);
        }
    }

    @Override
    public void setParent(Location newParent) {
        setParent(newParent, true);
    }
    
    public void setParent(Location newParent, boolean updateChildListParents) {
        if (newParent == this) {
            throw new IllegalArgumentException("Location cannot be its own parent: "+this);
        }
        if (newParent == parent.get()) {
            return; // no-op; already have desired parent
        }
        
        if (parent.get() != null) {
            Location oldParent = parent.get();
            parent.set(null);
            if (updateChildListParents)
                ((AbstractLocation)oldParent).removeChild(this);
        }
        // TODO Should we support a location changing parent? The resulting unmanage/manage might cause problems.
        // The code above suggests we do, but maybe we should warn or throw error, or at least test it!
        
        parent.set(newParent);
        if (newParent != null) {
            if (updateChildListParents)
                ((AbstractLocation)newParent).addChild(this);
        }
        
        onChanged();
    }

    @Override
    public ConfigurationSupportInternal config() {
        return config;
    }

    // the concrete type rather than an interface is returned because Groovy subclasses
    // complain (incorrectly) if we return SubscriptionSupportInternal
    // TODO revert to SubscriptionSupportInternal when groovy subclasses work without this (eg new groovy version)
    @Override
    @Beta
    public BasicSubscriptionSupport subscriptions() {
        return subscriptions;
    }

    private class BasicConfigurationSupport extends AbstractConfigurationSupportInternal {

        @Override
        public <T> T get(ConfigKey<T> key) {
            Object result = null;
            if (hasConfig(key, false)) {
                result = getLocalBag().getAllConfigRaw().get(key.getName());

            } else if (getParent() != null && isInherited(key)) {
                result = getParent().getConfig(key);

            } else {
                // In case this entity class has overridden the given key (e.g. to set default), then retrieve this entity's key
                // TODO when locations become entities, the duplication of this compared to EntityConfigMap.getConfig will disappear.
                @SuppressWarnings("unchecked")
                ConfigKey<T> ownKey = (ConfigKey<T>) elvis(locationType.getConfigKey(key.getName()), key);
                result = ownKey.getDefaultValue();
            }

            if (result instanceof DeferredSupplier<?>) {
                try {
                    ManagementContext mgmt = AbstractLocation.this.getManagementContext();
                    ExecutionContext exec = mgmt.getServerExecutionContext();
                    result = Tasks.resolveValue(result, key.getType(), exec);

                } catch (Exception e) {
                    throw Exceptions.propagate(e);
                }
            }

            return TypeCoercions.coerce(result, key.getTypeToken());
        }

        @Override
        public <T> T set(ConfigKey<T> key, T val) {
            ConfigConstraints.assertValid(AbstractLocation.this, key, val);
            T result = configBag.put(key, val);
            onChanged();
            return result;
        }

        @Override
        public <T> T set(ConfigKey<T> key, Task<T> val) {
            // TODO Support for locations
            throw new UnsupportedOperationException();
        }

        @Override
        public ConfigBag getBag() {
            ConfigBag result = ConfigBag.newInstanceExtending(configBag, ImmutableMap.of());
            Location p = getParent();
            if (p!=null) result.putIfAbsent(((LocationInternal)p).config().getBag());
            return result;
        }

        @Override
        public ConfigBag getLocalBag() {
            return configBag;
        }

        @Override
        public Maybe<Object> getRaw(ConfigKey<?> key) {
            if (hasConfig(key, false)) return Maybe.of(getLocalBag().getStringKey(key.getName()));
            if (getParent() != null && isInherited(key)) return ((LocationInternal)getParent()).config().getRaw(key);
            return Maybe.absent();
        }

        @Override
        public Maybe<Object> getLocalRaw(ConfigKey<?> key) {
            if (hasConfig(key, false)) return Maybe.of(getLocalBag().getStringKey(key.getName()));
            return Maybe.absent();
        }

        @Override
        public void addToLocalBag(Map<String, ?> vals) {
            configBag.putAll(vals);
        }

        @Override
        public void removeFromLocalBag(String key) {
            configBag.remove(key);
        }

        @Override
        public void refreshInheritedConfig() {
            // no-op for location
        }
        
        @Override
        public void refreshInheritedConfigOfChildren() {
            // no-op for location
        }
        
        private boolean hasConfig(ConfigKey<?> key, boolean includeInherited) {
            if (includeInherited && isInherited(key)) {
                return getBag().containsKey(key);
            } else {
                return getLocalBag().containsKey(key);
            }
        }
        
        private boolean isInherited(ConfigKey<?> key) {
            ConfigInheritance inheritance = key.getInheritance();
            if (inheritance==null) inheritance = getDefaultInheritance();
            return inheritance.isInherited(key, getParent(), AbstractLocation.this);
        }

        private ConfigInheritance getDefaultInheritance() {
            return ConfigInheritance.ALWAYS;
        }

        @Override
        protected ExecutionContext getContext() {
            return AbstractLocation.this.getManagementContext().getServerExecutionContext();
        }
    }
    
    public class BasicSubscriptionSupport implements SubscriptionSupportInternal {
        @Override
        public <T> SubscriptionHandle subscribe(Entity producer, Sensor<T> sensor, SensorEventListener<? super T> listener) {
            return getSubscriptionTracker().subscribe(producer, sensor, listener);
        }

        @Override
        public <T> SubscriptionHandle subscribe(Map<String, ?> flags, Entity producer, Sensor<T> sensor, SensorEventListener<? super T> listener) {
            return getSubscriptionTracker().subscribe(flags, producer, sensor, listener);
        }

        @Override
        public <T> SubscriptionHandle subscribeToMembers(Group producerGroup, Sensor<T> sensor, SensorEventListener<? super T> listener) {
            return getSubscriptionTracker().subscribeToMembers(producerGroup, sensor, listener);
        }

        @Override
        public <T> SubscriptionHandle subscribeToChildren(Entity producerParent, Sensor<T> sensor, SensorEventListener<? super T> listener) {
            return getSubscriptionTracker().subscribeToChildren(producerParent, sensor, listener);
        }
        
        @Override
        public boolean unsubscribe(Entity producer) {
            return getSubscriptionTracker().unsubscribe(producer);
        }

        @Override
        public boolean unsubscribe(Entity producer, SubscriptionHandle handle) {
            return getSubscriptionTracker().unsubscribe(producer, handle);
        }

        @Override
        public boolean unsubscribe(SubscriptionHandle handle) {
            return getSubscriptionTracker().unsubscribe(handle);
        }

        @Override
        public void unsubscribeAll() {
            getSubscriptionTracker().unsubscribeAll();
        }

        protected SubscriptionTracker getSubscriptionTracker() {
            synchronized (AbstractLocation.this) {
                if (_subscriptionTracker!=null) return _subscriptionTracker;
                _subscriptionTracker = new SubscriptionTracker(newSubscriptionContext());
                return _subscriptionTracker;
            }
        }
        
        private SubscriptionContext newSubscriptionContext() {
            synchronized (AbstractLocation.this) {
                return getManagementContext().getSubscriptionContext(AbstractLocation.this);
            }
        }
    }
    
    @Override
    public <T> T getConfig(HasConfigKey<T> key) {
        return config().get(key);
    }

    @Override
    public <T> T getConfig(ConfigKey<T> key) {
        return config().get(key);
    }

    @Override
    @Deprecated
    public boolean hasConfig(ConfigKey<?> key, boolean includeInherited) {
        return config.hasConfig(key, includeInherited);
    }

    @Override
    @Deprecated
    public Map<String,Object> getAllConfig(boolean includeInherited) {
        // TODO Have no information about what to include/exclude inheritance wise.
        // however few things use getAllConfigBag()
        ConfigBag bag = (includeInherited ? config().getBag() : config().getLocalBag());
        return bag.getAllConfig();
    }
    
    @Override
    @Deprecated
    public ConfigBag getAllConfigBag() {
        // TODO see comments in EntityConfigMap and on interface methods. 
        // here ConfigBag is used exclusively so
        // we have no information about what to include/exclude inheritance wise.
        // however few things use getAllConfigBag()
        return config().getBag();
    }
    
    @Override
    public ConfigBag getLocalConfigBag() {
        return config().getLocalBag();
    }

    /** 
     * @deprecated since 0.7; use {@link #getLocalConfigBag()}
     * @since 0.6
     */
    @Deprecated
    public ConfigBag getRawLocalConfigBag() {
        return config().getLocalBag();
    }
    
    @Override
    @Deprecated
    public <T> T setConfig(ConfigKey<T> key, T value) {
        return config().set(key, value);
    }

    /**
     * @since 0.6.0 (?) - use getDisplayName
     * @deprecated since 0.7.0; use {@link #getDisplayName()}
     */
    @Deprecated
    public void setName(String newName) {
        setDisplayName(newName);
    }

    public void setDisplayName(String newName) {
        name.set(newName);
        displayNameAutoGenerated = false;
        onChanged();
    }

    @Override
    public boolean equals(Object o) {
        if (! (o instanceof Location)) {
            return false;
        }

        Location l = (Location) o;
        return getId().equals(l.getId());
    }

    @Override
    public int hashCode() {
        return getId().hashCode();
    }

    @Override
    public boolean containsLocation(Location potentialDescendent) {
        Location loc = potentialDescendent;
        while (loc != null) {
            if (this == loc) return true;
            loc = loc.getParent();
        }
        return false;
    }

    protected <T extends Location> T addChild(LocationSpec<T> spec) {
        T child = getManagementContext().getLocationManager().createLocation(spec);
        addChild(child);
        return child;
    }
    
    @SuppressWarnings("deprecation")
    public void addChild(Location child) {
        // Previously, setParent delegated to addChildLocation and we sometimes ended up with
        // duplicate entries here. Instead this now uses a similar scheme to 
        // AbstractLocation.setParent/addChild (with any weaknesses for distribution that such a 
        // scheme might have...).
        // 
        // We continue to use a list to allow identical-looking locations, but they must be different 
        // instances.
        
        synchronized (children) {
            for (Location contender : children) {
                if (contender == child) {
                    // don't re-add; no-op
                    return;
                }
            }

            children.add(child);
        }
        
        if (isManaged()) {
            if (!getManagementContext().getLocationManager().isManaged(child)) {
                Locations.manage(child, getManagementContext());
            }
        } else if (getManagementContext() != null) {
            if (((LocalLocationManager)getManagementContext().getLocationManager()).getLocationEvenIfPreManaged(child.getId()) == null) {
                ((ManagementContextInternal)getManagementContext()).prePreManage(child);
            }
        }

        children.add(child);
        child.setParent(this);
        
        onChanged();
    }
    
    public boolean removeChild(Location child) {
        boolean removed;
        synchronized (children) {
            removed = children.remove(child);
        }
        if (removed) {
            if (child instanceof Closeable) {
                Streams.closeQuietly((Closeable)child);
            }
            child.setParent(null);
            
            if (isManaged()) {
                getManagementContext().getLocationManager().unmanage(child);
            }
        }
        onChanged();
        return removed;
    }

    protected void onChanged() {
        // currently changes simply trigger re-persistence; there is no intermediate listener as we do for EntityChangeListener
        if (isManaged()) {
            getManagementContext().getRebindManager().getChangeListener().onChanged(this);
        }
    }

    /** Default String representation is simplified name of class, together with selected fields. */
    @Override
    public String toString() {
        return string().toString();
    }
    
    @Override
    public String toVerboseString() {
        return toString();
    }

    /** override this, adding to the returned value, to supply additional fields to include in the toString */
    protected ToStringHelper string() {
        return Objects.toStringHelper(getClass()).add("id", getId()).add("name", name);
    }
    
    @Override
    public HostGeoInfo getHostGeoInfo() { return hostGeoInfo.get(); }
    
    public void setHostGeoInfo(HostGeoInfo hostGeoInfo) {
        if (hostGeoInfo!=null) { 
            this.hostGeoInfo.set(hostGeoInfo);
            setConfig(LocationConfigKeys.LATITUDE, hostGeoInfo.latitude); 
            setConfig(LocationConfigKeys.LONGITUDE, hostGeoInfo.longitude); 
        } 
    }

    @Override
    public RebindSupport<LocationMemento> getRebindSupport() {
        return new BasicLocationRebindSupport(this);
    }
    
    @SuppressWarnings("unchecked")
    @Override
    public RelationSupportInternal<Location> relations() {
        return (RelationSupportInternal<Location>) super.relations();
    }
    
    @Override
    public boolean hasExtension(Class<?> extensionType) {
        return extensions.get().containsKey(checkNotNull(extensionType, "extensionType"));
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getExtension(Class<T> extensionType) {
        Object extension = extensions.get().get(checkNotNull(extensionType, "extensionType"));
        if (extension == null) {
            throw new IllegalArgumentException("No extension of type "+extensionType+" registered for location "+this);
        }
        return (T) extension;
    }
    
    @Override
    public <T> void addExtension(Class<T> extensionType, T extension) {
        checkNotNull(extensionType, "extensionType");
        checkNotNull(extension, "extension");
        checkArgument(extensionType.isInstance(extension), "extension %s does not implement %s", extension, extensionType);
        extensions.get().put(extensionType, extension);
    }

    @Override
    public Map<String, String> toMetadataRecord() {
        ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
        if (getDisplayName() != null) builder.put("displayName", getDisplayName());
        if (getParent() != null && getParent().getDisplayName() != null) {
            builder.put("parentDisplayName", getParent().getDisplayName());
        }
        return builder.build();
    }
}
