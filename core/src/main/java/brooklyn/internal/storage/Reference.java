package brooklyn.internal.storage;

public interface Reference<T> {

    T get();
    
    T set(T val);
}
