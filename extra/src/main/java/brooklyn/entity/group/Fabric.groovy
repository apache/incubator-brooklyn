package brooklyn.entity.group

import java.util.Collection
import java.util.Map

import brooklyn.entity.Entity
import brooklyn.entity.Group
import brooklyn.location.Location
import brooklyn.util.internal.EntityStartUtils

/**
 * Fabric is a {@link Tier} of entities over multiple locations.
 */
public abstract class Fabric extends TierFromTemplate {
    public Fabric(Map properties=[:], Group owner=null, Entity template=null) {
        super(properties, owner, template)
        // accept the word 'location' singular as well as plural (plural was put into field already)
        if (this.properties.location) locations += this.properties.remove('location')
    }
    
    public void start(Collection<? extends Location> locs) {
        // FIXME Do we want to use one location per child?
        
        if (!template) throw new IllegalStateException("cannot start tier $this: no template set")
        
        //if location or locations specified as argument, use them; otherwise use default
        
        log.info "starting $this tier in locations $locs"

        def newNodes = locs.collect({ loc -> 
            if (children.any { it.properties.location==loc }) {
                log.info "start of $this tier skipping already-present location $loc"
                return null
            }
            EntityStartUtils.createFromTemplate(this, template);
        }).findAll { it }  //remove nulls
        //TODO use ParallelTask
        Set tasks = newNodes.collect { node -> getExecutionContext().submit({node.start(locs)}) }
        tasks.collect { it.get() }
    }
}
