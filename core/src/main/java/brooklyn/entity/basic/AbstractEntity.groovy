package brooklyn.entity.basic

import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.util.Collection;
import java.util.concurrent.CancellationException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.enricher.basic.AbstractEnricher
import brooklyn.entity.Application
import brooklyn.entity.ConfigKey
import brooklyn.entity.Effector
import brooklyn.entity.Entity
import brooklyn.entity.EntityClass
import brooklyn.entity.Group
import brooklyn.entity.ConfigKey.HasConfigKey
import brooklyn.event.AttributeSensor
import brooklyn.event.Sensor
import brooklyn.event.SensorEvent
import brooklyn.event.SensorEventListener
import brooklyn.event.basic.AttributeMap
import brooklyn.event.basic.AttributeSensorAndConfigKey
import brooklyn.event.basic.BasicNotificationSensor
import brooklyn.event.basic.ListConfigKey
import brooklyn.event.basic.MapConfigKey
import brooklyn.event.basic.SubElementConfigKey
import brooklyn.location.Location
import brooklyn.management.ExecutionContext
import brooklyn.management.ManagementContext
import brooklyn.management.SubscriptionContext
import brooklyn.management.SubscriptionHandle
import brooklyn.management.Task
import brooklyn.management.internal.SubscriptionTracker;
import brooklyn.policy.Enricher
import brooklyn.policy.Policy
import brooklyn.policy.basic.AbstractPolicy
import brooklyn.util.BrooklynLanguageExtensions
import brooklyn.util.flags.FlagUtils
import brooklyn.util.flags.SetFromFlag
import brooklyn.util.flags.TypeCoercions
import brooklyn.util.internal.ConfigKeySelfExtracting
import brooklyn.util.internal.LanguageUtils
import brooklyn.util.task.BasicExecutionContext

import com.google.common.collect.ImmutableList
import com.google.common.collect.SetMultimap;

/**
 * Default {@link Entity} implementation.
 *
 * Provides several common fields ({@link #name}, {@link #id});
 * a map {@link #config} which contains arbitrary config data;
 * sensors and effectors; policies; managementContext.
 * <p>
 * Fields in config can be accessed (get and set) without referring to config,
 * (through use of propertyMissing). Note that config is typically inherited
 * by children, whereas the fields are not. (Attributes cannot be so accessed,
 * nor are they inherited.)
 */
public abstract class AbstractEntity implements EntityLocal, GroovyInterceptable {
    
    protected static final Logger LOG = LoggerFactory.getLogger(AbstractEntity.class)
    static { BrooklynLanguageExtensions.init(); }
    
    public static BasicNotificationSensor<Sensor> SENSOR_ADDED = new BasicNotificationSensor<Sensor>(Sensor.class,
            "entity.sensor.added", "Sensor dynamically added to entity")
    public static BasicNotificationSensor<Sensor> SENSOR_REMOVED = new BasicNotificationSensor<Sensor>(Sensor.class,
            "entity.sensor.removed", "Sensor dynamically removed from entity")

    final String id = LanguageUtils.newUid()
    String displayName
    
    EntityReference owner
    protected volatile EntityReference<Application> application
    final EntityCollectionReference<Group> groups = new EntityCollectionReference<Group>(this);
    
    final EntityCollectionReference ownedChildren = new EntityCollectionReference<Entity>(this);

    Map<String,Object> presentationAttributes = [:]
    Collection<AbstractPolicy> policies = [] as CopyOnWriteArrayList
    Collection<AbstractEnricher> enrichers = [] as CopyOnWriteArrayList
    Collection<Location> locations = [] as CopyOnWriteArrayList

    // FIXME we do not currently support changing owners, but to implement a cluster that can shrink we need to support at least
    // removing ownership. This flag notes if the class has previously been owned, and if an attempt is made to set a new owner
    // an exception will be thrown.
    boolean previouslyOwned = false

    // following two perhaps belong in entity class in a registry;
    // but that is an optimization, and possibly wrong if we have dynamic sensors/effectors
    // (added only to this instance), however if we did we'd need to reset/update entity class
    // on sensor/effector set change

