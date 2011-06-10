package org.overpaas.core.types.common

import groovy.transform.InheritConstructors

import org.overpaas.core.decorators.GroupEntity;
import org.overpaas.core.decorators.OverpaasEntity;

//@InheritConstructors
public abstract class AbstractGroupEntity extends AbstractOverpaasEntity implements GroupEntity {

	public AbstractGroupEntity(Map props=[:], GroupEntity parent=null) {
		super(props, parent)
	}

	final Collection<OverpaasEntity> children = Collections.synchronizedCollection(new LinkedHashSet<OverpaasEntity>())
	
	/** adds argument as child of this group *and* this group as parent of the child;
	 * returns argument passed in, for convenience */
	public OverpaasEntity addChild(OverpaasEntity t) {
		t.addParent(this)
		children.add(t)
		t
	}
	public boolean removeChild(OverpaasEntity child) {
		children.remove child		
	}
	
}
