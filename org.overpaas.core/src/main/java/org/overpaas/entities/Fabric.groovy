package org.overpaas.entities

import java.util.Collection;
import java.util.Map;

import org.overpaas.types.Location;
import org.overpaas.types.MultiLocation;

public abstract class Fabric extends TierFromTemplate implements MultiLocation {
	
	public final Collection<Location> locations = []
	
	public Fabric(Map properties=[:], Group parent=null, Entity template=null) {
		super(properties, parent, template)
		//accept the word 'location' singular as well as plural (plural was put into field already)
		if (this.properties.location) locations += this.properties.remove('location')
	}
	
	public Collection<Location> getLocations() { locations }

	public void start(Map addlChildProperties=[:]) {
		def childProperties = [:]
		childProperties << addlChildProperties
		
		if (!template) throw new IllegalStateException("cannot start tier $this: no template set")
		
		//if location or locations specified as argument, use them; otherwise use default
		def ll = (childProperties.remove('locations') ?: childProperties.location ?: locations ?: []).flatten() as Set
		
//		println "starting $this tier with properties "+childProperties+", locations $ll"

		def newNodes = ll.collect({ loc -> 
			if (children.any { it.properties.location==loc }) {
				println "start of $this tier skipping already-present location $loc"
				return null
			}
			EntityStartUtils.createFromTemplate(childProperties + [location:loc], this, template);
		}).findAll { it }  //remove nulls
		OverpaasDsl.run(newNodes.collect({ node -> { -> node.start() }}) as Closure[])
	}
			
}
