package brooklyn.policy.basic;

import static brooklyn.util.GroovyJavaMethods.truth;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.config.ConfigMap;
import brooklyn.entity.Entity;
import brooklyn.entity.Group;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.proxying.InternalPolicyFactory;
import brooklyn.entity.rebind.RebindManagerImpl;
import brooklyn.entity.trait.Configurable;
import brooklyn.event.AttributeSensor;
import brooklyn.event.Sensor;
import brooklyn.event.SensorEventListener;
import brooklyn.management.ExecutionContext;
import brooklyn.management.ManagementContext;
import brooklyn.management.SubscriptionContext;
import brooklyn.management.SubscriptionHandle;
import brooklyn.management.internal.SubscriptionTracker;
import brooklyn.policy.EntityAdjunct;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.flags.FlagUtils;
import brooklyn.util.flags.SetFromFlag;
import brooklyn.util.flags.TypeCoercions;
import brooklyn.util.text.Identifiers;

import com.google.common.annotations.Beta;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;


/**
 * Common functionality for policies and enrichers
 */
public abstract class AbstractEntityAdjunct implements EntityAdjunct, Configurable {
    private static final Logger log = LoggerFactory.getLogger(AbstractEntityAdjunct.class);

    private volatile ManagementContext managementContext;
    protected Map<String,Object> leftoverProperties = Maps.newLinkedHashMap();

    private boolean _legacyConstruction;
    private boolean _legacyNoConstructionInit;
    
    // TODO not sure if we need this -- never read
    @SuppressWarnings("unused")
    private boolean inConstruction;

    protected transient ExecutionContext execution;

    /**
     * The config values of this entity. Updating this map should be done
     * via getConfig/setConfig.
     */
    protected final ConfigMapImpl configsInternal = new ConfigMapImpl(this);

    protected final AdjunctType adjunctType = new AdjunctType(this);

    @SetFromFlag
    protected String id = Identifiers.makeRandomId(8);
    
    @SetFromFlag
    protected String name;
    
    protected transient EntityLocal entity;
    
    /** not for direct access; refer to as 'subscriptionTracker' via getter so that it is initialized */
    protected transient SubscriptionTracker _subscriptionTracker;
    
    private AtomicBoolean destroyed = new AtomicBoolean(false);

    public AbstractEntityAdjunct() {
        this(Collections.emptyMap());
    }
    
    public AbstractEntityAdjunct(@SuppressWarnings("rawtypes") Map flags) {
        inConstruction = true;
        _legacyConstruction = !InternalPolicyFactory.FactoryConstructionTracker.isConstructing();
        _legacyNoConstructionInit = (flags != null) && Boolean.TRUE.equals(flags.get("noConstructionInit"));
        
        if (!_legacyConstruction && flags!=null && !flags.isEmpty()) {
            log.debug("Using direct construction for "+getClass().getName()+" because properties were specified ("+flags+")");
            _legacyConstruction = true;
        }
        
        if (_legacyConstruction) {
            log.debug("Using direct construction for "+getClass().getName()+"; calling configure(Map) immediately");
            
            configure(flags);
            
            boolean deferConstructionChecks = (flags.containsKey("deferConstructionChecks") && TypeCoercions.coerce(flags.get("deferConstructionChecks"), Boolean.class));
            if (!deferConstructionChecks) {
                FlagUtils.checkRequiredFields(this);
            }
        }
        
        inConstruction = false;
    }

