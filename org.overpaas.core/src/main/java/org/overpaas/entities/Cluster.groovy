package org.overpaas.entities

import java.util.Collection
import java.util.Map

import org.overpaas.decorators.Resizable
import org.overpaas.decorators.Startable
import org.overpaas.execution.CompoundTask
import org.overpaas.execution.ExecutionManager
import org.overpaas.execution.ParallelTask
import org.overpaas.execution.SequentialTask
import org.overpaas.execution.Task
import org.overpaas.types.Location
import org.overpaas.types.SingleLocation
import org.overpaas.util.EntityStartUtils

/**
 * intended to represent a group of homogeneous entities in a single location;
 * subclass must implement {@link #expand()} and {@link #shrink()} to specify how to create/remove nodes; 
 * caller must supply location as field either in constructor or call to start;
 * initialSize property determines initial size when started (defaults to 1)
 */
public abstract class Cluster extends Tier implements Startable, SingleLocation {
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
	Entity template = null
	
	public ClusterFromTemplate(Map properties=[:], Group parent=null, Entity template=null) {
		super(properties, parent)
		if (template) this.template = template
	}
	
	public CompoundTask grow(int desiredIncrease) {
		
		final def nodes = []
		ExecutionManager.execute(this, new SequentialTask( 
			new Task({desiredIncrease.times { nodes += EntityStartUtils.createFromTemplate(this, template) }}),
			new ParallelTask( nodes.collect({ node -> { -> node.start() } }) as Closure[] ))
		)
	}

	public CompoundTask shrink(int desiredDecrease) {
		throw new UnsupportedOperationException()
	}

	int initialSize = 1

	public synchronized void start(Map addlProperties=[:]) {
		properties << addlProperties
		if (!(initialSize in Integer))
			throw new IllegalArgumentException('cluster initial size must be an integer')

		log.debug "starting $this cluster with properties {} and size $initialSize", properties

		int newNodes = initialSize - children.size()
		if (newNodes>0) grow(newNodes)
		else {
			log.info "start of $this cluster skipping call to start with size $initialSize because size is currently {}", children.size()
		}
	}

	public synchronized CompoundTask resize(int newSize) {
		int newNodes = newSize - children.size()
		if (newNodes>0) grow(newNodes)
		else if (newNodes<0) shrink(-newNodes);
		else []
	}
}
