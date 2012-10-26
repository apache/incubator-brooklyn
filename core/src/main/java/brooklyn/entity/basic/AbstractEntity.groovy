package brooklyn.entity.basic

import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutionException

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.config.ConfigKey
import brooklyn.config.ConfigKey.HasConfigKey
import brooklyn.enricher.basic.AbstractEnricher
import brooklyn.entity.Application
import brooklyn.entity.Effector
import brooklyn.entity.Entity
import brooklyn.entity.EntityType
import brooklyn.entity.Group
import brooklyn.entity.basic.EntityReferences.EntityCollectionReference
import brooklyn.entity.basic.EntityReferences.EntityReference
import brooklyn.entity.rebind.BasicEntityRebindSupport
import brooklyn.entity.rebind.RebindSupport
import brooklyn.event.AttributeSensor
import brooklyn.event.Sensor
import brooklyn.event.SensorEvent
import brooklyn.event.SensorEventListener
import brooklyn.event.basic.AttributeMap
import brooklyn.event.basic.AttributeSensorAndConfigKey
import brooklyn.event.basic.BasicNotificationSensor
import brooklyn.location.Location
import brooklyn.management.ExecutionContext
import brooklyn.management.ManagementContext
import brooklyn.management.SubscriptionContext
import brooklyn.management.SubscriptionHandle
import brooklyn.management.Task
import brooklyn.management.internal.AbstractManagementContext
import brooklyn.management.internal.EntityManagementSupport
import brooklyn.management.internal.SubscriptionTracker
import brooklyn.mementos.EntityMemento
import brooklyn.policy.Enricher
import brooklyn.policy.Policy
import brooklyn.policy.basic.AbstractPolicy
import brooklyn.util.BrooklynLanguageExtensions
import brooklyn.util.flags.FlagUtils
import brooklyn.util.flags.SetFromFlag
import brooklyn.util.text.Identifiers

import com.google.common.base.Objects
import com.google.common.base.Objects.ToStringHelper
import com.google.common.collect.ImmutableSet
import com.google.common.collect.Iterables
import com.google.common.collect.Maps

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
public abstract class AbstractEntity extends GroovyObjectSupport implements EntityLocal, GroovyInterceptable {
    
    protected static final Logger LOG = LoggerFactory.getLogger(AbstractEntity.class)
    static { BrooklynLanguageExtensions.init(); }
    
    public static BasicNotificationSensor<Sensor> SENSOR_ADDED = new BasicNotificationSensor<Sensor>(Sensor.class,
            "entity.sensor.added", "Sensor dynamically added to entity")
    public static BasicNotificationSensor<Sensor> SENSOR_REMOVED = new BasicNotificationSensor<Sensor>(Sensor.class,
            "entity.sensor.removed", "Sensor dynamically removed from entity")

    @SetFromFlag(value="id")
    private String id = Identifiers.makeRandomId(8);
    
    String displayName
    
    EntityReference<Entity> owner
    protected volatile EntityReference<Application> application
    final EntityCollectionReference<Group> groups = new EntityCollectionReference<Group>(this);
    
    final EntityCollectionReference ownedChildren = new EntityCollectionReference<Entity>(this);

    Map<String,Object> presentationAttributes = [:]
    Collection<AbstractPolicy> policies = [] as CopyOnWriteArrayList
    Collection<AbstractEnricher> enrichers = [] as CopyOnWriteArrayList
    Collection<Location> locations = Collections.newSetFromMap(new ConcurrentHashMap<Location,Boolean>())

    // FIXME we do not currently support changing owners, but to implement a cluster that can shrink we need to support at least
    // removing ownership. This flag notes if the class has previously been owned, and if an attempt is made to set a new owner
    // an exception will be thrown.
    boolean previouslyOwned = false

    /**
     * Whether we are still being constructed, in which case never warn in "assertNotYetOwned" 
     */
    private boolean preConfigured = true;
    
    private final EntityDynamicType entityType;
    
    protected transient EntityManagementSupport managementSupport;
    
    /**
     * The config values of this entity. Updating this map should be done
     * via getConfig/setConfig.
     */
    protected final EntityConfigMap configsInternal = new EntityConfigMap(this)

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

    protected transient SubscriptionTracker _subscriptionTracker;

