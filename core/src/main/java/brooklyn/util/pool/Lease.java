package brooklyn.util.pool;

public interface Lease<T> {

    T leasedObject();
    
    void close();
}
