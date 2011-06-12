package org.overpaas.entities

import java.util.Map;

import org.overpaas.decorators.Startable;

public abstract class Tier extends AbstractGroup {
	public Tier(Map props=[:], Group parent) {
		super(props, parent)
	}
}

public abstract class TierFromTemplate extends Tier implements Startable {
	Entity template

	public TierFromTemplate(Map properties=[:], Group parent=null, Entity template=null) {
		super(properties, parent);
		if (template) this.template = template
	}
}