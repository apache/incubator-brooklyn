package brooklyn.util.pool;

import java.io.Closeable;
import java.io.IOException;

public interface Lease<T> extends Closeable {

    T leasedObject();
    
    void close() throws IOException;
}