    /** Map of effectors on this entity by name, populated at constructor time. */
    private Map<String,Effector> effectors = null

    /** Map of sensors on this entity by name, populated at constructor time. */
    private Map<String,Sensor> sensors = null

    /** Map of config keys on this entity by name, populated at constructor time. */
	private Map<String,Sensor> configKeys = null

    private transient EntityClass entityClass = null
    protected transient ExecutionContext execution
    protected transient SubscriptionContext subscription
    protected transient ManagementContext managementContext

    /**
     * The sensor-attribute values of this entity. Updating this map should be done
     * via getAttribute/setAttribute; it will automatically emit an attribute-change event.
     */
    protected final AttributeMap attributesInternal = new AttributeMap(this)

    /**
     * For temporary data, e.g. timestamps etc for calculating real attribute values, such as when
     * calculating averages over time etc.
     */
    protected final Map<String,Object> tempWorkings = [:]

    /*
     * TODO An alternative implementation approach would be to have:
     *   setOwner(Entity o, Map<ConfigKey,Object> inheritedConfig=[:])
     * The idea is that the owner could in theory decide explicitly what in its config
     * would be shared.
     * I (Aled) am undecided as to whether that would be better...
     * 
     * (Alex) i lean toward the config key getting to make the decision
     */
    /**
     * Map of configuration information that is defined at start-up time for the entity. These
     * configuration parameters are shared and made accessible to the "owned children" of this
     * entity.
     */
    protected final Map<ConfigKey,Object> ownConfig = [:]
    protected final Map<ConfigKey,Object> inheritedConfig = [:]

    protected transient SubscriptionTracker _subscriptionTracker;

    public AbstractEntity(Entity owner) {
        this([:], owner)
    }

    public AbstractEntity(Map flags=[:], Entity owner=null) {
        this.@skipInvokeMethodEffectorInterception.set(true)
        try {
            if (flags==null) {
                throw new IllegalArgumentException("Flags passed to entity $this must not be null (try no-arguments or empty map)")
            }
            if (flags.owner != null && owner != null && flags.owner != owner) {
                throw new IllegalArgumentException("Multiple owners supplied, ${flags.owner} and $owner")
            }
            if (owner!=null) flags.owner = owner;
            
            // initialize the effectors defined on the class
            // (dynamic effectors could still be added; see #getEffectors
			// TODO we could/should maintain a registry of EntityClass instances and re-use that,
			//      except where dynamic sensors/effectors are desired (nowhere currently I think)
            Map<String,Effector> effectorsT = [:]
            for (Field f in getClass().getFields()) {
                if (Effector.class.isAssignableFrom(f.getType())) {
                    Effector eff = f.get(this)
                    def overwritten = effectorsT.put(eff.name, eff)
                    if (overwritten!=null && !overwritten.is(eff)) 
                        LOG.warn("multiple definitions for effector ${eff.name} on $this; preferring $eff to $overwritten")
                }
            }
            if (LOG.isTraceEnabled())
                LOG.trace "Entity {} effectors: {}", id, effectorsT.keySet().join(", ")
            effectors = effectorsT
    
            Map<String,Sensor> sensorsT = [:]
            for (Field f in getClass().getFields()) {
                if (Sensor.class.isAssignableFrom(f.getType())) {
                    Sensor sens = f.get(this)
                    def overwritten = sensorsT.put(sens.name, sens)
                    if (overwritten!=null && !overwritten.is(sens)) 
                        LOG.warn("multiple definitions for sensor ${sens.name} on $this; preferring $sens to $overwritten")
                }
            }
            if (LOG.isTraceEnabled())
                LOG.trace "Entity {} sensors: {}", id, sensorsT.keySet().join(", ")
            sensors = sensorsT

            Map<String,ConfigKey> configT = [:]
            Map<String,Field> configFields = [:]
            for (Field f in getClass().getFields()) {
            	ConfigKey k = null;
                if (ConfigKey.class.isAssignableFrom(f.getType())) {
                	k = f.get(this)
                } else if (HasConfigKey.class.isAssignableFrom(f.getType())) {
					k = ((HasConfigKey)f.get(this)).getConfigKey();
				}
				if (k) {
				    Field alternativeField = configFields.get(k.name)
                    // Allow overriding config keys (e.g. to set default values) when there is an assignable-from relationship between classes
                    Field definitiveField = alternativeField ? inferSubbestField(alternativeField, f) : f
                    boolean skip = false;
                    if (definitiveField != f) {
                        // If they refer to the _same_ instance, just keep the one we already have
                        if (alternativeField.get(this).is(f.get(this))) skip = true;
                    }
                    if (skip) {
                        //nothing
                    } else if (definitiveField == f) {
                        def overwritten = configT.put(k.name, k)
                        configFields.put(k.name, f)
                    } else if (definitiveField != null) {
                        LOG.debug("multiple definitions for config key ${k.name} on $this; preferring that in sub-class: $alternativeField to $f")
                    } else if (definitiveField == null) {
                        LOG.warn("multiple definitions for config key ${k.name} on $this; preferring $alternativeField to $f")
                    }
                }
            }
            if (LOG.isTraceEnabled())
                LOG.trace "Entity {} config keys: {}", id, configT.keySet().join(", ")
            configKeys = configT

            def checkWeGetThis = configure(flags);
            assert this == checkWeGetThis : "$this configure method does not return itself; returns $checkWeGetThis instead"

        } finally { this.@skipInvokeMethodEffectorInterception.set(false) }
    }

