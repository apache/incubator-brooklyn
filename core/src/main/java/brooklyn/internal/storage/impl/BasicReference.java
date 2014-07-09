package brooklyn.internal.storage.impl;

import java.util.concurrent.atomic.AtomicReference;

import brooklyn.internal.storage.Reference;

import com.google.common.base.Objects;

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

    @Override
    public boolean isNull() {
        return get() == null;
    }

    @Override
    public void clear() {
        set(null);
    }

    @Override
    public boolean contains(Object other) {
        return Objects.equal(get(), other);
    }
    
    @Override
    public String toString() {
        return ""+get();
    }
}
