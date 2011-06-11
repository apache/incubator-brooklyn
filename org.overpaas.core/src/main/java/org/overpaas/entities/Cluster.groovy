package org.overpaas.entities

import groovy.transform.InheritConstructors;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

import org.overpaas.decorators.Resizable;
import org.overpaas.decorators.Startable;
import org.overpaas.types.Location;
import org.overpaas.types.SingleLocation;

/**
 * intended to represent a group of homogeneous entities in a single location;
 * subclass must implement {@link #expand()} and {@link #shrink()} to specify how to create/remove nodes; 
 * caller must supply location as field either in constructor or call to start;
 * initialSize property determines initial size when started (defaults to 1)
 */
@InheritConstructors
public abstract class Cluster<T extends Entity> extends Tier implements Startable, SingleLocation {
	public Cluster(Map props=[:], Group parent) {
		super(props, parent)
	}

	Location location
	
	@Override
	public Collection<String> toStringFieldsToInclude() {
		return super.toStringFieldsToInclude() + ['location'];
	}
}

public abstract class ClusterFromTemplate extends Cluster implements Resizable {
	Entity template=null
	
	public ClusterFromTemplate(Map properties=[:], Group parent=null, Entity template=null) {
		super(properties, parent)
		if (template) this.template = template
	}
	
	public List<Future> grow(int desiredIncrease) {
		def nodes = []
		desiredIncrease.times { nodes += EntityStartUtils.createFromTemplate(this, template) }
		OverpaasDsl.run( nodes.collect({ node -> { -> node.start() } }) as Closure[] )
	}
//	public List<Future> shrink(int desiredDecrease) {
//		throw new UnsupportedOperationException()
//	}

	int initialSize = 1

	public synchronized void start(Map addlProperties=[:]) {
		properties << addlProperties

		//		println "starting $this cluster with properties "+properties+", size $initialSize"
		if (!(initialSize in Integer))
			throw new IllegalArgumentException('cluster initial size must be an integer')

		int newNodes = initialSize - children.size()
		if (newNodes>0) grow(newNodes)
		else {
			println "start of $this cluster skipping call to start with size $initialSize because size is currently "+children.size()
		}
	}

	public synchronized List<Future> resize(int newSize) {
		int newNodes = newSize - children.size()
		if (newNodes>0) grow(newNodes)
		else if (newNodes<0) shrink(-newNodes);
		else []
	}

}
