package brooklyn.entity.basic

import groovy.lang.Closure

import java.util.Map

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Entity
import brooklyn.management.internal.AbstractManagementContext;
import brooklyn.management.internal.CollectionChangeListener

public class DynamicGroup extends AbstractGroup {
    public static final Logger log = LoggerFactory.getLogger(DynamicGroup.class)
    
    private volatile MyEntitySetChangeListener setChangeListener = null;
    private Closure entityFilter
    
    public DynamicGroup(Map properties=[:], Entity owner=null, Closure entityFilter=null) {
        super(properties, owner)
        if (entityFilter) this.entityFilter = entityFilter;
    }
    
    void setEntityFilter(Closure entityFilter) {
        this.entityFilter = entityFilter
        rescanEntities()
    }
    
    protected boolean acceptsEntity(Entity e) {
        return (entityFilter != null && entityFilter.call(e))
    }
    
    protected void onEntityAdded(Entity item) {
        if (acceptsEntity(item)) {
            log.info("$this detected item add $item")
            addMember(item)
        }
    }
    
    protected void onEntityRemoved(Entity item) {
        if (removeMember(item))
            log.info("$this detected item removal $item")
    }
    
    class MyEntitySetChangeListener implements CollectionChangeListener<Entity> {
        public void onItemAdded(Entity item) { onEntityAdded(item) }
        public void onItemRemoved(Entity item) { onEntityRemoved(item) }
    }

    @Override
    public synchronized void onManagementBecomingMaster() {
        if (setChangeListener != null) {
            log.warn("$this becoming master twice");
            return;
        }
        setChangeListener = new MyEntitySetChangeListener();
        ((AbstractManagementContext)getManagementContext()).addEntitySetListener(setChangeListener)
        rescanEntities();
    }

    @Override
    public synchronized void onManagementNoLongerMaster() {
        if (setChangeListener == null) {
            log.warn("$this no longer master twice");
            return;
        }
        ((AbstractManagementContext) getManagementContext()).removeEntitySetListener(setChangeListener)
        setChangeListener = null
    }
    
    public synchronized void rescanEntities() {
        if (!entityFilter) {
            log.warn "$this not (yet) scanning for children of $this: no filter defined"
            return
        }
        if (!getApplication()) {
            log.warn "$this not (yet) scanning for children of $this: no application defined"
            return
        }
        boolean changed = false
        Collection<Entity> currentMembers = super.getMembers()
        Collection<Entity> toRemove = []
        toRemove.addAll(currentMembers);
        ((AbstractManagementContext) getManagementContext()).getEntities().each {
            if (acceptsEntity(it) && !currentMembers.contains(it)) {
                log.info("$this rescan detected new item $it")
                addMember(it)
                toRemove.remove(it)
                changed = true
            }
        }
        toRemove.each { 
            log.info("$this rescan detected vanished item $it")
            removeMember(it)
            changed = true
        }
        if (changed)
            log.info("$this rescan complete, members now ${getMembers()}")
    }
    
}
