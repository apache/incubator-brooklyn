package brooklyn.internal.storage.impl;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Map;

import brooklyn.internal.storage.Reference;

import com.google.common.base.Objects;

class BackedReference<T> implements Reference<T> {
    private final Map<String,? super T> backingMap;
    private final String key;
    
    BackedReference(Map<String,? super T> backingMap, String key) {
        this.backingMap = checkNotNull(backingMap, "backingMap");
        this.key = key;
    }
    
    @Override
    public T get() {
        // For happens-before (for different threads calling get and set), relies on 
        // underlying map (e.g. from datagrid) having some synchronization
        return (T) backingMap.get(key);
    }
    
    @Override
    public T set(T val) {
        if (val == null) {
            return (T) backingMap.remove(key);
        } else {
            return (T) backingMap.put(key, val);
        }
    }
    
    @Override
    public String toString() {
        return ""+get();
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
}
