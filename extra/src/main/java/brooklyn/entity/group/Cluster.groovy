package brooklyn.entity.group

import java.util.Collection
import java.util.List
import java.util.Map
import java.util.concurrent.Future

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Entity
import brooklyn.entity.Group
import brooklyn.entity.basic.AbstractGroup
import brooklyn.entity.trait.Resizable
import brooklyn.entity.trait.Startable
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.event.basic.BasicConfigKey
import brooklyn.location.Location
import brooklyn.util.internal.EntityStartUtils

/**
 * Intended to represent a group of homogeneous entities in a single location.
 */
public interface Cluster extends Group, Startable, Resizable {
    BasicConfigKey<Integer> INITIAL_SIZE = [ Integer, "cluster.initial.size", "Initial cluster size", 0 ]

    BasicAttributeSensor<String> CLUSTER_SIZE = [ Integer, "cluster.size", "Cluster size" ]
}

public abstract class ClusterFromTemplate extends  AbstractGroup implements Cluster {
    final static Logger log = LoggerFactory.getLogger(ClusterFromTemplate.class);

    Entity template = null
    Collection<Location> locations = null
    
    public ClusterFromTemplate(Map properties=[:], Entity owner=null, Entity template=null) {
        super(properties, owner)
        if (template) this.template = template
    }
    
    public List<Future> grow(int desiredIncrease) {
        def nodes = []
        desiredIncrease.times { nodes += EntityStartUtils.createFromTemplate(this, template) }

        Set tasks = nodes.collect { node -> getExecutionContext().submit({node.start(locations)}) }
        tasks.collect { it.get() }
    }

    public List<Future> shrink(int desiredDecrease) {
        throw new UnsupportedOperationException()
    }

    public synchronized void start(Collection<Location> locs) {
        this.locations = locs

        if (!(initialSize in Integer))
            throw new IllegalArgumentException('cluster initial size must be an integer')

        log.debug "starting $this cluster with properties {} and size $initialSize in $locs"

        int newNodes = initialSize - members.size()
        if (newNodes>0) grow(newNodes)
        else {
            log.info "start of $this cluster skipping call to start with size $initialSize because size is currently {}", children.size()
        }
    }
    
    public synchronized void stop() {
        members.each { Startable entity  -> entity.stop() }
    }

    public synchronized void restart() {
        stop()
        start locations
    }

    public synchronized Integer resize(Integer newSize) {
        int newNodes = newSize - members.size()
        if (newNodes>0) grow(newNodes)
        else if (newNodes<0) shrink(-newNodes);
        return members.size()
    }
}
