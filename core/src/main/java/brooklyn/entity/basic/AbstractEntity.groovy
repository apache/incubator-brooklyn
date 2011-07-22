package brooklyn.entity.basic

import java.lang.reflect.Field
import java.util.concurrent.Callable
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Application
import brooklyn.entity.ConfigKey
import brooklyn.entity.Effector
import brooklyn.entity.Entity
import brooklyn.entity.EntityClass
import brooklyn.entity.Group
import brooklyn.event.AttributeSensor
import brooklyn.event.SensorEventListener
import brooklyn.event.Sensor
import brooklyn.event.basic.AttributeMap
import brooklyn.location.Location
import brooklyn.management.ExecutionContext
import brooklyn.management.ManagementContext
import brooklyn.management.SubscriptionContext
import brooklyn.management.SubscriptionHandle
import brooklyn.management.Task
import brooklyn.policy.Policy
import brooklyn.util.internal.LanguageUtils
import brooklyn.util.task.BasicExecutionContext
import brooklyn.util.task.BasicTask
import brooklyn.util.task.ParallelTask

/**
 * Default {@link Entity} implementation.
 *
 * FIXME rewrite documentation below
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
abstract class AbstractEntity implements EntityLocal, GroovyInterceptable {
    private static final Logger LOG = LoggerFactory.getLogger(AbstractEntity.class)

    String id = LanguageUtils.newUid()
    String name
    String displayName
    EntityReference owner
 
    protected Map properties = [:]
    protected volatile EntityReference<Application> application
 
    // XXX see comments below (grkvlt)
//    private final Object ownedChildrenLock = new Object();
 
    final EntityCollectionReference ownedChildren = new EntityCollectionReference<Entity>(this);
    final EntityCollectionReference<Group> groups = new EntityCollectionReference<Group>(this);

    Map<String,Object> presentationAttributes = [:]
    Collection<Policy> policies = [] as CopyOnWriteArrayList
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

    public AbstractEntity(Entity owner) {
        this([:], owner)
    }

    public AbstractEntity(Map flags=[:], Entity owner=null) {
        this.@skipInvokeMethodEffectorInterception.set(true)
        try {
            if (flags.owner != null && owner != null && flags.owner != owner) {
                throw new IllegalArgumentException("Multiple owners supplied, ${flags.owner} and $owner")
            }
            Entity suppliedOwner = flags.remove('owner') ?: owner
            Map<ConfigKey,Object> suppliedOwnConfig = flags.remove('config')

            if (suppliedOwnConfig) ownConfig.putAll(suppliedOwnConfig)
        
            name = flags.remove('name') ?: (getClass().getSimpleName() + ":" + id)
            displayName = flags.remove('displayName') ?: name

            // initialize the effectors defined on the class
            // (dynamic effectors could still be added; see #getEffectors
            Map<String,Effector> effectorsT = [:]
            for (Field f in getClass().getFields()) {
                if (Effector.class.isAssignableFrom(f.getType())) {
                    Effector eff = f.get(this)
                    def overwritten = effectorsT.put(eff.name, eff)
                    if (overwritten!=null) LOG.warn("multiple definitions for effector ${eff.name} on $this; preferring $eff to $overwritten")
                }
            }
            LOG.trace "Entity {} effectors: {}", id, effectorsT.keySet().join(", ")
            effectors = effectorsT
    
            Map<String,Sensor> sensorsT = [:]
            for (Field f in getClass().getFields()) {
                if (Sensor.class.isAssignableFrom(f.getType())) {
                    Sensor sens = f.get(this)
                    def overwritten = sensorsT.put(sens.name, sens)
                    if (overwritten!=null) LOG.warn("multiple definitions for sensor ${sens.name} on $this; preferring $sens to $overwritten")
                }
            }
            LOG.trace "Entity {} sensors: {}", id, sensorsT.keySet().join(", ")
            sensors = sensorsT

            properties = flags

            //set the owner if supplied; accept as argument or field
            if (suppliedOwner) suppliedOwner.addOwnedChild(this)
        } finally { this.@skipInvokeMethodEffectorInterception.set(false) }
    }

    /**
     * Adds this as a member of the given group, registers with application if necessary
     */
    public synchronized void setOwner(Entity entity) {
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
        if (isDescendant(entity)) throw new IllegalStateException("loop detected trying to set owner of $this as $entity, which is already a descendent")
        
        owner = new EntityReference(this, entity)
        //used to test entity!=null but that should be guaranteed?
        entity.addOwnedChild(this)
        inheritedConfig.putAll(entity.getAllConfig())
        previouslyOwned = true
        
        getApplication()
    }

    public synchronized void clearOwner() {
        if (owner == null) return
        Entity oldOwner = owner
        owner = null
        oldOwner.removeOwnedChild(this)
    }

    public boolean isAncestor(Entity oldee) {
        AbstractEntity ancestor = getOwner()
        while (ancestor) {
            if (ancestor.equals(oldee)) return true
            ancestor = ancestor.getOwner()
        }
        return false
    }

    public boolean isDescendant(Entity youngster) {
        Set<Entity> inspected = [] as HashSet
        List<Entity> toinspect = [this]
        
        while (!toinspect.isEmpty()) {
            Entity e = toinspect.pop()
            if (e.getOwnedChildren().contains(youngster)) {
                return true
            }
            inspected.add(e)
            toinspect.addAll(e.getOwnedChildren())
            toinspect.removeAll(inspected)
        }
        
        return false
    }

    /**
     * Access the set of owned children in a thread-safe manner. The supplied closure is passed a reference to the
     * @{link Set} of owned children, and is run while an exclusive lock is held. The operation is synchronous, i.e., this
     * method will block until the mutex can be obtained and the closure has finished executing.
     *
     * Example:
     * <code>
     *     // This code queries the set of children, and then removes one of its entries. Between the two operations there is
     *     // scope for a race condition, so it must be run while holding an exclusive lock on the set of children.
     *     entity.accessOwnedChildrenSynchronized({ Set<Entity> children ->
     *         Entity child = children.iterator().next()
     *         entity.removeOwnedChild(child)
     *     })
     * </code>
     * @param closure a block of code to run while holding the exclusive lock.
     * @deprecated see comments below
     */
    // XXX the group methods addMember and removeMember are now synchronized on the group itself, and the resize effector
    //      implementation for dymanic cluster is also synchronized on the owning group. the members collection returned
    //      is immutable, so group membership changes should be intrinsicly thread safe, meaning this is not required.
    //      Note that set and clear of owner are also synchronized.
    // FIXME If this method is present the Web Console build barfs with error:
    // Compilation error: BUG! exception in phase 'semantic analysis' in source unit '<https://ccweb.cloudsoftcorp.com/jenkins/job/Brooklyn/ws/web-console/grails-app/services/brooklyn/web/console/ManagementContextService.groovy'> null
