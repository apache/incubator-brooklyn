package org.overpaas.core.types.common


import groovy.transform.InheritConstructors;

import java.util.Collection
import java.util.Map
import java.util.concurrent.Future

import org.overpaas.core.decorators.GroupEntity;
import org.overpaas.core.decorators.Location;
import org.overpaas.core.decorators.OverpaasEntity;
import org.overpaas.core.decorators.Resizable;
import org.overpaas.core.decorators.Startable;
import org.overpaas.core.decorators.Location.SingleLocationEntity;
import org.overpaas.util.OverpaasDsl;

/** intended to represent a group of homogeneous entities in a single location;
 * subclass must implement {@link #expand()} and {@link #shrink()} to specify how to create/remove nodes; 
 * caller must supply location as field either in constructor or call to start;
 * initialSize property determines initial size when started (defaults to 1)
 **/
//@InheritConstructors
public abstract class Cluster<T extends OverpaasEntity> extends Tier implements Startable, SingleLocationEntity {

	public Cluster(Map props=[:], GroupEntity parent) {
		super(props, parent)
	}

	Location location
	
	@Override
	public Collection<String> toStringFieldsToInclude() {
		return super.toStringFieldsToInclude() + ['location'];
	}
}

public abstract class ClusterFromTemplate extends Cluster implements Resizable {
	OverpaasEntity template=null
	
	public ClusterFromTemplate(Map properties=[:], GroupEntity parent=null, OverpaasEntity template=null) {
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
