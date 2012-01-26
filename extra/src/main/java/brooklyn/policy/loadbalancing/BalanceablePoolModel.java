package brooklyn.policy.loadbalancing;

import java.util.Map;
import java.util.Set;

import brooklyn.location.Location;

/**
 * Captures the state of a balanceable cluster of containers and all their constituent items, including workrates,
 * for consumption by a BalancingStrategy.
 * 
 * @author splodge
 */
public interface BalanceablePoolModel<ContainerType, ItemType> {
    
    // Attributes of the pool.
    public String getName();
    public int getPoolSize();
    public Set<ContainerType> getPoolContents();
    
    
    // Attributes of containers and items.
    public String getName(ContainerType container);
    public Location getLocation(ContainerType container);
    public double getLowThreshold(ContainerType container);
    public double getHighThreshold(ContainerType container);
    public double getTotalWorkrate(ContainerType container); // -1 for not known / invalid
    public Map<ContainerType, Double> getContainerWorkrates(); // contains -1 for items which are unknown
    /** contains -1 instead of actual item workrate, for items which cannot be moved */
    // @Nullable("null if the node is prevented from reporting and/or being adjusted, or has no data yet")
    public Map<ItemType, Double> getItemWorkrates(ContainerType container);
    public boolean isItemMoveable(ItemType item);
    public boolean isItemAllowedIn(ItemType item, Location location);
    
    
    // Mutators
    public void addContainer(ContainerType newContainer, double lowThreshold, double highThreshold);
    public void removeContainer(ContainerType oldContainer);
    public void addItem(ItemType item, ContainerType parentContainer);
    public void addItem(ItemType item, ContainerType parentContainer, Number currentWorkrate);
    public void removeItem(ItemType item);
    public void updateItemWorkrate(ItemType item, double newValue);
    public void moveItem(ItemType item, ContainerType originContainer, ContainerType targetContainer);
    
}
