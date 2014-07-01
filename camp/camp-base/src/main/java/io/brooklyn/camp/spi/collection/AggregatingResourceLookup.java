package io.brooklyn.camp.spi.collection;

import io.brooklyn.camp.spi.AbstractResource;

import java.util.ArrayList;
import java.util.List;

public class AggregatingResourceLookup<T extends AbstractResource> extends AbstractResourceLookup<T> {

    List<ResourceLookup<T>> targets = new ArrayList<ResourceLookup<T>>();
    
    public static <T extends AbstractResource> AggregatingResourceLookup<T> of(ResourceLookup<T> ...targets) {
        AggregatingResourceLookup<T> result = new AggregatingResourceLookup<T>();
        for (ResourceLookup<T> item: targets) result.targets.add(item);
        return result;
    }
    
    public static <T extends AbstractResource> AggregatingResourceLookup<T> of(Iterable<ResourceLookup<T>> targets) {
        AggregatingResourceLookup<T> result = new AggregatingResourceLookup<T>();
        for (ResourceLookup<T> item: targets) result.targets.add(item);
        return result;        
    }

    public T get(String id) {
        for (ResourceLookup<T> item: targets) {
            T result = item.get(id);
            if (result!=null) return result;
        }
        return null;
    }

    public List<ResolvableLink<T>> links() {
        List<ResolvableLink<T>> result = new ArrayList<ResolvableLink<T>>();
        for (ResourceLookup<T> item: targets) result.addAll(item.links());
        return result;
    }
    
}
