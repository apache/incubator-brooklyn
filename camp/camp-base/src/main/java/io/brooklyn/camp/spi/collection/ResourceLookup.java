package io.brooklyn.camp.spi.collection;

import io.brooklyn.camp.spi.AbstractResource;

import java.util.Collections;
import java.util.List;
import java.util.NoSuchElementException;

public interface ResourceLookup<T extends AbstractResource> {

    public abstract T get(String id);
    
    public abstract List<ResolvableLink<T>> links();
    
    public abstract boolean isEmpty();

    public static class EmptyResourceLookup<T extends AbstractResource> implements ResourceLookup<T> {
        public T get(String id) {
            throw new NoSuchElementException("no resource: "+id);
        }
        public List<ResolvableLink<T>> links() {
            return Collections.emptyList();
        }
        public boolean isEmpty() {
            return links().isEmpty();
        }
    }
    
}
