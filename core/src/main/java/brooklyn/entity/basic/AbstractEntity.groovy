package brooklyn.entity.basic

import java.lang.reflect.Field
import java.util.Collection
import java.util.Map
import java.util.concurrent.CopyOnWriteArrayList

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Application
import brooklyn.entity.Effector
import brooklyn.entity.Entity
import brooklyn.entity.EntityClass
import brooklyn.entity.Group
import brooklyn.entity.ParameterType
import brooklyn.event.AttributeSensor
import brooklyn.event.EventListener
import brooklyn.event.Sensor
import brooklyn.event.adapter.PropertiesSensorAdapter
import brooklyn.event.basic.AttributeMap
import brooklyn.event.basic.ConfigKey
import brooklyn.location.Location
import brooklyn.management.ManagementContext
import brooklyn.management.SubscriptionContext
import brooklyn.management.Task
import brooklyn.management.internal.LocalManagementContext
import brooklyn.management.internal.LocalSubscriptionContext
import brooklyn.util.internal.LanguageUtils
import brooklyn.util.task.ExecutionContext

/**
 * Default {@link Entity} implementation
 * 
 * Provides several common fields ({@link #name}, {@link #id});
 * a map {@link #config} which contains arbitrary config data;
 * sensors and effectors; policies; managementContext.
 * <p>
 * Fields in config can be accessed (get and set) without referring to config,
 * (through use of propertyMissing). Note that config is typically inherited
 * by children, whereas the fields are not. (Attributes cannot be so accessed,
 * nor are they inherited.)
 *
 * @author alex, aled
 */
public abstract class AbstractEntity implements EntityLocal, GroovyInterceptable {
    private static final Logger log = LoggerFactory.getLogger(AbstractEntity.class);
 
    String id = LanguageUtils.newUid();
    Map<String,Object> presentationAttributes = [:]
    String displayName;
    final Collection<Group> groups = new CopyOnWriteArrayList<Group>()
    volatile Application application
    Collection<Location> locations = []
    Group owner
 
    protected transient volatile ExecutionContext execution
    protected transient volatile SubscriptionContext subscription
    protected transient volatile LocalManagementContext management = LocalManagementContext.getContext()
 
    /**
     * The sensor-attribute values of this entity. Updating this map should be done
     * via getAttribute/updateAttribute; it will automatically emit an attribute-change event.
     */
    protected final AttributeMap attributesInternal = new AttributeMap(this)
	
	//ENGR-1458  interesting to use property change. if it works great. 
	//if there are any issues with it consider instead just making attributesInternal private,
	//and forcing all changes to attributesInternal to go through update(AttributeSensor,...)
	//and do the publishing there...  (please leave this comment here for several months until we know... it's Jun 2011 right now)
    protected final PropertiesSensorAdapter propertiesAdapter = new PropertiesSensorAdapter(this, attributes)

    /*
     * TODO An alternative implementation approach would be to have:
     *   setOwner(Entity o, Map<ConfigKey,Object> inheritedConfig=[:])
     * The idea is that the owner could in theory decide explicitly what in its config
     * would be shared.
     * I (Aled) am undecided as to whether that would be better...
     */
    /**
     * Map of configuration information that is defined at start-up time for the entity. These
     * configuration parameters are shared and made accessible to the "owned children" of this
     * entity.
     */
    protected final Map<ConfigKey,Object> inheritableConfig = [:]
    
