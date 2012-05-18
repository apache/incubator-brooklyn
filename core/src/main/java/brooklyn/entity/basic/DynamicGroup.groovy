package brooklyn.entity.basic

import groovy.lang.Closure

import java.util.Map

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Entity
import brooklyn.event.Sensor
import brooklyn.event.SensorEvent
import brooklyn.event.SensorEventListener
import brooklyn.management.internal.AbstractManagementContext
import brooklyn.management.internal.CollectionChangeListener

import com.google.common.base.Predicate

public class DynamicGroup extends AbstractGroup {
    public static final Logger log = LoggerFactory.getLogger(DynamicGroup.class)
    
    private volatile MyEntitySetChangeListener setChangeListener = null;
    private Predicate<Entity> entityFilter
    private volatile running = true

    public DynamicGroup(Map properties=[:], Entity owner=null, Closure entityFilter=null) {
        this(properties, owner, (entityFilter != null ? entityFilter as Predicate : null))
    }
    
    public DynamicGroup(Map properties=[:], Entity owner=null, Predicate<Entity> entityFilter) {
        super(properties, owner)
        if (entityFilter) this.entityFilter = entityFilter;
    }
    
    /**
     * Stops this group (but does not stop any of its members). De-activates the filter and unsubscribes to
     * entity-updates, so the membership of the group will not change.
     */
    public void stop() {
        running = false
        if (setChangeListener != null) {
            ((AbstractManagementContext)getManagementContext()).removeEntitySetListener(setChangeListener)
        }
    }
    
    void setEntityFilter(Predicate<Entity> filter) {
        this.entityFilter = filter
        rescanEntities()
    }
    
    void setEntityFilter(Closure filter) {
        setEntityFilter(filter != null ? filter as Predicate : null)
    }
    
    void addSubscription(Entity producer, Sensor sensor, Predicate<SensorEvent> filter={true} as Predicate) {
        subscribe(producer, sensor, { SensorEvent event -> if (filter.apply(event)) onEntityChanged(event.getSource()) } as SensorEventListener)
    }
    
    protected boolean acceptsEntity(Entity e) {
        return (entityFilter != null && entityFilter.apply(e))
    }
    
    protected void onEntityAdded(Entity item) {
        if (acceptsEntity(item)) {
            if (log.isDebugEnabled()) log.debug("$this detected item add $item")
            addMember(item)
        }
    }
    
    protected void onEntityRemoved(Entity item) {
        if (removeMember(item))
            if (log.isDebugEnabled()) log.debug("$this detected item removal $item")
    }
    
    protected void onEntityChanged(Entity item) {
        boolean accepts = acceptsEntity(item);
        boolean has = hasMember(item);
        if (has && !accepts) {
            removeMember(item)
            if (log.isDebugEnabled()) log.debug("{} detected item removal on change of {}", this, item)
        } else if (!has && accepts) {
            if (log.isDebugEnabled()) log.debug("{} detected item add on change of {}", this, item)
            addMember(item)
        }
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
        if (!running) {
            if (log.isDebugEnabled()) log.debug "$this not scanning for children: stopped"
            return
        }
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
        toRemove.addAll(currentMembers)
        ((AbstractManagementContext) getManagementContext()).getEntities().each {
            if (acceptsEntity(it)) {
                toRemove.remove(it)
                if (!currentMembers.contains(it)) {
                    if (log.isDebugEnabled()) log.debug("$this rescan detected new item $it")
                    addMember(it)
                    changed = true
                }
            }
        }
        toRemove.each { 
            if (log.isDebugEnabled()) log.debug("$this rescan detected vanished item $it")
            removeMember(it)
            changed = true
        }
        if (changed)
            if (log.isDebugEnabled()) log.debug("$this rescan complete, members now ${getMembers()}")
    }
}