    /**
     * Gets the field that is in the sub-class; or null if one field does not come from a sub-class of the other field's class
     */
    protected Field inferSubbestField(Field f1, Field f2) {
        Class<?> c1 = f1.getDeclaringClass()
        Class<?> c2 = f2.getDeclaringClass()
        boolean isSuper1 = c1.isAssignableFrom(c2)
        boolean isSuper2 = c2.isAssignableFrom(c1)
        return (isSuper1) ? (isSuper2 ? null : f2) : (isSuper2 ? f1 : null)
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
    public Entity configure(Map flags=[:]) {
        Entity suppliedOwner = flags.remove('owner') ?: null
        if (suppliedOwner) suppliedOwner.addOwnedChild(this)

        Map<ConfigKey,Object> suppliedOwnConfig = flags.remove('config')
        if (suppliedOwnConfig) ownConfig.putAll(suppliedOwnConfig)

        displayName = flags.remove('displayName') ?: displayName;
        
        // allow config keys, and fields, to be set from these flags if they have a SetFromFlag annotation
        for (Field f: FlagUtils.getAllFields(getClass())) {
            SetFromFlag cf = f.getAnnotation(SetFromFlag.class);
            if (cf) {
                ConfigKey key;
                if (ConfigKey.class.isAssignableFrom(f.getType())) {
                    key = f.get(this);
                } else if (HasConfigKey.class.isAssignableFrom(f.getType())) {
                    key = ((HasConfigKey)f.get(this)).getConfigKey();
                } else {
                    if ((f.getModifiers() & (Modifier.STATIC))!=0) {
                        LOG.warn "Unsupported {} on static on {} in {}; ignoring", SetFromFlag.class.getSimpleName(), f, this
                    } else {
                        //normal field, not a config key
                        String flagName = cf.value() ?: f.getName();
                        if (flagName && flags.containsKey(flagName)) {
                            Object v, value;
                            try {
                                v = flags.remove(flagName);
                                value = TypeCoercions.coerce(v, f.getType());
                                FlagUtils.setField(this, f, value, cf)
                            } catch (Exception e) {
                                throw new IllegalArgumentException("Cannot coerce or set "+v+" / "+value+" to "+f, e)
                            }
                        } else if (!flagName) {
                            LOG.warn "Unsupported {} on {} in {}; ignoring", SetFromFlag.class.getSimpleName(), f, this
                        }
                    }
                }
                if (key) {
                    String flagName = cf.value() ?: key?.getName();
                    if (flagName && flags.containsKey(flagName)) {
                        Object v = flags.remove(flagName);
                        setConfigInternal(key, v)
                        if (flagName=="name" && displayName==null)
                            displayName = v
                    }
                }
            }
        }

		if (displayName==null)
			displayName = flags.name ? flags.remove('name') : getClass().getSimpleName()+":"+id.substring(0, 4)
		
        if (!flags.isEmpty()) {
            LOG.warn "Unsupported flags when configuring {}; ignoring: {}", this, flags
        }

        return this;
    }
    
    /**
     * Adds this as a member of the given group, registers with application if necessary
     */
    public void setOwner(Entity entity) {
        if (owner != null) {
            // If we are changing to the same owner...
            if (owner.get() == entity) return
            // If we have an owner but changing to unowned...
            if (entity==null) { clearOwner(); return; }
            
            // We have an owner and are changing to another owner...
            throw new UnsupportedOperationException("Cannot change owner of $this from $owner to $entity (owner change not supported)")
        }
        // If we have previously had an owner and are trying to change to another one...
        if (previouslyOwned && entity != null) 
            throw new UnsupportedOperationException("Cannot set an owner of $this because it has previously had an owner")
        // We don't have an owner, never have and are changing to being owned...

        //make sure there is no loop
        if (this.equals(entity)) throw new IllegalStateException("entity $this cannot own itself")
		//this may be expensive, but preferable to throw before setting the owner!
        if (Entities.isDescendant(this, entity))
			throw new IllegalStateException("loop detected trying to set owner of $this as $entity, which is already a descendent")
        
        owner = new EntityReference(this, entity)
        //used to test entity!=null but that should be guaranteed?
        entity.addOwnedChild(this)
        inheritedConfig.putAll(entity.getAllConfig())
        previouslyOwned = true
        
        getApplication()
    }

    public void clearOwner() {
        if (owner == null) return
        Entity oldOwner = owner.get()
        owner = null
        oldOwner?.removeOwnedChild(this)
    }

    /**
     * Adds the given entity as a member of this group <em>and</em> this group as one of the groups of the child;
     * returns argument passed in, for convenience.
     */
    @Override
    public Entity addOwnedChild(Entity child) {
        synchronized (ownedChildren) {
	        if (Entities.isAncestor(this, child)) throw new IllegalStateException("loop detected trying to add child $child to $this; it is already an ancestor")
	        child.setOwner(this)
	        ownedChildren.add(child)
        }
        child
    }

    @Override
    public void removeOwnedChild(Entity child) {
        synchronized (ownedChildren) {
	        ownedChildren.remove child
	        child.clearOwner()
        }
    }

    /**
     * Adds this as a member of the given group, registers with application if necessary
     */
    @Override
    public void addGroup(Group e) {
        groups.add e
        getApplication()
    }

    @Override
    public Entity getOwner() { owner?.get() }

    @Override
    public Collection<Entity> getOwnedChildren() { ownedChildren.get() }
    
    @Override
    public Collection<Group> getGroups() { groups.get() }

    /**
     * Returns the application, looking it up if not yet known (registering if necessary)
     */
    @Override
    public Application getApplication() {
        if (this.@application!=null) return this.@application.get();
        def app = getOwner()?.getApplication()
        if (app) {
            setApplication(app)
        }
        app
    }

    protected synchronized void setApplication(Application app) {
        if (application) {
            if (this.@application.id!=app.id) {
                throw new IllegalStateException("Cannot change application of entity (attempted for $this from ${this.application} to ${app})")
            }
            return;
        }
        this.application = new EntityReference(this, app);
    }

    @Override
    public String getApplicationId() {
        getApplication()?.id
    }

    @Override
    public synchronized ManagementContext getManagementContext() {
        ManagementContext m = managementContext
        if (m) return m
        managementContext = getApplication()?.getManagementContext()
    }


    @Override
    public synchronized EntityClass getEntityClass() {
        if (entityClass) entityClass
        entityClass = new BasicEntityClass(this.class.canonicalName, configKeys.values(), sensors.values(), effectors.values()) 
    }

    @Override
    public Collection<Location> getLocations() {
        locations
    }

    /**
     * Should be invoked at end-of-life to clean up the item.
     */
    public void destroy() {
        // TODO we need some way of deleting stale items
    }

    @Override
    public <T> T getAttribute(AttributeSensor<T> attribute) {
        attributesInternal.getValue(attribute);
    }

    @Override
    public <T> T setAttribute(AttributeSensor<T> attribute, T val) {
        LOG.debug "setting attribute {} to {} on {}", attribute.name, val, this
        attributesInternal.update(attribute, val);
    }

    /** sets the value of the given attribute sensor from the config key value herein,
     * if the config key resolves to a non-null value as a sensor
     * <p>
     * returns old value */
    public <T> T setAttribute(AttributeSensorAndConfigKey<?,T> configuredSensor) {
        T v = getAttribute(configuredSensor);
        if (v!=null) return v;
        v = configuredSensor.getAsSensorValue(this);
        if (v!=null) return setAttribute(configuredSensor, v)
        return null;
    }

	/**
	 * ConfigKeys available on this entity.
	 */
	public Map<String,ConfigKey<?>> getConfigKeys() { configKeys }

	@Override
	public <T> T getConfig(ConfigKey<T> key) { getConfig(key, null) }
    
	@Override
	public <T> T getConfig(HasConfigKey<T> key) { getConfig(key, null) }
	
    @Override
    public <T> T getConfig(HasConfigKey<T> key, T defaultValue) {
        return getConfig(key.configKey, defaultValue)
    }
    
	//don't use groovy defaults for defaultValue as that doesn't implement the contract; we need the above
    @Override
    public <T> T getConfig(ConfigKey<T> key, T defaultValue) {
        // FIXME What about inherited task in config?!
		//              alex says: think that should work, no?
        // FIXME What if someone calls getConfig on a task, before setting parent app?
		//              alex says: not supported (throw exception, or return the task)
        
        // In case this entity class has overridden the given key (e.g. to set default), then retrieve this entity's key
        // TODO If ask for a config value that's not in our configKeys, should we really continue with rest of method and return key.getDefaultValue?
        //      e.g. SshBasedJavaAppSetup calls setAttribute(JMX_USER), which calls getConfig(JMX_USER)
        //           but that example doesn't have a default...
        ConfigKey<T> ownKey = getConfigKeys().get(key.getName()) ?: key
        
        ExecutionContext exec = getExecutionContext();
        
        // Don't use groovy truth: if the set value is e.g. 0, then would ignore set value and return default!
        if (ownKey in ConfigKeySelfExtracting) {
            if (((ConfigKeySelfExtracting)ownKey).isSet(ownConfig)) {
                return ((ConfigKeySelfExtracting)ownKey).extractValue(ownConfig, exec);
            } else if (((ConfigKeySelfExtracting)ownKey).isSet(inheritedConfig)) {
                return ((ConfigKeySelfExtracting)ownKey).extractValue(inheritedConfig, exec);
            }
        } else {
            LOG.warn("Config key $ownKey of $this is not a ConfigKeySelfExtracting; cannot retrieve value; returning default")
        }
        return TypeCoercions.coerce((defaultValue != null) ? defaultValue : ownKey.getDefaultValue(), key.type);
    }
    
    @Override
    public <T> T setConfig(ConfigKey<T> key, T val) {
        // TODO Is this the best idea, for making life easier for brooklyn coders when supporting changing config?
        if (getApplication()?.isDeployed()) throw new IllegalStateException("Cannot set configuration $key on active entity $this")

        setConfigInternal(key, val)
    }
    
    protected <T> T setConfigInternal(ConfigKey<T> key, T v) {
        Object val
        if ((v in Future) || (v in Closure)) {
            //no coercion for these (yet)
            val = v;
        } else {
            try {
                val = TypeCoercions.coerce(v, key.getType())
            } catch (Exception e) {
                throw new IllegalArgumentException("Cannot coerce or set "+v+" / "+val+" to "+key, e)
            }
        }
        T oldVal = ownConfig.put(key, val);        
        ownedChildren.get().each {
            it.refreshInheritedConfig()
        }

        oldVal
    }
    
	@Override
	public <T> T setConfig(HasConfigKey<T> key, T val) {
		setConfig(key.configKey, val)
	}

    protected void setConfigIfValNonNull(ConfigKey key, Object val) {
        if (val != null) setConfig(key, val)
    }
    protected void setConfigIfValNonNull(HasConfigKey key, Object val) {
        if (val != null) setConfig(key, val)
    }

    public void refreshInheritedConfig() {
        if (getOwner() != null) {
            inheritedConfig.putAll(getOwner().getAllConfig())
        } else {
            inheritedConfig.clear();
        }

        ownedChildren.get().each {
            it.refreshInheritedConfig()
        }
    }

    @Override
    public Map<ConfigKey,Object> getAllConfig() {
        // FIXME What about task-based config?!
        Map<ConfigKey,Object> result = [:]
        result.putAll(ownConfig);
        result.putAll(inheritedConfig);
        return result.asImmutable()
    }

    /** @see Entity#subscribe(Entity, Sensor, EventListener) */
    public <T> SubscriptionHandle subscribe(Entity producer, Sensor<T> sensor, SensorEventListener<? super T> listener) {
        return subscriptionTracker.subscribe(producer, sensor, listener)
    }

    /** @see Entity#subscribeToChildren(Entity, Sensor, EventListener) */
    public <T> SubscriptionHandle subscribeToChildren(Entity parent, Sensor<T> sensor, SensorEventListener<? super T> listener) {
        return subscriptionTracker.subscribeToChildren(parent, sensor, listener)
    }

    /**
     * Unsubscribes the given producer.
     *
     * @see SubscriptionContext#unsubscribe(SubscriptionHandle)
     */
    protected boolean unsubscribe(Entity producer) {
        return subscriptionTracker.unsubscribe(producer)
    }

    /**
    * Unsubscribes the given handle.
    *
    * @see SubscriptionContext#unsubscribe(SubscriptionHandle)
    */
   protected boolean unsubscribe(Entity producer, SubscriptionHandle handle) {
       return subscriptionTracker.unsubscribe(producer, handle)
   }

    protected synchronized SubscriptionContext getSubscriptionContext() {
        if (subscription) return subscription
        subscription = getManagementContext()?.getSubscriptionContext(this);
    }

    protected synchronized SubscriptionTracker getSubscriptionTracker() {
        if (_subscriptionTracker!=null) return _subscriptionTracker;
        _subscriptionTracker = new SubscriptionTracker(getSubscriptionContext());
    }
    
    public synchronized ExecutionContext getExecutionContext() {
        if (execution) return execution;
		def execMgr = getManagementContext()?.executionManager;
		if (!execMgr) return null
        execution = new BasicExecutionContext(tag:this, execMgr)
    }

    /** Default String representation is simplified name of class, together with selected fields. */
    @Override
    public String toString() {
        StringBuffer result = []
        result << getClass().getSimpleName()
        if (!result) result << getClass().getName()
		def fields = toStringFieldsToInclude()
        result << "[" << fields.collect({
            def v = this.hasProperty(it) ? this[it] : null  /* TODO would like to use attributes, config: this.properties[it] */
            v ? "$it=$v" : null
        }).findAll({it!=null}).join(",") << "]"
    }

    /** override this, adding to the collection, to supply fields whose value, if not null, should be included in the toString */
    public Collection<String> toStringFieldsToInclude() { 
		Set fields = ['id']
		if (!this.hasProperty('name')) fields << 'displayName'
		else fields << 'name'
	}

    
    // -------- POLICIES --------------------

    @Override
    public Collection<Policy> getPolicies() {
        return policies.asImmutable()
    }

    @Override
    public void addPolicy(AbstractPolicy policy) {
        policies.add(policy)
        policy.setEntity(this)
    }

    @Override
    boolean removePolicy(AbstractPolicy policy) {
        policy.destroy()
        return policies.remove(policy)
    }
    
    @Override
    public Collection<Enricher> getEnrichers() {
        return enrichers.asImmutable()
    }

    @Override
    public void addEnricher(AbstractEnricher enricher) {
        enrichers.add(enricher)
        enricher.setEntity(this)
    }

    @Override
    boolean removeEnricher(AbstractEnricher enricher) {
        enricher.destroy()
        return enrichers.remove(enricher)
    }

    // -------- SENSORS --------------------

    @Override
    public <T> void emit(Sensor<T> sensor, T val) {
        if (sensor instanceof AttributeSensor) {
            LOG.warn("Strongly discouraged use of emit with attribute sensor $sensor $val; use setAttribute instead!", 
                new Throwable("location of discouraged attribute $sensor emit"))
        }
        if (val instanceof SensorEvent) {
            LOG.warn("Strongly discouraged use of emit with sensor event as value $sensor $val; value should be unpacked!",
                new Throwable("location of discouraged event $sensor emit"))
        }
        LOG.debug "Emitting sensor notification {} value {} on {}", sensor.name, val, this
        emitInternal(sensor, val);
    }
    
    @Override
    public <T> void emitInternal(Sensor<T> sensor, T val) {
        subscriptionContext?.publish(sensor.newEvent(this, val))
    }

    
	/**
	 * Sensors available on this entity.
	 */
	public Map<String,Sensor<?>> getSensors() { sensors }

    /** Convenience for finding named sensor in {@link #getSensor()} {@link Map}. */
    public <T> Sensor<T> getSensor(String sensorName) { getSensors()[sensorName] }

    /**
     * Add the given {@link Sensor} to this entity.
     */
    public void addSensor(Sensor<?> sensor) {
        sensors.put(sensor.name, sensor)
        emit(SENSOR_ADDED, sensor)
    }

    /**
     * Remove the named {@link Sensor} from this entity.
     */
    public void removeSensor(String sensorName) {
        Sensor removedSensor = sensors.remove(sensorName)
        if (removedSensor != null) {
            emit(SENSOR_REMOVED, removedSensor)
        }
    }

    // -------- EFFECTORS --------------

    /** Flag needed internally to prevent invokeMethod from recursing on itself. */     
    private ThreadLocal<Boolean> skipInvokeMethodEffectorInterception = new ThreadLocal() { protected Object initialValue() { Boolean.FALSE } }

    /** 
     * Called by groovy for all method invocations; pass-through for everything but effectors; 
     * effectors get wrapped in a new task (parented by current task if there is one).
     */
    public Object invokeMethod(String name, Object args) {
        if (!this.@skipInvokeMethodEffectorInterception.get()) {
            this.@skipInvokeMethodEffectorInterception.set(true);

            //args should be an array, warn if we got here wrongly (extra defensive as args accepts it, but it shouldn't happen here)
            if (args==null) LOG.warn("$this.$name invoked with incorrect args signature (null)", new Throwable("source of incorrect invocation of $this.$name"))
            else if (!args.class.isArray()) LOG.warn("$this.$name invoked with incorrect args signature (non-array ${args.class}): "+args, new Throwable("source of incorrect invocation of $this.$name"))

            try {
                Effector eff = effectors.get(name)
                if (eff) {
                    LOG.debug("Invoking effector {} on {} with args {}", name, this, args)
                    return getManagementContext().invokeEffectorMethodSync(this, eff, args);
                }
            } catch (CancellationException ce) {
	            LOG.info "Execution of effector {} on entity {} was cancelled", name, id
                throw ce;
            } catch (ExecutionException ee) {
                LOG.info "Execution of effector {} on entity {} failed with {}", name, id, ee
                // Exceptions thrown in Futures are wrapped
                throw ee.getCause()
            } finally { this.@skipInvokeMethodEffectorInterception.set(false); }
        }
        metaClass.invokeMethod(this, name, args);
    }

    /**
     * Effectors available on this entity.
     *
     * NB no work has been done supporting changing this after initialization,
     * but the idea of these so-called "dynamic effectors" has been discussed and it might be supported in future...
     */
    public Map<String,Effector<?>> getEffectors() { effectors }

    /** Convenience for finding named effector in {@link #getEffectors()} {@link Map}. */
    public <T> Effector<T> getEffector(String effectorName) { effectors[effectorName] }

    /** Invoke an {@link Effector} directly. */
    public <T> Task<T> invoke(Map parameters=[:], Effector<T> eff) {
        invoke(eff, parameters);
    }

    /**
     * Additional form supplied for when the parameter map needs to be made explicit.
     *
     * @see #invoke(Effector)
     */
    public <T> Task<T> invoke(Effector<T> eff, Map<String,?> parameters) {
        getManagementContext().invokeEffector(this, eff, parameters);
    }

    /**
     * Invoked by {@link ManagementContext} when this entity becomes managed at a particular management node,
     * including the initial management started and subsequent management node master-change for this entity.
     */
    public void onManagementBecomingMaster() {}
    
    /**
     * Invoked by {@link ManagementContext} when this entity becomes mastered at a particular management node,
     * including the final management end and subsequent management node master-change for this entity.
     */
    public void onManagementNoLongerMaster() {}

    /** Field for use only by management plane, to record remote destination when proxied. */
    public Object managementData = null;

    /** For use by management plane, to invalidate all fields (e.g. when an entity is changing to being proxied) */
    public void invalidate() {
        this.@owner.invalidate();
        this.@application.invalidate();
        ownedChildren.get().each { it.invalidate() }
        groups.get().each { it.invalidate() }
        
        entityClass = null
        execution = null
        subscription = null
    }
}

/**
 * Serialization helper.
 *
 * This masks (with transience) a remote entity (e.g a child or parent) during serialization,
 * by keeping a non-transient reference to the entity which owns the reference, 
 * and using his management context reference to find the referred Entity (master instance or proxy),
 * which is then cached.
 */
private class EntityReference<T extends Entity> implements Serializable {
    Entity referrer;