    public AbstractEntity(Map flags=[:]) {
		this.@skipCustomInvokeMethod.set(true)
        Entity suppliedOwner = flags.remove('owner')
        Map<ConfigKey,Object> suppliedInheritableConfig = flags.remove('inheritableConfig')

        if (suppliedInheritableConfig) inheritableConfig.putAll(suppliedInheritableConfig)
        
        //place named-arguments into corresponding fields if they exist, otherwise put into attributes map
        this.attributes << LanguageUtils.setFieldsFromMap(this, flags)

        // initialize the effectors defined on the class
		// (dynamic effectors could still be added; see #getEffectors
		Map<String,Effector> effectorsT = [:]
		for (Field f in getClass().getFields()) {
			if (Effector.class.isAssignableFrom(f.getType())) {
				Effector eff = f.get(this)
				def overwritten = effectorsT.put(eff.name, eff)
				if (overwritten!=null) log.warn("multiple definitions for effector ${eff.name} on $this; preferring $eff to $overwritten")
			}
		}
		effectors = effectorsT

        //set the owner if supplied; accept as argument or field
        if (suppliedOwner) suppliedOwner.addOwnedChild(this)
        this.@skipCustomInvokeMethod.set(false)
    }

    public void propertyMissing(String name, value) { attributes[name] = value }
 
    public Object propertyMissing(String name) {
        if (attributes.containsKey(name)) return attributes[name];
        else {
            //TODO could be more efficient ;)
            def v = owner?.attributes[name]
            if (v != null) return v;
            if (groups.find { group -> v = group.attributes[name] }) return v;
        }
        log.debug "no property or attribute $name on $this"
		if (name=="activity") log.warn "reference to removed field 'activity' on entity $this", new Throwable("location of failed reference to 'activity' on $this")
    }
	
    /**
     * Adds this as a member of the given group, registers with application if necessary
     */
    public void setOwner(Group e) {
        owner = e
        owner.inheritableConfig?.entrySet().each { Map.Entry<ConfigKey,Object> entry ->
            if (!inheritableConfig.containsKey(entry.getKey())) {
                inheritableConfig.put(entry.getKey(), entry.getValue())
            }
        }
        getApplication()
    }

    /**
     * Adds this as a member of the given group, registers with application if necessary
     */
    public void addGroup(Group e) {
        groups.add e
        getApplication()
    }
 
	public Collection<String> getGroupIds() {
        groups.collect { g -> g.id }
	}
	
	public Group getOwner() { owner }

	public Collection<Group> getGroups() { groups }

    /**
     * Returns the application, looking it up if not yet known (registering if necessary)
     */
    public Application getApplication() {
        if (application!=null) return application;
        def app = owner?.getApplication()
        if (app) {
            registerWithApplication(app)
            application
        }
        app
    }

	public String getApplicationId() {
		getApplication()?.id
	}

	public ManagementContext getManagementContext() {
		getApplication()?.getManagementContext()
	}
	
    protected synchronized void registerWithApplication(Application app) {
        if (application) return;
        this.application = app
        app.registerEntity(this)
    }

    public EntityClass getEntityClass() {
		//TODO registry? or a transient?
		new BasicEntityClass(getClass())
    }

    /**
     * Should be invoked at end-of-life to clean up the item.
     */
    public void destroy() {
		//FIXME this doesn't exist, but we need some way of deleting stale items
        removeApplicationRegistrant()
    }

    /**
     * Mutable attributes on this entity.
     *
     * This can include activity information and status information (e.g. AttributeSensors), as well as
     * arbitrary internal properties which can make life much easier/dynamic (though we lose something in type safety)
     * e.g. jmxHost / jmxPort are handled as attributes.
     * 
     * @deprecated this will not be exposed, final API TBD
     */
    @Deprecated
    public Map<String, Object> getAttributes() {
        return attributesInternal.asMap(); // .asImmutable(); // FIXME this does not make the children immutable
    }
    
	public <T> T getAttribute(AttributeSensor<T> attribute) { attributesInternal.getValue(attribute); }
 
    public <T> T updateAttribute(AttributeSensor<T> attribute, T val) {
        log.info "updating attribute {} as {}", attribute.name, val
        attributesInternal.update(attribute, val);
    }

    @Override
    public <T> T getConfig(ConfigKey<T> key) {
        return inheritableConfig.get(key);
    }

