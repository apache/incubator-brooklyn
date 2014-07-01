package io.brooklyn.camp.spi.collection;

import io.brooklyn.camp.spi.AbstractResource;
import io.brooklyn.camp.spi.Link;

public class ResolvableLink<T extends AbstractResource> extends Link<T> {
    
    protected final ResourceLookup<T> provider;
    
    public ResolvableLink(String id, String name, ResourceLookup<T> provider) {
        super(id, name);
        this.provider = provider;
    }

    public T resolve() {
        return provider.get(getId());
    }

}
