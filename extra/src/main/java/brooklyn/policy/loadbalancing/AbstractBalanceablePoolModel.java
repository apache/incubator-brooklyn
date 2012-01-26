package brooklyn.policy.loadbalancing;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import brooklyn.location.Location;

public class AbstractBalanceablePoolModel<ContainerType, ItemType> implements BalanceablePoolModel<ContainerType, ItemType> {
    
    private final String name;
    private Set<ContainerType> containers = new LinkedHashSet<ContainerType>();
    private Map<ContainerType, Double> containerToLowThreshold = new LinkedHashMap<ContainerType, Double>();
    private Map<ContainerType, Double> containerToHighThreshold = new LinkedHashMap<ContainerType, Double>();
    private Map<ItemType, ContainerType> itemToContainer = new LinkedHashMap<ItemType, ContainerType>();
    private Map<ItemType, Double> itemToWorkrate = new LinkedHashMap<ItemType, Double>();
    
    
    public AbstractBalanceablePoolModel(String name) {
        this.name = name;
    }
    
    public ContainerType getParentContainer(ItemType item) {
        return itemToContainer.get(item);
    }
    
    public Set<ItemType> getItemsForContainer(ContainerType node) {
        Set<ItemType> result = new LinkedHashSet<ItemType>();
        for (Entry<ItemType, ContainerType> entry : itemToContainer.entrySet()) {
            if (node.equals(entry.getValue()))
                result.add(entry.getKey());
        }
        return result;
    }
    
    public Double getItemWorkrate(ItemType item) {
        return itemToWorkrate.get(item);
    }
    
    public void dumpItemDistribution() {
        for (ContainerType container : getPoolContents()) {
            System.out.println("Container '"+container+"'");
            for (ItemType item : getItemsForContainer(container)) {
                Double workrate = getItemWorkrate(item);
                System.out.println("    Item '"+item+"'   ("+workrate+")");
            }
        }
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
    
    @Override public void moveItem(ItemType item, ContainerType oldNode, ContainerType newNode) {
        assert(itemToContainer.get(item).equals(oldNode));
        itemToContainer.put(item, newNode);
    }
    
    
    // Mutators.
    
    @Override
    public void addContainer(ContainerType newContainer, double lowThreshold, double highThreshold) {
        containers.add(newContainer);
        containerToLowThreshold.put(newContainer, lowThreshold);
        containerToHighThreshold.put(newContainer, highThreshold);
    }
    
    @Override
    public void removeContainer(ContainerType oldContainer) {
        containers.remove(oldContainer);
        containerToLowThreshold.remove(oldContainer);
        containerToHighThreshold.remove(oldContainer);
        // TODO: assert no orphaned items
    }
    
    @Override
    public void addItem(ItemType item, ContainerType parentContainer) {
        addItem(item, parentContainer, null);
    }
    
    @Override
    public void addItem(ItemType item, ContainerType parentContainer, Number currentWorkrate) {
        itemToContainer.put(item, parentContainer);
        if (currentWorkrate != null)
            itemToWorkrate.put(item, currentWorkrate.doubleValue());
    }
    
    @Override
    public void removeItem(ItemType item) {
        itemToContainer.remove(item);
        itemToWorkrate.remove(item);
    }
    
    @Override
    public void updateItemWorkrate(ItemType item, double newValue) {
        itemToWorkrate.put(item, newValue);
    }
    
}
