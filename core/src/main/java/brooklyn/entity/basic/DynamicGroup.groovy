package brooklyn.entity.basic

import groovy.lang.Closure

import java.util.Map

import brooklyn.entity.Application
import brooklyn.entity.Group

public class DynamicGroup extends AbstractGroup {
    Closure entityFilter=null;
	
	public DynamicGroup(Map properties=[:], Group parent=null, Closure entityFilter=null) {
		super(properties, null)
		if (entityFilter) this.entityFilter = entityFilter;
		//do this last, rather than passing parent up, so that entity filter is ready
		if (parent) parent.addChild(this)
	}
	
	void setEntityFilter(Closure entityFilter) {
		this.entityFilter = entityFilter
		rescanEntities()
	}
	
    @Override
	protected synchronized void registerWithApplication(Application app) {
		super.registerWithApplication(app)
		app.addEntityChangeListener({ rescanEntities() })
		rescanEntities()
	}
	
	public void rescanEntities() {
		//TODO extremely inefficient; should act on the event!
		if (!entityFilter) {
			log.info "not (yet) scanning for children of $this: no filter defined"
			return
		}
		if (!getApplication()) return
		Set childrenSet = getChildren() as HashSet
		log.info "scanning {}", getApplication().getEntities()
		getApplication().getEntities().each {
			if (entityFilter.call(it)) {
				if (childrenSet.add(it))
					addChild(it)
			}
		}
	}
}
