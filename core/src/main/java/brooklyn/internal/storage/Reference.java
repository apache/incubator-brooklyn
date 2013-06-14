package brooklyn.internal.storage;

/**
 * A reference to a value, backed by the strage-medium.
 * 
 * @see BrooklynStorage#getReference(String)
 * 
 * @author aled
 */
public interface Reference<T> {

    // TODO We can add compareAndSet(T,T) as and when required
    
    T get();
    
    T set(T val);
    
    /**
     * @return true if the value is null; false otherwise.
     */
    boolean isNull();
    
    /**
     * Sets the value back to null. Similar to {@code set(null)}.
     */
    void clear();
    
    /**
     * @return true if the value equals the given parameter; false otherwise
     */
    boolean contains(Object other);
}
