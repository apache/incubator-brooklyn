package org.overpaas.entities

import groovy.lang.Closure;

import java.beans.PropertyChangeListener;
import java.util.Map;

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
	
	protected synchronized void registerWithApplication(Application app) {
		super.registerWithApplication(app)
		((ObservableMap)app.entities).addPropertyChangeListener( { rescanEntities() } as PropertyChangeListener );
		rescanEntities()
	}
	
	public void rescanEntities() {
		//TODO extremely inefficient; should act on the event!
		if (!entityFilter) {
			println "not (yet) scanning for children of $this: no filter defined"
			return
		}
		if (!getApplication()) return
		Set childrenSet = getChildren() as HashSet
		println "scanning "+getApplication().getEntities()
		getApplication().getEntities().values().each {
			if (entityFilter.call(it)) {
				if (childrenSet.add(it))
					addChild(it)
			}
		}
	}

}
