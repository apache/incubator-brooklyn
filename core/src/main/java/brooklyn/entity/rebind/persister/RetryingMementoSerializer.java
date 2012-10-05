package brooklyn.entity.rebind.persister;

import static com.google.common.base.Preconditions.checkNotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RetryingMementoSerializer<T> implements MementoSerializer<T> {
    
    private static final Logger LOG = LoggerFactory.getLogger(RetryingMementoSerializer.class);
    
    private final MementoSerializer<T> delegate;
    private final int maxAttempts;
    
    public RetryingMementoSerializer(MementoSerializer<T> delegate, int maxAttempts) {
        this.delegate = checkNotNull(delegate, "delegate");
        this.maxAttempts = maxAttempts;
        if (maxAttempts < 1) throw new IllegalArgumentException("Max attempts must be at least 1, but was "+maxAttempts);
    }
    
    public String toString(T memento) {
        RuntimeException lastException = null;
        int attempt = 0;
        do {
            attempt++;
            try {
                return delegate.toString(memento);
            } catch (RuntimeException e) {
                LOG.warn("Error serializing memento (attempt "+attempt+" of "+maxAttempts+") for "+memento, e);
                lastException = e;
            }
        } while (attempt < maxAttempts);
        
        throw lastException;
    }
    
    public T fromString(String string) {
        RuntimeException lastException = null;
        int attempt = 0;
        do {
            attempt++;
            try {
                return delegate.fromString(string);
            } catch (RuntimeException e) {
                LOG.warn("Error deserializing memento (attempt "+attempt+" of "+maxAttempts+")", e);
                lastException = e;
            }
        } while (attempt < maxAttempts);
        
        throw lastException;
    }
}
