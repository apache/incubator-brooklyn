package brooklyn.entity.basic;

import groovy.lang.Closure;

import java.util.Collection;
import java.util.LinkedHashSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.event.Sensor;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.management.internal.CollectionChangeListener;
import brooklyn.management.internal.ManagementContextInternal;
import brooklyn.util.GroovyJavaMethods;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;

public class DynamicGroupImpl extends AbstractGroupImpl implements DynamicGroup {
    public static final Logger log = LoggerFactory.getLogger(DynamicGroupImpl.class);
    
    private final Object memberChangeMutex = new Object();
    
    private volatile MyEntitySetChangeListener setChangeListener = null;

    public DynamicGroupImpl() {
    }
    
    @Override
    public void init() {
        super.init();
        setAttribute(RUNNING, true);
    }
    
    public void setEntityFilter(Predicate<? super Entity> filter) {
        // TODO Sould this be "evenIfOwned"?
        setConfigEvenIfOwned(ENTITY_FILTER, filter);
        rescanEntities();
    }
    
    public void setEntityFilter(Closure<Boolean> filter) {
        setEntityFilter(filter != null ? GroovyJavaMethods.<Entity>predicateFromClosure(filter) : null);
    }
    
    private boolean isRunning() {
        return getAttribute(RUNNING);
    }
    
    /**
     * Stops this group (but does not stop any of its members). De-activates the filter and unsubscribes to
     * entity-updates, so the membership of the group will not change.
     */
    @Override
    public void stop() {
        setAttribute(RUNNING, false);
        if (setChangeListener != null) {
            ((ManagementContextInternal)getManagementContext()).removeEntitySetListener(setChangeListener);
        }
    }
    
    public <T> void addSubscription(Entity producer, Sensor<T> sensor, final Predicate<? super SensorEvent<? super T>> filter) {
        SensorEventListener<T> listener = new SensorEventListener<T>() {
            @Override public void onEvent(SensorEvent<T> event) {
                if (filter.apply(event)) onEntityChanged(event.getSource());
            }
        };
        subscribe(producer, sensor, listener);
    }

    public <T> void addSubscription(Entity producer, Sensor<T> sensor) {
        addSubscription(producer, sensor, Predicates.<SensorEvent<? super T>>alwaysTrue());
    }
    
    protected boolean acceptsEntity(Entity e) {
        Predicate<? super Entity> entityFilter = getConfig(ENTITY_FILTER);
        return (entityFilter != null && entityFilter.apply(e));
    }
    
    protected void onEntityAdded(Entity item) {
        synchronized (memberChangeMutex) {
            if (acceptsEntity(item)) {
                if (log.isDebugEnabled()) log.debug("{} detected item add {}", this, item);
                addMember(item);
            }
        }
    }
    
    protected void onEntityRemoved(Entity item) {
        synchronized (memberChangeMutex) {
            if (removeMember(item))
                if (log.isDebugEnabled()) log.debug("{} detected item removal {}", this, item);
        }
    }
    
    protected void onEntityChanged(Entity item) {
        synchronized (memberChangeMutex) {
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
    }
    
    class MyEntitySetChangeListener implements CollectionChangeListener<Entity> {
        public void onItemAdded(Entity item) { onEntityAdded(item); }
        public void onItemRemoved(Entity item) { onEntityRemoved(item); }
    }

    @Override
    public void onManagementBecomingMaster() {
        if (setChangeListener != null) {
            log.warn("{} becoming master twice", this);
            return;
        }
        setChangeListener = new MyEntitySetChangeListener();
        ((ManagementContextInternal)getManagementContext()).addEntitySetListener(setChangeListener);
        rescanEntities();
    }

    @Override
    public void onManagementNoLongerMaster() {
        if (setChangeListener == null) {
            log.warn("{} no longer master twice", this);
            return;
        }
        ((ManagementContextInternal) getManagementContext()).removeEntitySetListener(setChangeListener);
        setChangeListener = null;
    }
    
    public void rescanEntities() {
        synchronized (memberChangeMutex) {
            if (!isRunning() || !getManagementSupport().isDeployed()) {
                if (log.isDebugEnabled()) log.debug("{} not scanning for children: stopped", this);
                return;
            }
            if (getConfig(ENTITY_FILTER) == null) {
                log.warn("{} not (yet) scanning for children: no filter defined", this, this);
                return;
            }
            if (getApplication() == null) {
                log.warn("{} not (yet) scanning for children: no application defined", this);
                return;
            }
            boolean changed = false;
            Collection<Entity> currentMembers = super.getMembers();
            Collection<Entity> toRemove = new LinkedHashSet<Entity>(currentMembers);
            
            for (Entity it : getManagementContext().getEntityManager().getEntities()) {
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
}
