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
package org.apache.brooklyn.core.objs;

import static com.google.common.base.Preconditions.checkState;
import static org.apache.brooklyn.util.groovy.GroovyJavaMethods.truth;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntityLocal;
import org.apache.brooklyn.api.entity.Group;
import org.apache.brooklyn.api.mgmt.ExecutionContext;
import org.apache.brooklyn.api.mgmt.SubscriptionHandle;
import org.apache.brooklyn.api.mgmt.Task;
import org.apache.brooklyn.api.objs.BrooklynObject;
import org.apache.brooklyn.api.objs.Configurable;
import org.apache.brooklyn.api.objs.EntityAdjunct;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.api.sensor.Sensor;
import org.apache.brooklyn.api.sensor.SensorEventListener;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.config.ConfigKey.HasConfigKey;
import org.apache.brooklyn.config.ConfigMap;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.enricher.AbstractEnricher;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.EntityInternal;
import org.apache.brooklyn.core.mgmt.internal.SubscriptionTracker;
import org.apache.brooklyn.util.core.config.ConfigBag;
import org.apache.brooklyn.util.core.flags.FlagUtils;
import org.apache.brooklyn.util.core.flags.SetFromFlag;
import org.apache.brooklyn.util.core.flags.TypeCoercions;
import org.apache.brooklyn.util.guava.Maybe;
import org.apache.brooklyn.util.text.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.Beta;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;


/**
 * Common functionality for policies and enrichers
 */
public abstract class AbstractEntityAdjunct extends AbstractBrooklynObject implements BrooklynObjectInternal, EntityAdjunct, Configurable {
    private static final Logger log = LoggerFactory.getLogger(AbstractEntityAdjunct.class);

    private boolean _legacyNoConstructionInit;

    /**
     * @deprecated since 0.7.0; leftover properties are put into config, since when coming from yaml this is normal.
     */
    @Deprecated
    protected Map<String,Object> leftoverProperties = Maps.newLinkedHashMap();

    protected transient ExecutionContext execution;

    private final BasicConfigurationSupport config = new BasicConfigurationSupport();

    private final BasicSubscriptionSupport subscriptions = new BasicSubscriptionSupport();

    /**
     * The config values of this entity. Updating this map should be done
     * via {@link #config()}.
     * 
     * @deprecated since 0.7.0; use {@link #config()} instead; this field may be made private or deleted in a future release.
     */
    @Deprecated
    protected final AdjunctConfigMap configsInternal = new AdjunctConfigMap(this);

    /**
     * @deprecated since 0.7.0; use {@link #getAdjunctType()} instead; this field may be made private or deleted in a future release.
     */
    @Deprecated
    protected final AdjunctType adjunctType = new AdjunctType(this);

    @SetFromFlag
    protected String name;
    
    protected transient EntityLocal entity;
    
    /** not for direct access; refer to as 'subscriptionTracker' via getter so that it is initialized */
    protected transient SubscriptionTracker _subscriptionTracker;
    
    private AtomicBoolean destroyed = new AtomicBoolean(false);
    
    @SetFromFlag(value="uniqueTag")
    protected String uniqueTag;

    public AbstractEntityAdjunct() {
        this(Collections.emptyMap());
    }
    
    public AbstractEntityAdjunct(@SuppressWarnings("rawtypes") Map properties) {
        super(properties);
        _legacyNoConstructionInit = (properties != null) && Boolean.TRUE.equals(properties.get("noConstructionInit"));
        
        if (isLegacyConstruction()) {
            AbstractBrooklynObject checkWeGetThis = configure(properties);
            assert this.equals(checkWeGetThis) : this+" configure method does not return itself; returns "+checkWeGetThis+" instead of "+this;

            boolean deferConstructionChecks = (properties.containsKey("deferConstructionChecks") && TypeCoercions.coerce(properties.get("deferConstructionChecks"), Boolean.class));
            if (!deferConstructionChecks) {
                FlagUtils.checkRequiredFields(this);
            }
        }
    }

