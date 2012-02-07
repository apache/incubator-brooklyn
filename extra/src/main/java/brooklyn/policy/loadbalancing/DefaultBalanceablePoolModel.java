package brooklyn.policy.loadbalancing;

import static com.google.common.base.Preconditions.checkState;

import java.io.PrintStream;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import brooklyn.location.Location;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;

public class DefaultBalanceablePoolModel<ContainerType, ItemType> implements BalanceablePoolModel<ContainerType, ItemType> {
    
    /*
     * Performance comments.
     *  - Used hprof with LoadBalancingPolicySoakTest.testLoadBalancingManyManyItemsTest (1000 items)
     *  - Prior to adding containerToItems, it created a new set by iterating over all items.
     *    This was the biggest percentage of any brooklyn code.
     *    Hence it's worth duplicating the values, keyed by item and keyed by container.
     */
    
    private final String name;
    private final Set<ContainerType> containers = new LinkedHashSet<ContainerType>();
    private final Map<ContainerType, Double> containerToLowThreshold = new LinkedHashMap<ContainerType, Double>();
    private final Map<ContainerType, Double> containerToHighThreshold = new LinkedHashMap<ContainerType, Double>();
    private final Map<ItemType, ContainerType> itemToContainer = new LinkedHashMap<ItemType, ContainerType>();
    private final SetMultimap<ContainerType, ItemType> containerToItems = HashMultimap.create();
    private final Map<ItemType, Double> itemToWorkrate = new LinkedHashMap<ItemType, Double>();
    
    
    public DefaultBalanceablePoolModel(String name) {
        this.name = name;
    }
    
    public ContainerType getParentContainer(ItemType item) {
        return itemToContainer.get(item);
    }
    
    public Set<ItemType> getItemsForContainer(ContainerType node) {
        Set<ItemType> result = containerToItems.get(node);
        return (result != null) ? result : Collections.<ItemType>emptySet();
    }
    
    public Double getItemWorkrate(ItemType item) {
        return itemToWorkrate.get(item);
    }
    
    
    // Provider methods.
    
    @Override public String getName() { return name; }
    @Override public int getPoolSize() { return containers.size(); }
    @Override public Set<ContainerType> getPoolContents() { return containers; }
    @Override public String getName(ContainerType container) { return container.toString(); } // TODO: delete?
    @Override public Location getLocation(ContainerType container) { return null; } // TODO?
    @Override public double getLowThreshold(ContainerType container) { return containerToLowThreshold.get(container); }
    @Override public double getHighThreshold(ContainerType container) { return containerToHighThreshold.get(container); }
    @Override public double getTotalWorkrate(ContainerType container) {
        double totalWorkrate = 0;
        for (ItemType item : getItemsForContainer(container)) {
            Double workrate = itemToWorkrate.get(item);
            if (workrate != null)
                totalWorkrate += Math.abs(workrate);
        }
        return totalWorkrate;
    }
    
    @Override public Map<ContainerType, Double> getContainerWorkrates() {
        Map<ContainerType, Double> result = new LinkedHashMap<ContainerType, Double>();
        for (ContainerType node : containers)
            result.put(node, getTotalWorkrate(node));
        return result;
    }
    
    @Override public Map<ItemType, Double> getItemWorkrates(ContainerType node) {
        Map<ItemType, Double> result = new LinkedHashMap<ItemType, Double>();
        for (ItemType item : getItemsForContainer(node))
            result.put(item, itemToWorkrate.get(item));
        return result;
    }
    
    @Override public boolean isItemMoveable(ItemType item) {
        return true; // TODO?
    }
    
    @Override public boolean isItemAllowedIn(ItemType item, Location location) {
        return true; // TODO?
    }

    
    // Mutators.

    @Override
    public void onItemMoved(ItemType item, ContainerType newNode) {
        checkState(itemToContainer.containsKey(item), "Unknown item "+item);
        ContainerType oldNode = itemToContainer.put(item, newNode);
        if (oldNode != null) containerToItems.remove(oldNode, item);
        containerToItems.put(newNode, item);
    }

    @Override
    public void onContainerAdded(ContainerType newContainer, double lowThreshold, double highThreshold) {
        containers.add(newContainer);
        containerToLowThreshold.put(newContainer, lowThreshold);
        containerToHighThreshold.put(newContainer, highThreshold);
    }
    
    @Override
    public void onContainerRemoved(ContainerType oldContainer) {
        containers.remove(oldContainer);
        containerToLowThreshold.remove(oldContainer);
        containerToHighThreshold.remove(oldContainer);
        // TODO: assert no orphaned items
    }
    
    @Override
    public void onItemAdded(ItemType item, ContainerType parentContainer) {
        onItemAdded(item, parentContainer, null);
    }
    
    @Override
    public void onItemAdded(ItemType item, ContainerType parentContainer, Number currentWorkrate) {
        itemToContainer.put(item, parentContainer);
        containerToItems.put(parentContainer, item);
        if (currentWorkrate != null)
            itemToWorkrate.put(item, currentWorkrate.doubleValue());
    }
    
    @Override
    public void onItemRemoved(ItemType item) {
        ContainerType oldNode = itemToContainer.remove(item);
        if (oldNode != null) containerToItems.remove(oldNode, item);
        itemToWorkrate.remove(item);
    }
    
    @Override
    public void onItemWorkrateUpdated(ItemType item, double newValue) {
        itemToWorkrate.put(item, newValue);
    }
    

    // Mutators that change the real world
    
    @Override public void moveItem(ItemType item, ContainerType oldNode, ContainerType newNode) {
        // TODO no-op; should this be abstract?
    }
    
    
    // Additional methods for tests.
    
    @VisibleForTesting
    public void dumpItemDistribution() {
        dumpItemDistribution(System.out);
    }
    
    @VisibleForTesting
    public void dumpItemDistribution(PrintStream out) {
        for (ContainerType container : getPoolContents()) {
            out.println("Container '"+container+"': ");
            for (ItemType item : getItemsForContainer(container)) {
                Double workrate = getItemWorkrate(item);
                out.println("\t"+"Item '"+item+"' ("+workrate+")");
            }
        }
        out.flush();
    }
}
