package io.brooklyn.camp.spi.collection;

import io.brooklyn.camp.spi.AbstractResource;

public abstract class AbstractResourceLookup<T extends AbstractResource> implements ResourceLookup<T> {

    /** convenience for concrete subclasses */
    protected ResolvableLink<T> newLink(String id, String name) {
        return new ResolvableLink<T>(id, name, this);
    }

    @Override
    public boolean isEmpty() {
        return links().isEmpty();
    }

}