    /**
     * @deprecated since 0.7.0; only used for legacy brooklyn types where constructor is called directly
     */
    @Override
    @Deprecated
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public AbstractEntityAdjunct configure(Map flags) {
        // TODO only set on first time through
        boolean isFirstTime = true;
        
        // allow config keys, and fields, to be set from these flags if they have a SetFromFlag annotation
        // or if the value is a config key
        for (Iterator<Map.Entry> iter = flags.entrySet().iterator(); iter.hasNext();) {
            Map.Entry entry = iter.next();
            if (entry.getKey() instanceof ConfigKey) {
                ConfigKey key = (ConfigKey)entry.getKey();
                if (adjunctType.getConfigKeys().contains(key)) {
                    setConfig(key, entry.getValue());
                } else {
                    log.warn("Unknown configuration key {} for policy {}; ignoring", key, this);
                    iter.remove();
                }
            }
        }

        ConfigBag bag = new ConfigBag().putAll(flags);
        FlagUtils.setFieldsFromFlags(this, bag, isFirstTime);
        FlagUtils.setAllConfigKeys(this, bag, false);
        leftoverProperties.putAll(bag.getUnusedConfig());

        //replace properties _contents_ with leftovers so subclasses see leftovers only
        flags.clear();
        flags.putAll(leftoverProperties);
        leftoverProperties = flags;
        
        if (!truth(name) && flags.containsKey("displayName")) {
            //TODO inconsistent with entity and location, where name is legacy and displayName is encouraged!
            //'displayName' is a legacy way to refer to a policy's name
            Preconditions.checkArgument(flags.get("displayName") instanceof CharSequence, "'displayName' property should be a string");
            setDisplayName(flags.remove("displayName").toString());
        }
        
        // set leftover flags should as config items; particularly useful when these have come from a brooklyn.config map 
        for (Object flag: flags.keySet()) {
            ConfigKey<Object> key = ConfigKeys.newConfigKey(Object.class, Strings.toString(flag));
            if (config().getRaw(key).isPresent()) {
                log.warn("Config '"+flag+"' on "+this+" conflicts with key already set; ignoring");
            } else {
                config().set(key, flags.get(flag));
            }
        }
        
        return this;
    }
    
    /**
     * Used for legacy-style policies/enrichers on rebind, to indicate that init() should not be called.
     * Will likely be deleted in a future release; should not be called apart from by framework code.
     */
    @Beta
    protected boolean isLegacyNoConstructionInit() {
        return _legacyNoConstructionInit;
    }

    @Override
    public ConfigurationSupportInternal config() {
        return config;
    }

    @Override
    public BasicSubscriptionSupport subscriptions() {
        return subscriptions;
    }

    public class BasicSubscriptionSupport implements SubscriptionSupportInternal {
        @Override
        public <T> SubscriptionHandle subscribe(Entity producer, Sensor<T> sensor, SensorEventListener<? super T> listener) {
            if (!checkCanSubscribe()) return null;
            return getSubscriptionTracker().subscribe(producer, sensor, listener);
        }

        @Override
        public <T> SubscriptionHandle subscribe(Map<String, ?> flags, Entity producer, Sensor<T> sensor, SensorEventListener<? super T> listener) {
            if (!checkCanSubscribe()) return null;
            return getSubscriptionTracker().subscribe(flags, producer, sensor, listener);
        }

        @Override
        public <T> SubscriptionHandle subscribeToMembers(Group producerGroup, Sensor<T> sensor, SensorEventListener<? super T> listener) {
            if (!checkCanSubscribe(producerGroup)) return null;
            return getSubscriptionTracker().subscribeToMembers(producerGroup, sensor, listener);
        }

        @Override
        public <T> SubscriptionHandle subscribeToChildren(Entity producerParent, Sensor<T> sensor, SensorEventListener<? super T> listener) {
            if (!checkCanSubscribe(producerParent)) return null;
            return getSubscriptionTracker().subscribeToChildren(producerParent, sensor, listener);
        }
        
