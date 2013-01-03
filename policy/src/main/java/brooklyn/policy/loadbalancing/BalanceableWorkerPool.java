package brooklyn.policy.loadbalancing;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.Group;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.basic.AbstractGroup;
import brooklyn.entity.trait.Resizable;
import brooklyn.entity.trait.Startable;
import brooklyn.event.Sensor;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.event.basic.BasicNotificationSensor;
import brooklyn.util.MutableMap;


/**
 * Represents an elastic group of "container" entities, each of which is capable of hosting "item" entities that perform
 * work and consume the container's available resources (e.g. CPU or bandwidth). Auto-scaling and load-balancing policies can
 * be attached to this pool to provide dynamic elasticity based on workrates reported by the individual item entities.
 */
public class BalanceableWorkerPool extends AbstractEntity implements Resizable {

    // FIXME Asymmetry between loadbalancing and followTheSun: ITEM_ADDED and ITEM_REMOVED in loadbalancing
    // are of type ContainerItemPair, but in followTheSun it is just the `Entity item`.
    
    private static final Logger LOG = LoggerFactory.getLogger(BalanceableWorkerPool.class);
    
    /** Encapsulates an item and a container; emitted for <code>ITEM_ADDED</code>, <code>ITEM_REMOVED</code> and
     * <code>ITEM_MOVED</code> sensors.
     */
    public static class ContainerItemPair implements Serializable {
        private static final long serialVersionUID = 1L;
        public final BalanceableContainer<?> container;
        public final Entity item;
        
        public ContainerItemPair(BalanceableContainer<?> container, Entity item) {
            this.container = container;
            this.item = checkNotNull(item);
        }
        
        @Override
        public String toString() {
            return ""+item+" @ "+container;
        }
    }
    
    // Pool constituent notifications.
    public static BasicNotificationSensor<Entity> CONTAINER_ADDED = new BasicNotificationSensor<Entity>(
        Entity.class, "balanceablepool.container.added", "Container added to balanceable pool");
    public static BasicNotificationSensor<Entity> CONTAINER_REMOVED = new BasicNotificationSensor<Entity>(
        Entity.class, "balanceablepool.container.removed", "Container removed from balanceable pool");
    public static BasicNotificationSensor<ContainerItemPair> ITEM_ADDED = new BasicNotificationSensor<ContainerItemPair>(
        ContainerItemPair.class, "balanceablepool.item.added", "Item added to balanceable pool");
    public static BasicNotificationSensor<ContainerItemPair> ITEM_REMOVED = new BasicNotificationSensor<ContainerItemPair>(
        ContainerItemPair.class, "balanceablepool.item.removed", "Item removed from balanceable pool");
    public static BasicNotificationSensor<ContainerItemPair> ITEM_MOVED = new BasicNotificationSensor<ContainerItemPair>(
        ContainerItemPair.class, "balanceablepool.item.moved", "Item moved in balanceable pool to the given container");
    
    private Group containerGroup;
    private Group itemGroup;
    private Resizable resizable;
    
    private final Set<Entity> containers = Collections.synchronizedSet(new HashSet<Entity>());
    private final Set<Entity> items = Collections.synchronizedSet(new HashSet<Entity>());
    
    private final SensorEventListener<Object> eventHandler = new SensorEventListener<Object>() {
        @Override
        public void onEvent(SensorEvent<Object> event) {
            if (LOG.isTraceEnabled()) LOG.trace("{} received event {}", BalanceableWorkerPool.this, event);
            Entity source = event.getSource();
            Object value = event.getValue();
            Sensor sensor = event.getSensor();
            
            if (sensor.equals(AbstractGroup.MEMBER_ADDED)) {
                if (source.equals(containerGroup)) {
                    onContainerAdded((BalanceableContainer<?>) value);
                } else if (source.equals(itemGroup)) {
                    onItemAdded((Entity)value);
                } else {
                    throw new IllegalStateException("unexpected event source="+source);
                }
            } else if (sensor.equals(AbstractGroup.MEMBER_REMOVED)) {
                if (source.equals(containerGroup)) {
                    onContainerRemoved((BalanceableContainer<?>) value);
                } else if (source.equals(itemGroup)) {
                    onItemRemoved((Entity) value);
                } else {
                    throw new IllegalStateException("unexpected event source="+source);
                }
            } else if (sensor.equals(Startable.SERVICE_UP)) {
                // TODO What if start has failed? Is there a sensor to indicate that?
                if ((Boolean)value) {
                    onContainerUp((BalanceableContainer<?>) source);
                } else {
                    onContainerDown((BalanceableContainer<?>) source);
                }
            } else if (sensor.equals(Movable.CONTAINER)) {
                onItemMoved(source, (BalanceableContainer<?>) value);
            } else {
                throw new IllegalStateException("Unhandled event type "+sensor+": "+event);
            }
        }
    };
    
