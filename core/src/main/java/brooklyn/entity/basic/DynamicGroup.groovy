package brooklyn.entity.basic

import groovy.lang.Closure

import java.beans.PropertyChangeListener
import java.util.Map

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Application
import brooklyn.entity.Entity

public class DynamicGroup extends AbstractGroup {
    private static final Logger log = LoggerFactory.getLogger(DynamicGroup.class)
    
    Closure entityFilter=null;
    
    public DynamicGroup(Map properties=[:], Entity owner=null, Closure entityFilter=null) {
        super(properties, owner)
        if (entityFilter) this.entityFilter = entityFilter;
        
        //do this last, rather than passing owner up, so that entity filter is ready
        if (owner) owner.addOwnedChild(this)
    }
    
    void setEntityFilter(Closure entityFilter) {
        this.entityFilter = entityFilter
        rescanEntities()
    }
    
    @Override
    protected synchronized void registerWithApplication(Application app) {
        super.registerWithApplication(app)
        app.addEntityChangeListener({ rescanEntities() } as PropertyChangeListener)
        rescanEntities()
    }
    
    public void rescanEntities() {
        //TODO extremely inefficient; should act on the event!
        if (!entityFilter) {
            log.info "not (yet) scanning for children of $this: no filter defined"
            return
        }
        if (!getApplication()) return
        Set existingMembers = super.getMembers() as HashSet
        log.debug "scanning {}", getApplication().getEntities()
        getApplication().getEntities().each {
            if (entityFilter.call(it)) {
                if (existingMembers.add(it))
                    addMember(it)
            } else if (existingMembers.remove(it)) {
                removeMember(it)
            } 
        }
    }
    
    @Override
    public Collection<Entity> getMembers() {
        rescanEntities();
        return super.getMembers();
    }
}
