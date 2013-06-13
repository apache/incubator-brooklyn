package brooklyn.policy.loadbalancing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.Group;
import brooklyn.entity.basic.AbstractGroup;
import brooklyn.entity.basic.DynamicGroupImpl;
import brooklyn.event.Sensor;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;

import com.google.common.base.Predicate;

/**
 * A group of items that are contained within a given (dynamically changing) set of containers.
 * 
 * The {@link setContainers(Group)} sets the group of containers. The membership of that group
 * is dynamically tracked.
 * 
 * When containers are added/removed, or when an items is added/removed, or when an {@link Moveable} item 
 * is moved then the membership of this group of items is automatically updated accordingly.
 * 
 * For example: in Monterey, this could be used to track the actors that are within a given cluster of venues.
 */
public class ItemsInContainersGroupImpl extends DynamicGroupImpl implements ItemsInContainersGroup {

    // TODO Inefficient: will not scale to many 1000s of items

    private static final Logger LOG = LoggerFactory.getLogger(ItemsInContainersGroup.class);
    
    private Group containerGroup;
    
    private final SensorEventListener<Object> eventHandler = new SensorEventListener<Object>() {
        @Override
        public void onEvent(SensorEvent<Object> event) {
            Entity source = event.getSource();
            Object value = event.getValue();
            Sensor sensor = event.getSensor();
            
            if (sensor.equals(AbstractGroup.MEMBER_ADDED)) {
                    onContainerAdded((Entity) value);
            } else if (sensor.equals(AbstractGroup.MEMBER_REMOVED)) {
                onContainerRemoved((Entity) value);
            } else if (sensor.equals(Movable.CONTAINER)) {
                onItemMoved((Movable)source, (BalanceableContainer<?>) value);
            } else {
                throw new IllegalStateException("Unhandled event type "+sensor+": "+event);
            }
        }
    };

    public ItemsInContainersGroupImpl() {
    }
    
    @Override
    public void init() {
        super.init();
        setEntityFilter(new Predicate<Entity>() {
            @Override public boolean apply(Entity e) {
                return acceptsEntity(e);
            }});
    }
    
    protected Predicate<? super Entity> getItemFilter() {
        return getConfig(ITEM_FILTER);
    }
    
    @Override
    protected boolean acceptsEntity(Entity e) {
        if (e instanceof Movable) {
            return acceptsItem((Movable)e, ((Movable)e).getAttribute(Movable.CONTAINER));
        }
        return false;
    }

    boolean acceptsItem(Movable e, BalanceableContainer c) {
        return (containerGroup != null && c != null) ? getItemFilter().apply(e) && containerGroup.hasMember(c) : false;
    }

    @Override
    public void setContainers(Group containerGroup) {
        this.containerGroup = containerGroup;
        subscribe(containerGroup, AbstractGroup.MEMBER_ADDED, eventHandler);
        subscribe(containerGroup, AbstractGroup.MEMBER_REMOVED, eventHandler);
        subscribe(null, Movable.CONTAINER, eventHandler);
        
        if (LOG.isTraceEnabled()) LOG.trace("{} scanning entities on container group set", this);
        rescanEntities();
    }
    
    private void onContainerAdded(Entity newContainer) {
        if (LOG.isTraceEnabled()) LOG.trace("{} rescanning entities on container {} added", this, newContainer);
        rescanEntities();
    }
    
    private void onContainerRemoved(Entity oldContainer) {
        if (LOG.isTraceEnabled()) LOG.trace("{} rescanning entities on container {} removed", this, oldContainer);
        rescanEntities();
    }
    
    protected void onEntityAdded(Entity item) {
        if (acceptsEntity(item)) {
            if (LOG.isDebugEnabled()) LOG.debug("{} adding new item {}", this, item);
            addMember(item);
        }
    }
    
    protected void onEntityRemoved(Entity item) {
        if (removeMember(item)) {
            if (LOG.isDebugEnabled()) LOG.debug("{} removing deleted item {}", this, item);
        }
    }
    
    private void onItemMoved(Movable item, BalanceableContainer container) {
        if (LOG.isTraceEnabled()) LOG.trace("{} processing moved item {}, to container {}", new Object[] {this, item, container});
        if (hasMember(item)) {
            if (!acceptsItem(item, container)) {
                if (LOG.isDebugEnabled()) LOG.debug("{} removing moved item {} from group, as new container {} is not a member", new Object[] {this, item, container});
                removeMember(item);
            }
        } else {
            if (acceptsItem(item, container)) {
                if (LOG.isDebugEnabled()) LOG.debug("{} adding moved item {} to group, as new container {} is a member", new Object[] {this, item, container});
                addMember(item);
            }
        }
    }
}
