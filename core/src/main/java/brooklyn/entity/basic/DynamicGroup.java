package brooklyn.entity.basic;

import groovy.lang.Closure;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.event.AttributeSensor;
import brooklyn.event.Sensor;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.management.internal.AbstractManagementContext;
import brooklyn.management.internal.CollectionChangeListener;
import brooklyn.util.GroovyJavaMethods;
import brooklyn.util.MutableMap;
import brooklyn.util.flags.SetFromFlag;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Maps;

public class DynamicGroup extends AbstractGroup {
    public static final Logger log = LoggerFactory.getLogger(DynamicGroup.class);
    
    @SetFromFlag("entityFilter")
    public static final ConfigKey<Predicate<? super Entity>> ENTITY_FILTER = new BasicConfigKey(
            Predicate.class, "dynamicgroup.entityfilter", "Filter for which entities will automatically be in group", null);

    public static final AttributeSensor<Boolean> RUNNING = new BasicAttributeSensor<Boolean>(
            Boolean.class, "dynamicgroup.running", "Whether the entity is running, so will automatically update group membership");
    
    private volatile MyEntitySetChangeListener setChangeListener = null;

    public DynamicGroup(Entity owner) {
        this(Maps.newLinkedHashMap(), owner);
    }
    public DynamicGroup(Map<?,?> properties) {
        this(properties, (Entity)null);
    }
    public DynamicGroup(Entity owner, Closure<Boolean> entityFilter) {
        this(Maps.newLinkedHashMap(), owner, entityFilter);
    }
    public DynamicGroup(Map<?,?> properties, Entity owner, Closure<Boolean> entityFilter) {
        this(MutableMap.builder().putAll(properties).put("entityFilter", entityFilter).build(), owner);
        // (entityFilter != null ? GroovyJavaMethods.<Entity>predicateFromClosure(entityFilter) : null)
    }
    public DynamicGroup(Closure<Boolean> entityFilter) {
        this(Maps.newLinkedHashMap(), null, entityFilter);
    }
    public DynamicGroup(Map<?,?> properties, Closure<Boolean> entityFilter) {
        this(properties, null, entityFilter);
    }
    public DynamicGroup(Map<?,?> properties, Entity owner, Predicate<? super Entity> entityFilter) {
        this(MutableMap.builder().putAll(properties).put("entityFilter", entityFilter).build(), owner);
    }
    public DynamicGroup(Entity owner, Predicate<? super Entity> entityFilter) {
        this(MutableMap.of("entityFilter", entityFilter), owner);
    }
    public DynamicGroup(Predicate<? super Entity> entityFilter) {
        this(MutableMap.of("entityFilter", entityFilter), (Entity)null);
    }
    public DynamicGroup(Map<?,?> properties, Predicate<? super Entity> entityFilter) {
        this(MutableMap.builder().putAll(properties).put("entityFilter", entityFilter).build(), (Entity)null);
    }
    public DynamicGroup(Map<?,?> properties, Entity owner) {
        super(properties, owner);
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
    public void stop() {
        setAttribute(RUNNING, false);
        if (setChangeListener != null) {
            ((AbstractManagementContext)getManagementContext()).removeEntitySetListener(setChangeListener);
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