        @Override
        public boolean unsubscribe(Entity producer) {
            if (destroyed.get()) return false;
            return getSubscriptionTracker().unsubscribe(producer);
        }

        @Override
        public boolean unsubscribe(Entity producer, SubscriptionHandle handle) {
            if (destroyed.get()) return false;
            return getSubscriptionTracker().unsubscribe(producer, handle);
        }

        @Override
        public boolean unsubscribe(SubscriptionHandle handle) {
            if (destroyed.get()) return false;
            return getSubscriptionTracker().unsubscribe(handle);
        }

        @Override
        public void unsubscribeAll() {
            if (destroyed.get()) return;
            getSubscriptionTracker().unsubscribeAll();
        }

        protected SubscriptionTracker getSubscriptionTracker() {
            synchronized (AbstractEntityAdjunct.this) {
                if (_subscriptionTracker!=null) return _subscriptionTracker;
                if (entity==null) return null;
                _subscriptionTracker = new SubscriptionTracker(((EntityInternal)entity).getManagementSupport().getSubscriptionContext());
                return _subscriptionTracker;
            }
        }
        
        /** returns false if deleted, throws exception if invalid state, otherwise true.
         * okay if entity is not yet managed (but not if entity is no longer managed). */
        protected boolean checkCanSubscribe(Entity producer) {
            if (destroyed.get()) return false;
            if (producer==null) throw new IllegalStateException(this+" given a null target for subscription");
            if (entity==null) throw new IllegalStateException(this+" cannot subscribe to "+producer+" because it is not associated to an entity");
            if (((EntityInternal)entity).getManagementSupport().isNoLongerManaged()) throw new IllegalStateException(this+" cannot subscribe to "+producer+" because the associated entity "+entity+" is no longer managed");
            return true;
        }
        
        protected boolean checkCanSubscribe() {
            if (destroyed.get()) return false;
            if (entity==null) throw new IllegalStateException(this+" cannot subscribe because it is not associated to an entity");
            if (((EntityInternal)entity).getManagementSupport().isNoLongerManaged()) throw new IllegalStateException(this+" cannot subscribe because the associated entity "+entity+" is no longer managed");
            return true;
        }

        /**
         * @return a list of all subscription handles
         */
        protected Collection<SubscriptionHandle> getAllSubscriptions() {
            SubscriptionTracker tracker = getSubscriptionTracker();
            return (tracker != null) ? tracker.getAllSubscriptions() : Collections.<SubscriptionHandle>emptyList();
        }
    }
    
    private class BasicConfigurationSupport implements ConfigurationSupportInternal {

        @Override
        public <T> T get(ConfigKey<T> key) {
            return configsInternal.getConfig(key);
        }

