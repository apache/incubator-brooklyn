package brooklyn.entity.basic

import java.util.Collection;
import java.util.concurrent.CopyOnWriteArrayList

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Application
import brooklyn.entity.Entity
import brooklyn.entity.EntityClass
import brooklyn.entity.Group
import brooklyn.event.Event
import brooklyn.event.EventListener
import brooklyn.event.Sensor
import brooklyn.event.basic.AttributeMap
import brooklyn.location.Location
import brooklyn.management.ManagementContext
import brooklyn.util.internal.LanguageUtils
import brooklyn.util.task.ExecutionContext

/**
 * Default {@link Entity} definition.
 * 
 * Provides several common fields ({@link #name}, {@link #id});
 * also provides a map {@link #config} which contains arbitrary fields.
 * <p>
 * Fields in config can be accessed (get and set) without referring to config,
 * (through use of propertyMissing). Note that config is typically inherited
 * by children, whereas the fields are not.
 *
 * @author alex
 */
public abstract class AbstractEntity implements Entity {
    static final Logger log = LoggerFactory.getLogger(Entity.class);
 
    String id = LanguageUtils.newUid();
    Map<String,Object> presentationAttributes = [:]
    
    String displayName;
    
    /**
     * Properties can be accessed or set on the entity itself; can also be accessed
     * from ancestors if not present on an entity
     */
    Collection<Location> locations = []
 
    // TODO ref to local mgmt context and sub mgr etc
 
    protected final AttributeMap attributesInternal = new AttributeMap(this)

    /** Entity hierarchy */
    final Collection<Group> groups = new CopyOnWriteArrayList<Group>()
 
    Group owner
    
    Application application

    public AbstractEntity(Map flags=[:]) {
        def owner = flags.remove('owner')

        //place named-arguments into corresponding fields if they exist, otherwise put into attributes map
        this.attributes << LanguageUtils.setFieldsFromMap(this, flags)

        //set the owner if supplied; accept as argument or field
        if (owner) owner.addOwnedChild(this)
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
        getApplication()
    }

    /**
     * Adds this as a member of the given group, registers with application if necessary
     */
    public void addGroup(Group e) {
        groups.add e
        getApplication()
    }
	
	@Override
	public Group getOwner() { owner }
	@Override
	public Collection<Group> getGroups() { groups }

    /**
     * Returns the application, looking it up if not yet known (registering if necessary)
     */
    public Application getApplication() {
        if (application!=null) return application;
        def app = owner?.getApplication()
        app = (app != null) ? app : groups.find({ it.getApplication() })?.getApplication()
        if (app) {
            registerWithApplication(app)
            application
        }
        app
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

    /** default toString is simplified name of class, together with selected arguments */
    @Override
    public String toString() {
        StringBuffer result = []
        result << getClass().getSimpleName()
        if (!result) result << getClass().getName()
        //TODO groovy 1.8, use collectEntries
        result << toStringFieldsToInclude().collect({
            def v = this.hasProperty(it) ? this[it] : this.properties[it]
            v ? "$it=$v" : null
        }).findAll { it }
        result
    }
 
    /** override this, adding to the collection, to supply fields whose value, if not null, should be included in the toString */
    public Collection<String> toStringFieldsToInclude() { ['id', 'displayName'] }

    Map<String,Object> getAttributes() {
        return attributesInternal.asMap();
    }
    
	public <T> T getAttribute(Sensor<T> attribute) { attributesInternal.getValue(attribute); }
    public <T> void updateAttribute(Sensor<T> attribute, T val) {
        attributesInternal.update(attribute, val);
    }
    
    // TODO implement private methods
    // private void subscribe(EventFilter filter, EventListener listener) { }
    // private void subscribe(Predicate<Entity> entities, EventFilter filter, EventListener listener) { }
    
    /** @see Entity#subscribe(String, String, EventListener) */
    public <T> void subscribe(String entityId, String sensorname, EventListener<T> listener) {
        // TODO complete
    }
     
    /** @see Entity#raiseEvent(Event) */
    public <T> void raiseEvent(Event<T> event) {
        // TODO complete
    }
	
	private transient volatile ExecutionContext execution;
	protected ExecutionContext getExecutionContext() {
		if (execution) execution;
		synchronized (this) {
			if (execution) execution;
			execution = new ExecutionContext(tag: this, getApplication()?.getManagementContext().getExecutionManager())
		}
	} 
}
