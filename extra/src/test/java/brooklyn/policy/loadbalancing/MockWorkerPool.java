package brooklyn.policy.loadbalancing;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import com.google.common.collect.Lists;

public class MockWorkerPool extends DefaultBalanceablePoolModel<String, String> {
    
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
    
    
    public MockWorkerPool(List<ContainerSpec> containers, List<ItemSpec> items) {
        super("Mock pool");
        for (ContainerSpec container : containers)
            onContainerAdded(container.name, container.lowThreshold, container.highThreshold);
        for (ItemSpec item : items)
            onItemAdded(item.name, item.parentContainer, item.currentWorkrate);
    }
    
    
    // Additional methods for tests.
    
    public void dumpItemDistribution() {
        for (String container : getPoolContents()) {
            System.out.println("Container '"+container+"'");
            for (String item : getItemsForContainer(container)) {
                Double workrate = getItemWorkrate(item);
                System.out.println("    Item '"+item+"'   ("+workrate+")");
            }
        }
    }
    
}