        @Override
        public <T> T get(HasConfigKey<T> key) {
            return get(key.getConfigKey());
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> T set(ConfigKey<T> key, T val) {
            if (entity != null && isRunning()) {
                doReconfigureConfig(key, val);
            }
            T result = (T) configsInternal.setConfig(key, val);
            onChanged();
            return result;
        }

        @Override
        public <T> T set(HasConfigKey<T> key, T val) {
            return setConfig(key.getConfigKey(), val);
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> T set(ConfigKey<T> key, Task<T> val) {
            if (entity != null && isRunning()) {
                // TODO Support for AbstractEntityAdjunct
                throw new UnsupportedOperationException();
            }
            T result = (T) configsInternal.setConfig(key, val);
            onChanged();
            return result;
        }

        @Override
        public <T> T set(HasConfigKey<T> key, Task<T> val) {
            return set(key.getConfigKey(), val);
        }

        @Override
        public ConfigBag getBag() {
            return getLocalBag();
        }

        @Override
        public ConfigBag getLocalBag() {
            return ConfigBag.newInstance(configsInternal.getAllConfig());
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
            // no-op for location
        }
        
        @Override
        public void refreshInheritedConfigOfChildren() {
            // no-op for location
        }
    }

    public <T> T getConfig(ConfigKey<T> key) {
        return config().get(key);
    }
    
    protected <K> K getRequiredConfig(ConfigKey<K> key) {
        K result = config().get(key);
        if (result==null) 
            throw new NullPointerException("Value required for '"+key.getName()+"' in "+this);
        return result;
    }

    @Override
    @Deprecated
    public <T> T setConfig(ConfigKey<T> key, T val) {
        return config().set(key, val);
    }
    
    // TODO make immutable
    /** for inspection only */
    @Beta
    @Deprecated
    public ConfigMap getConfigMap() {
        return configsInternal;
    }
    
    /**
     * Invoked whenever a config change is applied after management is started.
     * Default implementation throws an exception to disallow the change. 
     * Can be overridden to return (allowing the change) or to make other changes 
     * (if necessary), and of course it can do this selectively and call the super to disallow any others. */
    protected <T> void doReconfigureConfig(ConfigKey<T> key, T val) {
        throw new UnsupportedOperationException("reconfiguring "+key+" unsupported for "+this);
    }
    
    @Override
    protected void onTagsChanged() {
        onChanged();
    }
    
    protected abstract void onChanged();
    
    protected AdjunctType getAdjunctType() {
        return adjunctType;
    }
    
    @Override
    public String getDisplayName() {
        if (name!=null && name.length()>0) return name;
        return getClass().getCanonicalName();
    }
    
    public void setDisplayName(String name) {
        this.name = name;
    }

    public void setEntity(EntityLocal entity) {
        if (destroyed.get()) throw new IllegalStateException("Cannot set entity on a destroyed entity adjunct");
        this.entity = entity;
        if (entity!=null && getCatalogItemId() == null) {
            setCatalogItemId(entity.getCatalogItemId());
        }
    }
    
    /** @deprecated since 0.7.0 only {@link AbstractEnricher} has emit convenience */
    protected <T> void emit(Sensor<T> sensor, Object val) {
        checkState(entity != null, "entity must first be set");
        if (val == Entities.UNCHANGED) {
            return;
        }
        if (val == Entities.REMOVE) {
            ((EntityInternal)entity).removeAttribute((AttributeSensor<T>) sensor);
            return;
        }
        
        T newVal = TypeCoercions.coerce(val, sensor.getTypeToken());
        if (sensor instanceof AttributeSensor) {
            entity.sensors().set((AttributeSensor<T>)sensor, newVal);
        } else { 
            entity.sensors().emit(sensor, newVal);
        }
    }

    /**
     * @deprecated since 0.9.0; for internal use only
     */
    @Deprecated
    protected synchronized SubscriptionTracker getSubscriptionTracker() {
        if (_subscriptionTracker!=null) return _subscriptionTracker;
        if (entity==null) return null;
        _subscriptionTracker = new SubscriptionTracker(((EntityInternal)entity).getManagementSupport().getSubscriptionContext());
        return _subscriptionTracker;
    }
    
    /**
     * @deprecated since 0.9.0; see {@link SubscriptionSupport#subscribe(Entity, Sensor, SensorEventListener)} and {@link BrooklynObject#subscriptions()}
     */
    @Deprecated
    public <T> SubscriptionHandle subscribe(Entity producer, Sensor<T> sensor, SensorEventListener<? super T> listener) {
        if (!checkCanSubscribe()) return null;
        return getSubscriptionTracker().subscribe(producer, sensor, listener);
    }

    /**
     * @deprecated since 0.9.0; see {@link SubscriptionSupport#subscribeToMembers(Entity, Sensor, SensorEventListener)} and {@link BrooklynObject#subscriptions()}
     */
    @Deprecated
    public <T> SubscriptionHandle subscribeToMembers(Group producerGroup, Sensor<T> sensor, SensorEventListener<? super T> listener) {
        if (!checkCanSubscribe(producerGroup)) return null;
        return getSubscriptionTracker().subscribeToMembers(producerGroup, sensor, listener);
    }

    /**
     * @deprecated since 0.9.0; see {@link SubscriptionSupport#subscribeToChildren(Entity, Sensor, SensorEventListener)} and {@link BrooklynObject#subscriptions()}
     */
    @Deprecated
    public <T> SubscriptionHandle subscribeToChildren(Entity producerParent, Sensor<T> sensor, SensorEventListener<? super T> listener) {
        if (!checkCanSubscribe(producerParent)) return null;
        return getSubscriptionTracker().subscribeToChildren(producerParent, sensor, listener);
    }

    /**
     * @deprecated since 0.7.0 use {@link BasicSubscriptionSupport#checkCanSubscribe(Entity)
     */
    @Deprecated
    protected boolean check(Entity requiredEntity) {
        return checkCanSubscribe(requiredEntity);
    }
    
    /**
     * @deprecated since 0.9.0; for internal use only
     */
    @Deprecated
    protected boolean checkCanSubscribe(Entity producer) {
        return subscriptions().checkCanSubscribe(producer);
    }
    
    /**
     * @deprecated since 0.9.0; for internal use only
     */
    @Deprecated
    protected boolean checkCanSubscribe() {
        return subscriptions().checkCanSubscribe();
    }
        
    /**
     * @deprecated since 0.9.0; see {@link SubscriptionSupport#unsubscribe(Entity)} and {@link BrooklynObject#subscriptions()}
     */
    @Deprecated
    public boolean unsubscribe(Entity producer) {
        return subscriptions().unsubscribe(producer);
    }

    /**
     * @deprecated since 0.9.0; see {@link SubscriptionSupport#unsubscribe(Entity, SubscriptionHandle)} and {@link BrooklynObject#subscriptions()}
     */
    @Deprecated
    public boolean unsubscribe(Entity producer, SubscriptionHandle handle) {
        return subscriptions().unsubscribe(producer, handle);
    }

    /**
     * @deprecated since 0.9.0; for internal use only
     */
    @Deprecated
    protected Collection<SubscriptionHandle> getAllSubscriptions() {
        SubscriptionTracker tracker = getSubscriptionTracker();
        return (tracker != null) ? tracker.getAllSubscriptions() : Collections.<SubscriptionHandle>emptyList();
    }
    
    /** 
     * Unsubscribes and clears all managed subscriptions; is called by the owning entity when a policy is removed
     * and should always be called by any subclasses overriding this method
     */
    public void destroy() {
        destroyed.set(true);
        SubscriptionTracker tracker = getSubscriptionTracker();
        if (tracker != null) tracker.unsubscribeAll();
    }
    
    @Override
    public boolean isDestroyed() {
        return destroyed.get();
    }
    
    @Override
    public boolean isRunning() {
        return !isDestroyed();
    }

    @Override
    public String getUniqueTag() {
        return uniqueTag;
    }

    public TagSupport tags() {
        return new AdjunctTagSupport();
    }

    public class AdjunctTagSupport extends BasicTagSupport {
        @Override
        public Set<Object> getTags() {
            ImmutableSet.Builder<Object> rb = ImmutableSet.builder().addAll(super.getTags());
            if (getUniqueTag()!=null) rb.add(getUniqueTag());
            return rb.build();
        }
        public String getUniqueTag() {
            return AbstractEntityAdjunct.this.getUniqueTag();
        }
        public void setUniqueTag(String uniqueTag) {
            AbstractEntityAdjunct.this.uniqueTag = uniqueTag;
        }
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(getClass()).omitNullValues()
                .add("name", name)
                .add("uniqueTag", uniqueTag)
                .add("running", isRunning())
                .add("entity", entity)
                .add("id", getId())
                .toString();
    }
}