    String id;
    transient T entity = null;

    public EntityReference(Entity referrer, String id) {
        this.referrer = referrer;
        this.id = id;
    }

    public EntityReference(Entity referrer, Entity reference) {
        this(referrer, reference.id);
        entity = reference;
    }
    
    public T get() {
        T e = entity;
        if (e!=null) return e;
        find();
    }

    protected synchronized T find() {
        if (entity) return entity;
        if (!referrer)
            throw new IllegalStateException("EntityReference $id should have been initialised with a reference owner")
        entity = ((AbstractEntity)referrer).getManagementContext().getEntity(id);
    }
    
    synchronized void invalidate() {
        entity = null;
    }
    
    public String toString() {
        getClass().getSimpleName()+"["+get().toString()+"]"
    }
}


class SelfEntityReference<T extends Entity> extends EntityReference<T> {
    public SelfEntityReference(Entity self) {
        super(self, self);
    }
    protected synchronized T find() {
        return referrer;
    }
}

private class EntityCollectionReference<T extends Entity> implements Serializable {
    private static final Logger LOG = LoggerFactory.getLogger(EntityCollectionReference.class)
    Entity referrer;
    
    Collection<String> entityRefs = new LinkedHashSet<String>();
    transient Collection<T> entities = null;
    
    public EntityCollectionReference(Entity referrer) {
        this.referrer = referrer;
    }

