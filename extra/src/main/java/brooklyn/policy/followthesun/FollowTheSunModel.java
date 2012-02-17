package brooklyn.policy.followthesun;

import java.util.Map;
import java.util.Set;

import brooklyn.location.Location;

/**
 * Captures the state of items, containers and locations for the purpose of moving items around
 * to minimise latency. For consumption by a {@link FollowTheSunStrategy}.
 */
public interface FollowTheSunModel<LocationType, ContainerType, ItemType> {

    // Attributes of the pool.
    public String getName();
    
    // Attributes of containers and items.
    public String getName(ItemType item);
    public Set<ItemType> getItems();
    public Map<ItemType, Map<LocationType, Double>> getDirectSendsToItemByLocation();
    public LocationType getItemLocation(ItemType item);
    public ContainerType getItemContainer(ItemType item);
    public LocationType getContainerLocation(ContainerType container);
    public boolean hasActiveMigration(ItemType item);
    public Set<ContainerType> getAvailableContainersFor(ItemType item, LocationType location);
    public boolean isItemMoveable(ItemType item);
    public boolean isItemAllowedIn(ItemType item, LocationType location);
    
    // Mutators for keeping the model in-sync with the observed world
    public void onContainerAdded(ContainerType container, LocationType location);
    public void onContainerRemoved(ContainerType container);
    public void onContainerLocationUpdated(ContainerType container, LocationType location);

    public void onItemAdded(ItemType item, ContainerType parentContainer);
    public void onItemRemoved(ItemType item);
    public void onItemUsageUpdated(ItemType item, Map<? extends ItemType, Double> newValues);
    public void onItemMoved(ItemType item, ContainerType newContainer);
    
    // Mutator for effecting the real world
    public void moveItem(ItemType item, ContainerType targetContainer);
}
