package brooklyn.policy.loadbalancing;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import com.google.common.collect.Lists;

import brooklyn.location.Location;

public class MockWorkerPool implements BalanceableClusterModel<String, String> {
    
    public static class ContainerSpec {
        public final String name;
        public final double lowThreshold;
        public final double highThreshold;
        public ContainerSpec(String name, Number lowThreshold, Number highThreshold) {
            this.name = name;
            this.lowThreshold = lowThreshold.doubleValue();
            this.highThreshold = highThreshold.doubleValue();
            // TODO: location
        }
    }
    
    public static class ItemSpec {
        public final String name;
        public final String parentContainer;
        public final double currentWorkrate;
        public ItemSpec(String name, String parentContainer, Number currentWorkrate) {
            this.name = name;
            this.parentContainer = parentContainer;
            this.currentWorkrate = currentWorkrate.doubleValue();
        }
    }
    
    public static List<ContainerSpec> containers(Object... keysAndValues) {
        List<ContainerSpec> result = new LinkedList<ContainerSpec>();
        Iterator<Object> kv = Lists.newArrayList(keysAndValues).iterator();
        while (kv.hasNext())
            result.add(new ContainerSpec((String) kv.next(), (Number) kv.next(), (Number) kv.next()));
        return result;
    }
    
    public static List<ItemSpec> items(Object... keysAndValues) {
        List<ItemSpec> result = new LinkedList<ItemSpec>();
        Iterator<Object> kv = Lists.newArrayList(keysAndValues).iterator();
        while (kv.hasNext())
            result.add(new ItemSpec((String) kv.next(), (String) kv.next(), (Number) kv.next()));
        return result;
    }
    
    
    private Set<String> containers = new LinkedHashSet<String>();
    private Map<String, Double> containerToLowThreshold = new LinkedHashMap<String, Double>();
    private Map<String, Double> containerToHighThreshold = new LinkedHashMap<String, Double>();
    private Map<String, String> itemToContainer = new LinkedHashMap<String, String>();
    private Map<String, Double> itemToWorkrate = new LinkedHashMap<String, Double>();
    
    
    public MockWorkerPool(List<ContainerSpec> containers, List<ItemSpec> items) {
        for (ContainerSpec container : containers) {
            this.containers.add(container.name);
            this.containerToLowThreshold.put(container.name, container.lowThreshold);
            this.containerToHighThreshold.put(container.name, container.highThreshold);
        }
        for (ItemSpec item : items) {
            this.itemToContainer.put(item.name, item.parentContainer);
            this.itemToWorkrate.put(item.name, item.currentWorkrate);
        }
    }
    
    
    // Additional methods for tests.
    
    public String getParentContainer(String item) {
        return itemToContainer.get(item);
    }
    
    public Set<String> getItemsForContainer(String node) {
        Set<String> result = new LinkedHashSet<String>();
        for (Entry<String, String> entry : itemToContainer.entrySet()) {
            if (node.equals(entry.getValue()))
                result.add(entry.getKey());
        }
        return result;
    }
    
    public Double getItemWorkrate(String item) {
        return itemToWorkrate.get(item);
    }
    
    public void dumpItemDistribution() {
        for (String container : getPoolContents()) {
            System.out.println("Container '"+container+"'");
            for (String item : getItemsForContainer(container)) {
                Double workrate = getItemWorkrate(item);
                System.out.println("    Item '"+item+"'   ("+workrate+")");
            }
        }
    }
    
    
    // Provider methods.
    
    @Override public String getPoolName() { return "MockWorkrateProvider"; }
    @Override public int getPoolSize() { return containers.size(); }
    @Override public Set<String> getPoolContents() { return containers; }
    @Override public String getName(String container) { return container; }
    @Override public Location getLocation(String container) { return null; } // TODO?
    @Override public double getLowThreshold(String container) { return containerToLowThreshold.get(container); }
    @Override public double getHighThreshold(String container) { return containerToHighThreshold.get(container); }
    @Override public double getTotalWorkrate(String container) {
        double totalWorkrate = 0;
        for (String item : getItemsForContainer(container)) {
            Double workrate = itemToWorkrate.get(item);
            if (workrate != null)
                totalWorkrate += Math.abs(workrate);
        }
        return totalWorkrate;
    }
    
    @Override public Map<String, Double> getContainerWorkrates() {
        Map<String, Double> result = new LinkedHashMap<String, Double>();
        for (String node : containers)
            result.put(node, getTotalWorkrate(node));
        return result;
    }
    
    @Override public Map<String, Double> getItemWorkrates(String node) {
        Map<String, Double> result = new LinkedHashMap<String, Double>();
        for (String item : getItemsForContainer(node))
            result.put(item, itemToWorkrate.get(item));
        return result;
    }
    
    @Override public boolean isItemMoveable(String item) {
        return true; // TODO?
    }
    
    @Override public boolean isItemAllowedIn(String item, Location location) {
        return true; // TODO?
    }
    
    @Override public void moveItem(String item, String oldNode, String newNode) {
        assert(itemToContainer.get(item).equals(oldNode));
        itemToContainer.put(item, newNode);
    }
    
    
    // Mutators.
    
    @Override
    public void addContainer(String newContainer, double lowThreshold, double highThreshold) {
        containers.add(newContainer);
        containerToLowThreshold.put(newContainer, lowThreshold);
        containerToHighThreshold.put(newContainer, highThreshold);
    }
    
    @Override
    public void removeContainer(String oldContainer) {
        containers.remove(oldContainer);
        containerToLowThreshold.remove(oldContainer);
        containerToHighThreshold.remove(oldContainer);
        // TODO: assert no orphaned items
    }
    
    @Override
    public void addItem(String item, String parentContainer) {
        addItem(item, parentContainer, null);
    }
    
    @Override
    public void addItem(String item, String parentContainer, Number currentWorkrate) {
        itemToContainer.put(item, parentContainer);
        if (currentWorkrate != null)
            itemToWorkrate.put(item, currentWorkrate.doubleValue());
    }
    
    @Override
    public void removeItem(String item) {
        itemToContainer.remove(item);
        itemToWorkrate.remove(item);
    }
    
    @Override
    public void updateItemWorkrate(String item, double newValue) {
        itemToWorkrate.put(item, newValue);
    }
    
}
