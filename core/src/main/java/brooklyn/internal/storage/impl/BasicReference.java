package brooklyn.internal.storage.impl;

import java.util.concurrent.atomic.AtomicReference;

import brooklyn.internal.storage.Reference;

public class BasicReference<T> implements Reference<T >{

    private final AtomicReference<T> ref = new AtomicReference<T>();
    
    public BasicReference() {
    }
    
    public BasicReference(T val) {
        set(val);
    }
    
    @Override
    public T get() {
        return ref.get();
    }

    @Override
    public T set(T val) {
        return ref.getAndSet(val);
    }
}
