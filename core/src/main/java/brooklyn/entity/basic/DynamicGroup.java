package brooklyn.entity.basic;

import groovy.lang.Closure;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.event.Sensor;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.management.internal.AbstractManagementContext;
import brooklyn.management.internal.CollectionChangeListener;
import brooklyn.util.GroovyJavaMethods;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Maps;

public class DynamicGroup extends AbstractGroup {
    public static final Logger log = LoggerFactory.getLogger(DynamicGroup.class);
    
    private volatile MyEntitySetChangeListener setChangeListener = null;
    private volatile Predicate<Entity> entityFilter;
    private volatile boolean running = true;

    public DynamicGroup(Map<?,?> properties, Entity owner, Closure<Boolean> entityFilter) {
        this(properties, owner, (entityFilter != null ? GroovyJavaMethods.<Entity>predicateFromClosure(entityFilter) : null));
    }
    public DynamicGroup(Map<?,?> properties) {
        this(properties, null, (Predicate<Entity>)null);
    }
    public DynamicGroup(Map<?,?> properties, Entity owner) {
        this(properties, owner, (Predicate<Entity>)null);
    }
    public DynamicGroup(Entity owner) {
        this(Maps.newLinkedHashMap(), owner, (Predicate<Entity>)null);
    }
    public DynamicGroup(Entity owner, Closure<Boolean> entityFilter) {
        this(Maps.newLinkedHashMap(), owner, entityFilter);
    }
    public DynamicGroup(Closure<Boolean> entityFilter) {
        this(Maps.newLinkedHashMap(), null, entityFilter);
    }
    public DynamicGroup(Map<?,?> properties, Closure<Boolean> entityFilter) {
        this(properties, null, entityFilter);
    }
    
    public DynamicGroup(Map<?,?> properties, Entity owner, Predicate<Entity> entityFilter) {
        super(properties, owner);
        this.entityFilter = entityFilter;
    }
    public DynamicGroup(Entity owner, Predicate<Entity> entityFilter) {
        this(Maps.newLinkedHashMap(), owner, entityFilter);
    }
    public DynamicGroup(Predicate<Entity> entityFilter) {
        this(Maps.newLinkedHashMap(), null, entityFilter);
    }
    public DynamicGroup(Map<?,?> properties, Predicate<Entity> entityFilter) {
        this(properties, null, entityFilter);
    }

    /**
     * Stops this group (but does not stop any of its members). De-activates the filter and unsubscribes to
     * entity-updates, so the membership of the group will not change.
     */
    public void stop() {
        running = false;
        if (setChangeListener != null) {
            ((AbstractManagementContext)getManagementContext()).removeEntitySetListener(setChangeListener);
        }
    }
    
    public void setEntityFilter(Predicate<Entity> filter) {
        this.entityFilter = filter;
        rescanEntities();
    }
    
    public void setEntityFilter(Closure<Boolean> filter) {
        setEntityFilter(filter != null ? GroovyJavaMethods.<Entity>predicateFromClosure(filter) : null);
    }
    
    public <T> void addSubscription(Entity producer, Sensor<T> sensor, final Predicate<SensorEvent<? super T>> filter) {
        SensorEventListener<T> listener = new SensorEventListener<T>() {
            @Override public void onEvent(SensorEvent<T> event) {
                if (filter.apply(event)) onEntityChanged(event.getSource());
            }
        };
        subscribe(producer, sensor, listener);
    }

    <T> void addSubscription(Entity producer, Sensor<T> sensor) {
        addSubscription(producer, sensor, Predicates.<SensorEvent<? super T>>alwaysTrue());
    }
    
    protected boolean acceptsEntity(Entity e) {
        // TODO Race where entityFilter could be set to null between the two statements below
        return (entityFilter != null && entityFilter.apply(e));
    }
    
    protected void onEntityAdded(Entity item) {
        if (acceptsEntity(item)) {
            if (log.isDebugEnabled()) log.debug("{} detected item add {}", this, item);
            addMember(item);
        }
    }
    
    protected void onEntityRemoved(Entity item) {
        if (removeMember(item))
            if (log.isDebugEnabled()) log.debug("{} detected item removal {}", this, item);
    }
    
    protected void onEntityChanged(Entity item) {
        boolean accepts = acceptsEntity(item);
        boolean has = hasMember(item);
        if (has && !accepts) {
            removeMember(item);
            if (log.isDebugEnabled()) log.debug("{} detected item removal on change of {}", this, item);
        } else if (!has && accepts) {
            if (log.isDebugEnabled()) log.debug("{} detected item add on change of {}", this, item);
            addMember(item);
        }
    }
    
    class MyEntitySetChangeListener implements CollectionChangeListener<Entity> {
        public void onItemAdded(Entity item) { onEntityAdded(item); }
        public void onItemRemoved(Entity item) { onEntityRemoved(item); }
    }

    @Override
    public synchronized void onManagementBecomingMaster() {
        if (setChangeListener != null) {
            log.warn("{} becoming master twice", this);
            return;
        }
        setChangeListener = new MyEntitySetChangeListener();
        ((AbstractManagementContext)getManagementContext()).addEntitySetListener(setChangeListener);
        rescanEntities();
    }

    @Override
    public synchronized void onManagementNoLongerMaster() {
        if (setChangeListener == null) {
            log.warn("{} no longer master twice", this);
            return;
        }
        ((AbstractManagementContext) getManagementContext()).removeEntitySetListener(setChangeListener);
        setChangeListener = null;
    }
    
    public synchronized void rescanEntities() {
        if (!running) {
            if (log.isDebugEnabled()) log.debug("{} not scanning for children: stopped", this);
            return;
        }
        if (entityFilter == null) {
            log.warn("{} not (yet) scanning for children: no filter defined", this, this);
            return;
        }
        if (getApplication() == null) {
            log.warn("{} not (yet) scanning for children: no application defined", this);
            return;
        }
        boolean changed = false;
        Collection<Entity> currentMembers = super.getMembers();
        Collection<Entity> toRemove = new LinkedHashSet<Entity>();
        toRemove.addAll(currentMembers);
        for (Entity it : ((AbstractManagementContext) getManagementContext()).getEntities()) {
            if (acceptsEntity(it)) {
                toRemove.remove(it);
                if (!currentMembers.contains(it)) {
                    if (log.isDebugEnabled()) log.debug("{} rescan detected new item {}", this, it);
                    addMember(it);
                    changed = true;
                }
            }
        }
        for (Entity it: toRemove) { 
            if (log.isDebugEnabled()) log.debug("{} rescan detected vanished item {}", this, it);
            removeMember(it);
            changed = true;
        }
        if (changed)
            if (log.isDebugEnabled()) log.debug("{} rescan complete, members now {}", this, getMembers());
    }
}