    public synchronized boolean add(Entity e) {
        if (entityRefs.add(e.id)) {
            def e2 = new LinkedHashSet<T>(entities!=null?entities:Collections.emptySet());
            e2 << e
            entities = e2;
            return true
        } else {
            return false
        }
    }

    public synchronized boolean remove(Entity e) {
        if (entityRefs.remove(e.id) && entities!=null) {
            def e2 = new LinkedHashSet<T>(entities);
            e2.remove(e);
            entities = e2;
            return true
        } else {
            return false
        }
    }

    public synchronized Collection<T> get() {
        Collection<T> result = entities;
        if (result==null) {
            result = find();
        }
        return ImmutableList.copyOf(result)
    }

    public synchronized boolean contains(Entity e) {
        return entityRefs.contains(e.id)
    }

    protected synchronized Collection<T> find() {
        if (entities!=null) return entities;
        if (!referrer)
            throw new IllegalStateException("EntityReference $id should have been initialised with a reference owner")
        Collection<T> result = new CopyOnWriteArrayList<T>();
        entityRefs.each { 
            def e = ((AbstractEntity)referrer).getManagementContext().getEntity(it); 
            if (e==null) 
                LOG.warn("unable to find $it, referred to by $referrer");
            else result << e;
        }
        entities = result;
    }
}
