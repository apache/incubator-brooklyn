package org.overpaas.core.types.common

import java.util.concurrent.CopyOnWriteArrayList 
import org.overpaas.core.types.GroupEntity 
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.Map
import java.util.concurrent.CopyOnWriteArrayList

import org.overpaas.core.decorators.GroupEntity;
import org.overpaas.core.decorators.OverpaasApplication;
import org.overpaas.core.decorators.OverpaasEntity;
import org.overpaas.core.types.Activity
import org.overpaas.util.LanguageUtils

/**
 * default EntityDefinition.
 * provides several common fields ({@link #name}, {@link #id});
 * also provides a map {@link #config} which contains arbitrary fields.
 * <p>
 * fields in config can be accessed (get and set) without referring to config,
 * (through use of propertyMissing).
 * note that config is typically inherited to children, whereas the fields are not.
 * 
 * @author alex
 *
 */
public abstract class AbstractOverpaasEntity implements OverpaasEntity {

	// --------------- description fields ------------------------------
	
	String id = LanguageUtils.newUid();
	String displayName;
	
	final Map<String,Object> presentationAttributes = [:]
	
	// --------------- properties ------------------------------
	
	/** properties can be accessed or set on the entity itself; can also be accessed from ancestors if not present on an entity */
	Map properties = [:]
	public Map getProperties() { properties }
	public void propertyMissing(String name, value) { properties[name] = value }
	public Object propertyMissing(String name) {
		if (properties.containsKey(name)) return properties[name];
		else {
			//TODO could be more efficient ;)
			def v = null
			if (parents.find { parent -> v = parent.properties[name] }) return v;  
		}
		println "WARNING: no property $name on $this"
//		GroovyObject.this.propertyMissing(name)
	}
	
	// --------------- entity hierarchy, including registering with the application when possible ------------------------------
	
	final private Collection<GroupEntity> parents = new CopyOnWriteArrayList<GroupEntity>();
	OverpaasApplication application
	
	@Override
	public Collection<? extends GroupEntity> getParents() {
		return Collections.unmodifiableCollection(parents);
	}
	/** adds a parent, registers with application if necessary */
	public void addParent(GroupEntity e) {
		parents.add e
		getApplication()
	}

	/** returns the application, looking it up if not yet known (registering if necessary) */
	@Override
	public OverpaasApplication getApplication() {
		if (application!=null) return application;
		def app = parents.find({ it.getApplication() })?.getApplication()
		if (app) {
			registerWithApplication(app)
			application
		}
		app
	}

	protected synchronized void registerWithApplication(OverpaasApplication app) {
		if (this.application) return;
		this.application = app
		app.registerEntity(this)
	}

	/** should be invoked at end-of-life to clean up the item */
	public void destroy() {
		removeApplicationRegistrant()
	}

	// -------------------- pretty-print ----------------------
		
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

	
	// -------------------- entity construction and action/event support ----------------------
	
	public AbstractOverpaasEntity(Map properties=[:], GroupEntity parent=null) {
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

	public final Activity activity = new Activity(this)
	
	@Override
	public Collection<? extends Field> getSensors() {
		// TODO find all fields here (or in delegates?) which are Sensor objects (statics only? statics and fields? include entity properties map?)
		return null;
	}

	@Override
	public Collection<? extends Method> getEffectors() {
		// TODO find all fields here (or in delegates) annotated with @Effector ?
		return null;
	}

}