    public BalanceableWorkerPool() {
        this(MutableMap.of(), null);
    }
    public BalanceableWorkerPool(Map properties) {
        this(properties, null);
    }
    public BalanceableWorkerPool(Entity parent) {
        this(MutableMap.of(), parent);
    }
    public BalanceableWorkerPool(Map properties, Entity parent) {
        super(properties, parent);
    }

    public void setResizable(Resizable resizable) {
        this.resizable = resizable;
    }
    
    public void setContents(Group containerGroup, Group itemGroup) {
        this.containerGroup = containerGroup;
        this.itemGroup = itemGroup;
        if (resizable == null && containerGroup instanceof Resizable) resizable = (Resizable) containerGroup;
        
        subscribe(containerGroup, AbstractGroup.MEMBER_ADDED, eventHandler);
        subscribe(containerGroup, AbstractGroup.MEMBER_REMOVED, eventHandler);
        subscribe(itemGroup, AbstractGroup.MEMBER_ADDED, eventHandler);
        subscribe(itemGroup, AbstractGroup.MEMBER_REMOVED, eventHandler);
        
        // Process extant containers and items
        for (Entity existingContainer : containerGroup.getMembers()) {
            onContainerAdded((BalanceableContainer<?>)existingContainer);
        }
        for (Entity existingItem : itemGroup.getMembers()) {
            onItemAdded(existingItem);
        }
    }
    
    public Group getContainerGroup() {
        return containerGroup;
    }
    
    public Group getItemGroup() {
        return itemGroup;
    }

    // methods inherited from Resizable
    public Integer getCurrentSize() { return containerGroup.getCurrentSize(); }
    
    public Integer resize(Integer desiredSize) {
        if (resizable != null) return resizable.resize(desiredSize);
        
        throw new UnsupportedOperationException("Container group is not resizable, and no resizable supplied: "+containerGroup+" of type "+(containerGroup != null ? containerGroup.getClass().getCanonicalName() : null));
    }
    
    private void onContainerAdded(BalanceableContainer<?> newContainer) {
        subscribe(newContainer, Startable.SERVICE_UP, eventHandler);
        if (!(newContainer instanceof Startable) || Boolean.TRUE.equals(newContainer.getAttribute(Startable.SERVICE_UP))) {
            onContainerUp(newContainer);
        }
    }
    
    private void onContainerUp(BalanceableContainer<?> newContainer) {
        if (containers.add(newContainer)) {
            emit(CONTAINER_ADDED, newContainer);
        }
    }
    
    private void onContainerDown(BalanceableContainer<?> oldContainer) {
        if (containers.remove(oldContainer)) {
            emit(CONTAINER_REMOVED, oldContainer);
        }
    }
    
    private void onContainerRemoved(BalanceableContainer<?> oldContainer) {
        unsubscribe(oldContainer);
        onContainerDown(oldContainer);
    }
    
    private void onItemAdded(Entity item) {
        if (items.add(item)) {
            subscribe(item, Movable.CONTAINER, eventHandler);
            emit(ITEM_ADDED, new ContainerItemPair(item.getAttribute(Movable.CONTAINER), item));
        }
    }
    
    private void onItemRemoved(Entity item) {
        if (items.remove(item)) {
            unsubscribe(item);
            emit(ITEM_REMOVED, new ContainerItemPair(null, item));
        }
    }
    
    private void onItemMoved(Entity item, BalanceableContainer<?> container) {
        emit(ITEM_MOVED, new ContainerItemPair(container, item));
    }
}
