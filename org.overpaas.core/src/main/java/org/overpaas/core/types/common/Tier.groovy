package org.overpaas.core.types.common

import groovy.transform.InheritConstructors

import java.util.Map

import org.overpaas.core.decorators.GroupEntity;
import org.overpaas.core.decorators.OverpaasEntity;
import org.overpaas.core.decorators.Startable;

//@InheritConstructors
public abstract class Tier extends AbstractGroupEntity {
	public Tier(Map props=[:], GroupEntity parent) {
		super(props, parent)
	}
}

public abstract class TierFromTemplate extends Tier implements Startable {
	OverpaasEntity template

	public TierFromTemplate(Map properties=[:], GroupEntity parent=null, OverpaasEntity template=null) {
		super(properties, parent);
		if (template) this.template = template
	}

}