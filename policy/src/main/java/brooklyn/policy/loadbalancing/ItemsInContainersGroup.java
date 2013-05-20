package brooklyn.policy.loadbalancing;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.Group;
import brooklyn.entity.basic.DynamicGroup;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.util.flags.SetFromFlag;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;

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
@ImplementedBy(ItemsInContainersGroupImpl.class)
public interface ItemsInContainersGroup extends DynamicGroup {

    @SetFromFlag("itemFilter")
    public static final ConfigKey<Predicate<? super Entity>> ITEM_FILTER = new BasicConfigKey(
            Predicate.class, "itemsInContainerGroup.itemFilter", "Filter for which items within the containers will automatically be in group", Predicates.alwaysTrue());

    public void setContainers(Group containerGroup);
}
