package brooklyn.entity.basic

import java.util.Map;

import java.util.Collection
import java.util.EventListener
import java.util.Map
import java.util.concurrent.CopyOnWriteArrayList

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Application
import brooklyn.entity.Entity
import brooklyn.entity.EntityClass
import brooklyn.entity.EntitySummary
import brooklyn.entity.Group
import brooklyn.event.AttributeSensor
import brooklyn.event.basic.Activity
import brooklyn.event.basic.EventFilter
import brooklyn.event.basic.SensorEvent
import brooklyn.location.Location

import com.google.common.base.Predicate
import brooklyn.util.internal.LanguageUtils

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
    
    //FIXME Delete?
    //final ObservableList listeners = new SerializableObservableList(new CopyOnWriteArrayList<EventListener>());

    /**
     * Properties can be accessed or set on the entity itself; can also be accessed
     * from ancestors if not present on an entity
     */
    final Map properties = [:]
    
    Collection<Location> locations = []
 
    public final Activity activity = new Activity(this)

    public void propertyMissing(String name, value) { properties[name] = value }
 
    public Object propertyMissing(String name) {
        if (properties.containsKey(name)) return properties[name];
        else {
            //TODO could be more efficient ;)
            def v = null
            if (parents.find { parent -> v = parent.properties[name] }) return v;
        }
        log.debug "no property $name on $this"
    }

    /** Entity hierarchy */
    final Collection<Group> parents = new CopyOnWriteArrayList<Group>();
 
    Application application

    /**
     * Adds a parent, registers with application if necessary
     */
    public void addParent(Group e) {
        parents.add e
        getApplication()
    }

    /**
     * Returns the application, looking it up if not yet known (registering if necessary
     */
    public Application getApplication() {
        if (application!=null) return application;
        def app = parents.find({ it.getApplication() })?.getApplication()
        if (app) {
            registerWithApplication(app)
            application
        }
        app
    }

    protected synchronized void registerWithApplication(Application app) {
        if (application) return;
        this.application = app
        app.registerEntity(this)
    }

    public EntitySummary getSummary() {
        Collection<String> groups = []
        getParents().each { groups.add it.getId() }
        return new BasicEntitySummary(id, displayName, getApplication().getId(), groups);
    }
    
    public EntityClass getEntityClass() {
        // FIXME `new EntityClass(this.getClass())`; but have a registry so re-use types?
        return null;
    }
    
    /**
     * Should be invoked at end-of-life to clean up the item.
     */
    public void destroy() {
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
    public Collection<String> toStringFieldsToInclude() { ['id', 'displayName']}


    public AbstractEntity(Map properties=[:], Group parent=null) {
        def parentFromProps = properties.remove('parent')
        if (parentFromProps) {
            if (!parent) parent = parentFromProps;
            else assert parent==parentFromProps;
        }

        addProperties(properties)

        //set the parent if supplied; accept as argument or field
        if (parent) parent.addChild(this)
    }

    public void addProperties(Map properties) {
        //place named-arguments into corresponding fields if they exist, otherwise put into config map
        this.properties << LanguageUtils.setFieldsFromMap(this, properties)
    }

    Map<String,Object> getAttributes() {
        return activity.asMap();
    }
    
    public <T> void updateAttribute(AttributeSensor<T> attribute, T val) {
        activity.update(attribute, val);
    }
    
    /** TODO */
    void subscribe(EventFilter filter, EventListener listener) { }
    
    /** TODO */
    void subscribe(Predicate<Entity> entities, EventFilter filter, EventListener listener) { }

    /** TODO */
    void raiseEvent(SensorEvent<?> event) { }
}
