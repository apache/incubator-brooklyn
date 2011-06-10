package org.overpaas.core.types.common

import java.beans.PropertyChangeListener

import org.overpaas.core.decorators.GroupEntity;
import org.overpaas.core.decorators.OverpaasApplication;


public class DynamicGroup extends AbstractGroupEntity {

	Closure entityFilter=null;
	
	public DynamicGroup(Map properties=[:], GroupEntity parent=null, Closure entityFilter=null) {
		super(properties, null)
		if (entityFilter) this.entityFilter = entityFilter;
		//do this last, rather than passing parent up, so that entity filter is ready
		if (parent) parent.addChild(this)
	}
	
	void setEntityFilter(Closure entityFilter) {
		this.entityFilter = entityFilter
		rescanEntities()
	}
	
	protected synchronized void registerWithApplication(OverpaasApplication app) {
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