//    protected <T> T accessOwnedChildrenSynchronized(Closure<T> closure) {
//        synchronized(ownedChildrenLock) {
//            return closure.call(ownedChildren)
//        }
//    }

    /**
     * Adds the given entity as a member of this group <em>and</em> this group as one of the groups of the child;
     * returns argument passed in, for convenience.
     */
    @Override
    public  synchronized Entity addOwnedChild(Entity child) {
        if (isAncestor(child)) throw new IllegalStateException("loop detected trying to add child $child to $this; it is already an ancestor")
        child.setOwner(this)
        ownedChildren.add(child)
        child
    }

    @Override
    public synchronized boolean removeOwnedChild(Entity child) {
        ownedChildren.remove child
        child.clearOwner()
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
    public ManagementContext getManagementContext() {
        ManagementContext m = managementContext
        if (m) return m
        managementContext = getApplication()?.getManagementContext()
    }


    @Override
    public synchronized EntityClass getEntityClass() {
        if (entityClass) entityClass
        entityClass = new BasicEntityClass(this.class.canonicalName, sensors.values(), effectors.values()) 
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
        LOG.trace "setting attribute {} to {}", attribute.name, val
        attributesInternal.update(attribute, val);
    }

    @Override
    public <T> T getConfig(ConfigKey<T> key) {
        // FIXME What about inherited task in config?!
        Object v = ownConfig.get(key);
        v = v ?: inheritedConfig.get(key)

        //if config is set as a task, we wait for the task to complete
        if (v in Task) {
            if ( !((Task)v).isSubmitted() ) {
                def exec = getExecutionContext()
//                if (exec==null || !getApplication().isDeployed())
//                    throw new IllegalStateException("Not permitted to access deferred config until application is deployed");
                exec.submit((Task)v)
            }
            v = v.get()
        }
        
        v
    }

    @Override
    public <T> T setConfig(ConfigKey<T> key, T val) {
        // TODO Is this the best idea, for making life easier for brooklyn coders when supporting changing config?
        if (getApplication()?.isDeployed()) throw new IllegalStateException("Cannot set configuration $key on active entity $this")

        T oldVal = ownConfig.put(key, val);        
        ownedChildren.get().each {
            it.refreshInheritedConfig()
        }

        oldVal
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
    public <T> SubscriptionHandle subscribe(Entity producer, Sensor<T> sensor, SensorEventListener<T> listener) {
        subscriptionContext.subscribe(producer, sensor, listener)
    }

    /** @see Entity#subscribeToChildren(Entity, Sensor, EventListener) */
    public <T> SubscriptionHandle subscribeToChildren(Entity parent, Sensor<T> sensor, SensorEventListener<T> listener) {
        subscriptionContext.subscribeToChildren(parent, sensor, listener)
    }

    protected synchronized SubscriptionContext getSubscriptionContext() {
        if (subscription) subscription
        subscription = getManagementContext()?.getSubscriptionContext(this);
    }

    protected synchronized ExecutionContext getExecutionContext() {
        if (execution) execution;
        execution = new BasicExecutionContext(tag:this, getManagementContext().executionManager)
    }

    /** default toString is simplified name of class, together with selected arguments */
    @Override
    public String toString() {
        StringBuffer result = []
        result << getClass().getSimpleName()
        if (!result) result << getClass().getName()
        result << "[" << toStringFieldsToInclude().collect({
            def v = this.hasProperty(it) ? this[it] : null  /* TODO would like to use attributes, config: this.properties[it] */
            v ? "$it=$v" : null
        }).findAll({it!=null}).join(",") << "]"
    }

    /** override this, adding to the collection, to supply fields whose value, if not null, should be included in the toString */
    public Collection<String> toStringFieldsToInclude() { ['id', 'name', 'displayName'] }

    
    // -------- POLICIES --------------------

    @Override
    public Collection<Policy> getPolicies() {
        return policies.asImmutable()
    }

    @Override
    public void addPolicy(Policy policy) {
        policies.add(policy)
        policy.setEntity(this)
    }

    @Override
    boolean removePolicy(Policy policy) {
        return policies.remove(policy)
    }

    // -------- SENSORS --------------------

    @Override
    public <T> void emit(Sensor<T> sensor, T val) {
        if (sensor instanceof AttributeSensor) {
            LOG.warn("Strongly discouraged use of emit with attribute sensor $sensor $val; use setAttribute instead!", 
                new Throwable("location of discouraged $sensor emit"))
        }
        emitInternal(sensor, val);
    }
    
    @Override
    public <T> void emitInternal(Sensor<T> sensor, T val) {
        subscriptionContext?.publish(sensor.newEvent(this, val))
    }

    
    /**
     * Sensors available on this entit5
     */
    public Map<String,Sensor<?>> getSensors() { sensors }

    /** convenience for finding named sensor in {@link #getSensor()} map */
    public <T> Sensor<T> getSensor(String sensorName) { getSensors()[sensorName] }

    /**
     * Add the given {@link Sensor} to this entity.
     */
    public void addSensor(Sensor<?> sensor) { sensors.put(sensor.name, sensor) }

    /**
     * Remove the named {@link Sensor} from this entity.
     */
    public void removeSensor(String sensorName) { sensors.remove(sensorName) }

    // -------- EFFECTORS --------------

    /** flag needed internally to prevent invokeMethod from recursing on itself */     
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
    public Map<String,Effector> getEffectors() { effectors }

    /** convenience for finding named effector in {@link #getEffectors()} map */
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
    public <T> Task<T> invoke(Effector<T> eff, Map parameters) {
        getManagementContext().invokeEffector(this, eff, parameters);
//        executionContext.submit(
//                { effector.call(this, parameters) } as Callable<T>,
//                description:"invocation of effector ${effector}")
    }

    public <T> Task<List<T>> invokeEffectorList(Collection<Entity> entities, Effector<T> effector, Map<String,?> parameters) {
        if (!entities || entities.isEmpty()) return null
        List<Task> tasks = entities.collect { it.invoke(effector, parameters) }
        ParallelTask invoke = new ParallelTask(tasks)
        executionContext.submit(invoke)
        invoke
    }

    /** invoked by management context when this entity becomes managed by a given management context (e.g. at a particular management node);
     * including the initial management started and subsequent management node master-change for this entity */
    public void onManagementBecomingMaster() {}
    
    /** invoked by management context when this entity becomes mastered by a given management context (e.g. at a particular management node),
     * including the final management end and subsequent management node master-change for this entity */
    public void onManagementNoLongerMaster() {}

    /** field for use only by management plane, to record remote destination when proxied */
    public Object managementData = null;

    /** for use by management plane, to invalidate all fields (e.g. when an entity is changing to being proxied) */
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

    public Collection<T> get() {
        Collection<T> result = entities;
        if (result==null) {
            result = find();
        }
        return Collections.unmodifiableCollection(result);
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