    /**
     * FIXME Temporary workaround for use-case:
     *  - the load balancing policy test calls app.managementContext.unmanage(itemToStop)
     *  - concurrently, the policy calls an effector on that item: item.move()
     *  - The code in AbstractManagementContext.invokeEffectorMethodSync calls manageIfNecessary. 
     *    This detects that the item is not managed, and sets it as managed again. The item is automatically  
     *    added back into the dynamic group, and the policy receives an erroneous MEMBER_ADDED event.
     */
    private volatile boolean hasEverBeenManaged
    
    public AbstractEntity(Entity owner) {
        this([:], owner)
    }

    // FIXME don't leak this reference in constructor - even to utils
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
            
            // TODO Don't let `this` reference escape during construction
            entityType = new EntityDynamicType(this);
            
            def checkWeGetThis = configure(flags);
            assert this == checkWeGetThis : "$this configure method does not return itself; returns $checkWeGetThis instead"

            preConfigured = false;
            
        } finally { this.@skipInvokeMethodEffectorInterception.set(false) }
    }

    public String getId() {
        return id;
    }
    
    /** @deprecated since 0.4.0 now handled by EntityMangementSupport */
    public void setBeingManaged() {
        hasEverBeenManaged = true;
    }
    
    /** @deprecated since 0.4.0 now handled by EntityMangementSupport */
    public boolean hasEverBeenManaged() {
        return hasEverBeenManaged;
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
        assertNotYetOwned()
		
        Entity suppliedOwner = flags.remove('owner') ?: null
        if (suppliedOwner) suppliedOwner.addOwnedChild(this)

        Map<ConfigKey,Object> suppliedOwnConfig = flags.remove('config')
        if (suppliedOwnConfig != null) {
            for (Map.Entry<ConfigKey, Object> entry : suppliedOwnConfig.entrySet()) {
                setConfigEvenIfOwned(entry.getKey(), entry.getValue());
            }
        }

        displayName = flags.remove('displayName') ?: displayName;

        // allow config keys, and fields, to be set from these flags if they have a SetFromFlag annotation
        flags = FlagUtils.setConfigKeysFromFlags(flags, this);
        flags = FlagUtils.setFieldsFromFlags(flags, this);
        
		if (displayName==null)
			displayName = flags.name ? flags.remove('name') : getClass().getSimpleName()+":"+id.substring(0, 4)
		
        // all config keys specified in map should be set as config
        for (Iterator fi = flags.iterator(); fi.hasNext(); ) {
            Map.Entry entry = fi.next();
            Object k = entry.key;
            if (k in HasConfigKey) k = ((HasConfigKey)k).getConfigKey();
            if (k in ConfigKey) {
                setConfigEvenIfOwned(k, entry.value);
                fi.remove();
            }
        }
        
        if (!flags.isEmpty()) {
            LOG.warn "Unsupported flags when configuring {}; ignoring: {}", this, flags
        }

        return this;
    }
    
    /**
     * Sets a config key value, and returns this Entity instance for use in fluent-API style coding.
     */
    public <T> Entity configure(ConfigKey<T> key, T value) {
        setConfig(key, value);
        return this;
    }
    
    /**
     * Adds this as a member of the given group, registers with application if necessary
     */
    public AbstractEntity setOwner(Entity entity) {
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
        configsInternal.setInheritedConfig(entity.getAllConfig());
        previouslyOwned = true
        
        getApplication()
        
        return this;
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
            if (child == null) throw new NullPointerException("child must not be null (for entity "+this+")")
	        if (Entities.isAncestor(this, child)) throw new IllegalStateException("loop detected trying to add child $child to $this; it is already an ancestor")
	        child.setOwner(this)
	        ownedChildren.add(child)
            
            getManagementSupport().getEntityChangeListener().onChildrenChanged();
        }
        return child
    }

    @Override
    public boolean removeOwnedChild(Entity child) {
        synchronized (ownedChildren) {
            boolean changed = ownedChildren.remove child
	        child.clearOwner()
            
            if (changed) {
                getManagementSupport().getEntityChangeListener().onChildrenChanged();
            }
            return changed
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

    // TODO synchronization: need to synchronize on ownedChildren, or have ownedChildren be a synchronized collection
    @Override
    public Collection<Entity> getOwnedChildren() { ownedChildren.get() }
    
    public EntityCollectionReference getOwnedChildrenReference() { this.@ownedChildren }
    
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

    /** @deprecated since 0.4.0 should not be needed / leaked outwith brooklyn internals / mgmt support? */
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

    /** @deprecated since 0.4.0 should not be needed / leaked outwith brooklyn internals? */
    @Override
    @Deprecated
    public synchronized ManagementContext getManagementContext() {
        return getManagementSupport().getManagementContext(false);
    }

    @Override
    public EntityType getEntityType() {
        return entityType.getSnapshot();
    }

    protected EntityDynamicType getMutableEntityType() {
        return entityType;
    }
    
    @Override
    public Collection<Location> getLocations() {
        locations
    }

    @Override
    public void addLocations(Collection<? extends Location> newLocations) {
        locations.addAll(newLocations);
        
        getManagementSupport().getEntityChangeListener().onLocationsChanged();
    }

    @Override
    public void removeLocations(Collection<? extends Location> newLocations) {
        locations.removeAll(newLocations);
        
        getManagementSupport().getEntityChangeListener().onLocationsChanged();
    }

    public Location firstLocation() {
        return Iterables.get(locations, 0)
    }
    
    /**
     * Should be invoked at end-of-life to clean up the item.
     */
    public void destroy() {
        // TODO we need some way of deleting stale items
    }

    @Override
    public <T> T getAttribute(AttributeSensor<T> attribute) {
        return attributesInternal.getValue(attribute);
    }

    public <T> T getAttributeByNameParts(List<String> nameParts) {
        return attributesInternal.getValue(nameParts)
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

    @Override
    public void removeAttribute(AttributeSensor<?> attribute) {
        attributesInternal.remove(attribute);
        entityType.removeSensor(attribute);
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

    protected void assertNotYetOwned() {
        if (!preConfigured && getApplication()?.isDeployed())
            LOG.warn("configuration being made to $this after deployment; may not be supported in future versions");
        //throw new IllegalStateException("Cannot set configuration $key on active entity $this")
    }

    @Override
    public <T> T setConfig(ConfigKey<T> key, T val) {
        assertNotYetOwned()
        configsInternal.setConfig(key, val);
    }

    public <T> T setConfig(ConfigKey<T> key, Task<T> val) {
        assertNotYetOwned()
        configsInternal.setConfig(key, val);
    }

	@Override
	public <T> T setConfig(HasConfigKey<T> key, T val) {
		setConfig(key.configKey, val)
	}

    public <T> T setConfig(HasConfigKey<T> key, Task<T> val) {
        setConfig(key.configKey, val)
    }

    public <T> T setConfigEvenIfOwned(ConfigKey<T> key, T val) {
        configsInternal.setConfig(key, val);
    }

    public <T> T setConfigEvenIfOwned(HasConfigKey<T> key, T val) {
        setConfigEvenIfOwned(key.getConfigKey(), val);
    }

    protected void setConfigIfValNonNull(ConfigKey key, Object val) {
        if (val != null) setConfig(key, val)
    }
    
    protected void setConfigIfValNonNull(HasConfigKey key, Object val) {
        if (val != null) setConfig(key, val)
    }

    public void refreshInheritedConfig() {
        if (getOwner() != null) {
            configsInternal.setInheritedConfig(getOwner().getAllConfig())
        } else {
            configsInternal.clearInheritedConfig();
        }

        refreshInheritedConfigOfChildren();
    }

    void refreshInheritedConfigOfChildren() {
        ownedChildren.get().each {
            ((AbstractEntity)it).refreshInheritedConfig()
        }
    }

    public EntityConfigMap getConfigMap() {
        return configsInternal;
    }
    
    public Map<ConfigKey,Object> getAllConfig() {
        return configsInternal.getAllConfig();
    }

    public Map<AttributeSensor, Object> getAllAttributes() {
        Map<AttributeSensor, Object> result = Maps.newLinkedHashMap();
        Map<String, Object> attribs = attributesInternal.asMap();
        for (Map.Entry<?,Object> entry : attribs.entrySet()) {
            AttributeSensor attribKey = (AttributeSensor) entityType.getSensor(entry.getKey());
            result.put(attribKey, entry.getValue());
        }
        return result;
    }

    /** @see EntityLocal#subscribe */
    public <T> SubscriptionHandle subscribe(Entity producer, Sensor<T> sensor, SensorEventListener<? super T> listener) {
        return subscriptionTracker.subscribe(producer, sensor, listener)
    }

    /** @see EntityLocal#subscribeToChildren */
    public <T> SubscriptionHandle subscribeToChildren(Entity parent, Sensor<T> sensor, SensorEventListener<? super T> listener) {
        return subscriptionTracker.subscribeToChildren(parent, sensor, listener)
    }

    /** @see EntityLocal#subscribeToMembers */
    public <T> SubscriptionHandle subscribeToMembers(Group group, Sensor<T> sensor, SensorEventListener<? super T> listener) {
        return subscriptionTracker.subscribeToMembers(group, sensor, listener)
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

    public synchronized SubscriptionContext getSubscriptionContext() {
        return getManagementSupport().getSubscriptionContext();
    }

    protected synchronized SubscriptionTracker getSubscriptionTracker() {
        if (_subscriptionTracker!=null) return _subscriptionTracker;
        _subscriptionTracker = new SubscriptionTracker(getSubscriptionContext());
    }
    
    public synchronized ExecutionContext getExecutionContext() {
        return getManagementSupport().getExecutionContext();
    }

    /** Default String representation is simplified name of class, together with selected fields. */
    @Override
    public String toString() {
        Collection<String> fields = toStringFieldsToInclude();
        if (fields.equals(ImmutableSet.of('id', 'displayName'))) {
            return toStringHelper().toString();
        } else {
            StringBuilder result = []
            result << getClass().getSimpleName()
            if (!result) result << getClass().getName()
            result << "[" << fields.collect({
                def v = this.hasProperty(it) ? this[it] : null  /* TODO would like to use attributes, config: this.properties[it] */
                v ? "$it=$v" : null
            }).findAll({it!=null}).join(",") << "]"
        }
    }

    /** 
     * Override this to add to the toString(), e.g. <code>return super.toStringHelper().add("port", port);</code>
     * 
     * Cannot be used in combination with overriding the deprecated toStringFieldsToInclude.
     */
    protected ToStringHelper toStringHelper() {
        return Objects.toStringHelper(this).omitNullValues().add("id", getId()).add("name", getDisplayName());
    }
    
    /** 
     * override this, adding to the collection, to supply fields whose value, if not null, should be included in the toString
     * 
     * @deprecated In 0.5.0, instead override toStringHelper instead
     */
    @Deprecated
    public Collection<String> toStringFieldsToInclude() { 
		return ['id', 'displayName'] as Set
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
        
        getManagementSupport().getEntityChangeListener().onPoliciesChanged();
    }

    @Override
    boolean removePolicy(AbstractPolicy policy) {
        policy.destroy()
        boolean changed = policies.remove(policy)
        
        if (changed) {
            getManagementSupport().getEntityChangeListener().onPoliciesChanged();
        }
        return changed;
    }
    
    @Override
    boolean removeAllPolicies() {
        boolean changed = false;
        for (Policy policy : policies) {
            removePolicy(policy);
            changed = true;
        }
        
        if (changed) {
            getManagementSupport().getEntityChangeListener().onPoliciesChanged();
        }
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

    @Override
    boolean removeAllEnrichers() {
        for (AbstractEnricher enricher : enrichers) {
            removeEnricher(enricher);
        }
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
        if (LOG.isDebugEnabled()) LOG.debug "Emitting sensor notification {} value {} on {}", sensor.name, val, this
        emitInternal(sensor, val);
    }
    
    @Override
    public <T> void emitInternal(Sensor<T> sensor, T val) {
        subscriptionContext?.publish(sensor.newEvent(this, val))
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
            if (args==null) 
                LOG.warn("$this.$name invoked with incorrect args signature (null)", new Throwable("source of incorrect invocation of $this.$name"))
            else if (!args.class.isArray()) 
                LOG.warn("$this.$name invoked with incorrect args signature (non-array ${args.class}): "+args, new Throwable("source of incorrect invocation of $this.$name"))

            try {
                Effector eff = entityType.getEffector(name);
                if (eff) {
                    if (LOG.isDebugEnabled()) LOG.debug("Invoking effector {} on {} with args {}", name, this, args)
                    EntityManagementSupport mgmt = getManagementSupport();
                    if (!mgmt.isDeployed()) {
                        mgmt.attemptLegacyAutodeployment(name);
                    }
                    AbstractManagementContext mgctx = mgmt.getManagementContext(false);
                    if (mgctx==null) {
                        throw new ExecutionException("Execution of effector "+name+" on entity "+id+" not permitted: not in managed state");
                    }
                    
                    getManagementSupport().getEntityChangeListener().onEffectorStarting(eff);
                    try {
                        return mgctx.invokeEffectorMethodSync(this, eff, args);
                    } finally {
                        getManagementSupport().getEntityChangeListener().onEffectorCompleted(eff);
                    }
                }
            } catch (CancellationException ce) {
	            LOG.info "Execution of effector {} on entity {} was cancelled", name, id
                throw ce;
            } catch (ExecutionException ee) {
                LOG.info "Execution of effector {} on entity {} failed with {}", name, id, ee
                // Exceptions thrown in Futures are wrapped
                if (ee.getCause()) throw ee.getCause();
                else throw ee;
            } finally { this.@skipInvokeMethodEffectorInterception.set(false); }
        }
        if (metaClass==null) 
            throw new IllegalStateException("no meta class for "+this+", invoking "+name); 
        metaClass.invokeMethod(this, name, args);
    }

    /** Convenience for finding named effector in {@link #getEffectors()} {@link Map}. */
    public <T> Effector<T> getEffector(String effectorName) {
        return entityType.getEffector(effectorName);
    }

    /** Invoke an {@link Effector} directly. */
    public <T> Task<T> invoke(Map parameters=[:], Effector<T> eff) {
        invoke(eff, parameters);
    }

    /**
     * TODO Calling the above groovy method from java gives compilation error due to use of generics
     * This method will be removed once that is resolved in groovy (or when this is converted to pure java).
     */
    public Task<?> invokeFromJava(Map parameters=[:], Effector eff) {
        invoke(eff, parameters);
    }

    /**
     * Additional form supplied for when the parameter map needs to be made explicit.
     *
     * @see #invoke(Effector)
     */
    public <T> Task<T> invoke(Effector<T> eff, Map<String,?> parameters) {
        ManagementContext mgmtCtx = getManagementContext();
        if (mgmtCtx==null) 
            throw new IllegalStateException("Cannot invoke "+eff+" on "+this+" when not managed"); 

        // FIXME Want a listenable task (like Guava's ListenableFuture)
        //       This call is non-blocking, so we're not notifying of onEffectorCompleted at correct time
        getManagementSupport().getEntityChangeListener().onEffectorStarting(eff);
        try {
            Task<T> result = mgmtCtx.invokeEffector(this, eff, parameters);
            return result;
        } finally {
            getManagementSupport().getEntityChangeListener().onEffectorCompleted(eff);
        }
    }

    /**
     * Invoked by {@link EntityManagementSupport} when this entity is becoming managed (i.e. it has a working
     * management context, but before the entity is visible to other entities).
     */
    public void onManagementStarting() {}
    
    /**
     * Invoked by {@link EntityManagementSupport} when this entity is fully managed and visible to other entities 
     * through the management context.
     */
    public void onManagementStarted() {}
    
    /**
     * Invoked by {@link ManagementContext} when this entity becomes managed at a particular management node,
     * including the initial management started and subsequent management node master-change for this entity.
     * @deprecated since 0.4.0 override EntityManagementSupport.onManagementStarting if customization needed
     */
    public void onManagementBecomingMaster() {}
    
    /**
     * Invoked by {@link ManagementContext} when this entity becomes mastered at a particular management node,
     * including the final management end and subsequent management node master-change for this entity.
     * @deprecated since 0.4.0 override EntityManagementSupport.onManagementStopping if customization needed
     */
    public void onManagementNoLongerMaster() {}

    /** Field for use only by management plane, to record remote destination when proxied. */
    public Object managementData = null;

    /** For use by management plane, to invalidate all fields (e.g. when an entity is changing to being proxied) */
    public void invalidateReferences() {
        // TODO move this to EntityMangementSupport,
        // when hierarchy fields can also be moved there
        this.@owner?.invalidate();
        this.@application?.invalidate();
        this.@ownedChildren.invalidate();
        this.@groups.invalidate();
    }
    
    public final synchronized EntityManagementSupport getManagementSupport() {
        if (managementSupport) return managementSupport;
        managementSupport = createManagementSupport();
        return managementSupport;
    }
    protected EntityManagementSupport createManagementSupport() {
        return new EntityManagementSupport(this);
    }

    @Override
    public RebindSupport<EntityMemento> getRebindSupport() {
        return new BasicEntityRebindSupport(this);
    }

    protected void finalize() throws Throwable {
        super.finalize();
        if (!getManagementSupport().wasDeployed())
            LOG.warn("Entity "+this+" was never deployed -- explicit call to manage(Entity) required."); 
    }
}
