package brooklyn.entity.group

import java.util.Collection
import java.util.List
import java.util.Map
import java.util.concurrent.Future

import brooklyn.entity.Entity
import brooklyn.entity.Group
import brooklyn.entity.trait.Resizable
import brooklyn.entity.trait.Startable
import brooklyn.util.internal.EntityStartUtils

/**
 * intended to represent a group of homogeneous entities in a single location;
 * subclass must implement {@link #expand()} and {@link #shrink()} to specify how to create/remove nodes; 
 * caller must supply location as field either in constructor or call to start;
 * initialSize property determines initial size when started (defaults to 1)
 */
public abstract class Cluster extends Tier implements Startable {
	public Cluster(Map props=[:], Group parent) {
		super(props, parent)
	}
    
    
    // TODO single location
}

public abstract class ClusterFromTemplate extends Cluster implements Resizable {
	Entity template = null
	
	public ClusterFromTemplate(Map properties=[:], Group parent=null, Entity template=null) {
		super(properties, parent)
		if (template) this.template = template
	}
	
	public List<Future> grow(int desiredIncrease) {
		def nodes = []
		desiredIncrease.times { nodes += EntityStartUtils.createFromTemplate(this, template) }
		
		Set tasks = nodes.collect { node -> getExecutionContext().submit({node.start()}) }
		tasks.collect { it.get() }
	}

	public List<Future> shrink(int desiredDecrease) {
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

	public synchronized List<Future> resize(int newSize) {
		int newNodes = newSize - children.size()
		if (newNodes>0) grow(newNodes)
		else if (newNodes<0) shrink(-newNodes);
		else []
	}
}