    /** will set fields from flags, and put the remaining ones into the 'leftovers' map.
     * can be subclassed for custom initialization but note the following. 
     * <p>
     * if you require fields to be initialized you must do that in this method. You must
     * *not* rely on field initializers because they may not run until *after* this method
     * (this method is invoked by the constructor in this class, so initializers
     * in subclasses will not have run when this overridden method is invoked.) */ 
    protected void configure() {
        configure(Collections.emptyMap());
    }
    
    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void configure(Map flags) {
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
            setName(flags.remove("displayName").toString());
        }
    }
    
    protected boolean isLegacyConstruction() {
        return _legacyConstruction;
    }

    /**
     * Used for legacy-style policies/enrichers on rebind, to indicate that init() should not be called.
     * Will likely be deleted in a future release; should not be called apart from by framework code.
     */
    @Beta
    protected boolean isLegacyNoConstructionInit() {
        return _legacyNoConstructionInit;
    }
    
    public void setManagementContext(ManagementContext managementContext) {
        this.managementContext = managementContext;
    }
    
    protected ManagementContext getManagementContext() {
        return managementContext;
    }

    /**
     * Called by framework (in new-style policies where PolicySpec was used) after configuring etc,
     * but before a reference to this policy is shared.
     * 
     * To preserve backwards compatibility for if the policy is constructed directly, one
     * can call the code below, but that means it will be called after references to this 
     * policy have been shared with other entities.
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
     * Called by framework (in new-style policies/enrichers where PolicySpec/EnricherSpec was used) on rebind, 
     * after configuring but before {@link #setEntity(EntityLocal)} and before a reference to this policy is shared.
     * Note that {@link #init()} will not be called on rebind.
     */
    public void rebind() {
        // no-op
    }
    
    protected boolean isRebinding() {
        return RebindManagerImpl.RebindTracker.isRebinding();
    }
    
    public <T> T getConfig(ConfigKey<T> key) {
        return configsInternal.getConfig(key);
    }
    
    public Map<ConfigKey<?>, Object> getAllConfig() {
        return configsInternal.getAllConfig();
    }
    
    protected <K> K getRequiredConfig(ConfigKey<K> key) {
        return checkNotNull(getConfig(key), key.getName());
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T setConfig(ConfigKey<T> key, T val) {
        if (entity != null && isRunning()) {
            doReconfigureConfig(key, val);
        }
        T result = (T) configsInternal.setConfig(key, val);
        onChanged();
        return result;
    }
    
    // TODO make immutable
    /** for inspection only */
    @Beta
    public ConfigMap getConfigMap() {
        return configsInternal;
    }
    
    protected <T> void doReconfigureConfig(ConfigKey<T> key, T val) {
        throw new UnsupportedOperationException("reconfiguring "+key+" unsupported for "+this);
    }
    
    protected abstract void onChanged();
    
    protected AdjunctType getAdjunctType() {
        return adjunctType;
    }
    
    @Override
    public String getName() { 
        if (name!=null && name.length()>0) return name;
        return getClass().getCanonicalName();
    }
    
    public void setName(String name) { this.name = name; }

    @Override
    public String getId() { return id; }
    
    public void setId(String id) { this.id = id; }
 
    public void setEntity(EntityLocal entity) {
        if (destroyed.get()) throw new IllegalStateException("Cannot set entity on a destroyed entity adjunct");
        this.entity = entity;
    }
    
    protected <T> void emit(Sensor<T> sensor, T val) {
        checkState(entity != null, "entity must first be set");
        if (sensor instanceof AttributeSensor) {
            entity.setAttribute((AttributeSensor<T>)sensor, val);
        } else { 
            entity.emit(sensor, val);
        }
    }

    protected synchronized SubscriptionTracker getSubscriptionTracker() {
        if (_subscriptionTracker!=null) return _subscriptionTracker;
        if (entity==null) return null;
        _subscriptionTracker = new SubscriptionTracker(((EntityInternal)entity).getManagementSupport().getSubscriptionContext());
        return _subscriptionTracker;
    }
    
    /** @see SubscriptionContext#subscribe(Entity, Sensor, SensorEventListener) */
    protected <T> SubscriptionHandle subscribe(Entity producer, Sensor<T> sensor, SensorEventListener<? super T> listener) {
        if (!checkCanSubscribe()) return null;
        return getSubscriptionTracker().subscribe(producer, sensor, listener);
    }

    /** @see SubscriptionContext#subscribe(Entity, Sensor, SensorEventListener) */
    protected <T> SubscriptionHandle subscribeToMembers(Group producerGroup, Sensor<T> sensor, SensorEventListener<? super T> listener) {
        if (!checkCanSubscribe(producerGroup)) return null;
        return getSubscriptionTracker().subscribeToMembers(producerGroup, sensor, listener);
    }

    /** @see SubscriptionContext#subscribe(Entity, Sensor, SensorEventListener) */
    protected <T> SubscriptionHandle subscribeToChildren(Entity producerParent, Sensor<T> sensor, SensorEventListener<? super T> listener) {
        if (!checkCanSubscribe(producerParent)) return null;
        return getSubscriptionTracker().subscribeToChildren(producerParent, sensor, listener);
    }

    /** @deprecated since 0.7.0 use {@link #checkCanSubscribe(Entity)} */
    @Deprecated
    protected boolean check(Entity requiredEntity) {
        return checkCanSubscribe(requiredEntity);
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
     * Unsubscribes the given producer.
     *
     * @see SubscriptionContext#unsubscribe(SubscriptionHandle)
     */
    protected boolean unsubscribe(Entity producer) {
        if (destroyed.get()) return false;
        return getSubscriptionTracker().unsubscribe(producer);
    }

    /**
    * Unsubscribes the given producer.
    *
    * @see SubscriptionContext#unsubscribe(SubscriptionHandle)
    */
   protected boolean unsubscribe(Entity producer, SubscriptionHandle handle) {
       if (destroyed.get()) return false;
       return getSubscriptionTracker().unsubscribe(producer, handle);
   }

    /**
    * @return a list of all subscription handles
    */
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
    public String toString() {
        return Objects.toStringHelper(getClass())
                .add("name", name)
                .add("running", isRunning())
                .toString();
    }
}