    @Override
    public <T> T setConfig(ConfigKey<T> key, T val) {
        return inheritableConfig.put(key, val);
    }

    /** @see Entity#subscribe(Entity, Sensor, EventListener) */
    public <T> long subscribe(Entity producer, Sensor<T> sensor, EventListener<T> listener) {
        subscriptionContext.getSubscriptionManager().subscribe this.id, producer.id, sensor.name, listener
    }

    protected synchronized SubscriptionContext getSubscriptionContext() {
		if (subscription) subscription;
        subscription = subscription ?: new LocalSubscriptionContext() // XXX doesn't work ?
    }

	protected synchronized ExecutionContext getExecutionContext() {
		if (execution) execution;
		synchronized (this) {
			if (execution) execution;
			execution = new ExecutionContext(tag: this, getManagementContext().getExecutionManager())
		}
	}
    
    public <T> Sensor<T> getSensor(String sensorName) {
        getEntityClass().getSensors() find { s -> s.name.equals(sensorName) }
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
    public Collection<String> toStringFieldsToInclude() { ['id', 'displayName'] }

    /** @see EntityLocal#emit(Sensor, Object) */
    public <T> void emit(Sensor<T> sensor, T val) {
        subscriptionContext.subscriptionManager.publish(sensor.newEvent(this, val))
    }
    
	// -------- EFFECTORS --------------

	/** flag needed internally to prevent invokeMethod from recursing on itself */ 	
	private ThreadLocal<Boolean> skipCustomInvokeMethod = new ThreadLocal() { protected Object initialValue() { Boolean.FALSE } }

	public Object invokeMethod(String name, Object args) {
		if (!this.@skipCustomInvokeMethod.get()) {
			this.@skipCustomInvokeMethod.set(true);
			
			//args should be an array, warn if we got here wrongly (extra defensive as args accepts it, but it shouldn't happen here)
			if (args==null) log.warn("$this.$name invoked with incorrect args signature (null)", new Throwable("source of incorrect invocation of $this.$name"))
			else if (!args.getClass().isArray()) log.warn("$this.$name invoked with incorrect args signature (non-array ${args.getClass()}): "+args, new Throwable("source of incorrect invocation of $this.$name"))
			
			try {
				Effector eff = getEffectors().get(name)
				if (eff) {
					args = AbstractEffector.prepareArgsForEffector(eff, args);
					Task currentTask = ExecutionContext.getCurrentTask();
					if (!currentTask || !currentTask.getTags().contains(this)) {
						//wrap in a task if we aren't already in a task that is tagged with this entity
						MetaClass mc = metaClass
						Task t = executionContext.submit( { mc.invokeMethod(this, name, args); },
							description: "call to method $name being treated as call to effector $eff" )
						return t.get();
					}
				}
			} finally { this.@skipCustomInvokeMethod.set(false); }
		}
		metaClass.invokeMethod(this, name, args);
		//following is recommended on web site, but above is how groovy actually implements it
//			def metaMethod = metaClass.getMetaMethod(name, newArgs)
//			if (metaMethod==null)
//				throw new IllegalArgumentException("Invalid arguments (no method found) for method $name: "+newArgs);
//			metaMethod.invoke(this, newArgs)
	}
	
	private Map<String,Effector> effectors = null
	/** effectors available on this entity; no work has been done supporting changing this after initialization,
	 * but the idea of these so-called "dynamic effectors" has been discussed and it might be supported in future...
	 */
	public Map<String,Effector> getEffectors() { effectors }
	
	public <T> Task<T> invoke(Map parameters=[:], Effector<T> eff) {
		invoke(eff, parameters);
	}
 
	//add'l form supplied for when map needs to be made explicit (above supports implicit named args)
	public <T> Task<T> invoke(Effector<T> eff, Map parameters) {
		executionContext.submit( { eff.call(this, parameters) }, description: "invocation of effector $eff" )
	}
}
