package brooklyn.util.pool;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.util.text.Identifiers;

import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Objects;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Supplier;
import com.google.common.collect.Lists;
import com.google.common.io.Closeables;

public class BasicPool<T> implements Pool<T> {

    // TODO Implement expiry of pooled resources

    protected static final Logger LOG = LoggerFactory.getLogger(BasicPool.class);

    public static <T> Builder<T> builder() {
        return new Builder<T>();
    }
    
    public static class Builder<T> {
        private String name;
        private Supplier<? extends T> supplier;
        private Predicate<? super T> viabilityChecker = Predicates.alwaysTrue();
        private Function<? super T, ?> closer = Functions.identity();
        
        public Builder<T> name(String val) {
            this.name = val;
            return this;
        }
        
        public Builder<T> supplier(Supplier<? extends T> val) {
            this.supplier = val;
            return this;
        }
        
        public Builder<T> viabilityChecker(Predicate<? super T> val) {
            this.viabilityChecker = val;
            return this;
        }
        
        public Builder<T> closer(Function<? super T, ?> val) {
            this.closer = val;
            return this;
        }
        
        public BasicPool<T> build() {
            return new BasicPool<T>(this);
        }
    }
    
    private final String name;
    private final Supplier<? extends T> supplier;
    private final Predicate<? super T> viabilityChecker;
    private Function<? super T, ?> closer;
    private final Deque<T> pool = Lists.newLinkedList();
    private AtomicBoolean closed = new AtomicBoolean(false);
    
    private AtomicInteger currentLeasedCount = new AtomicInteger(0);
    private AtomicInteger totalLeasedCount = new AtomicInteger(0);
    private AtomicInteger totalCreatedCount = new AtomicInteger(0);
    private AtomicInteger totalClosedCount = new AtomicInteger(0);
    
    private BasicPool(Builder<T> builder) {
        this.name = (builder.name != null) ? "Pool("+builder.name+")" : "Pool-"+Identifiers.makeRandomId(8);
        this.supplier = checkNotNull(builder.supplier, "supplier");
        this.viabilityChecker = checkNotNull(builder.viabilityChecker, "viabilityChecker");
        this.closer = checkNotNull(builder.closer, closer);
    }
    
    @Override
    public String toString() {
        return Objects.toStringHelper(this).add("name", name).toString();
    }
    
    @Override
    public Lease<T> leaseObject() {
        totalLeasedCount.incrementAndGet();
        T existing;
        do {
            existing = null;
            synchronized (pool) {
                if (closed.get()) {
                    throw new IllegalStateException("Pool closed for "+this);
                }
                if (pool.size() > 0) {
                    existing = pool.removeLast();
                }
            }
            
            if (existing != null) {
                if (viabilityChecker.apply(existing)) {
                    currentLeasedCount.incrementAndGet();
                    if (LOG.isTraceEnabled()) LOG.trace("{} reusing existing pool entry {} ({})", new Object[] {this, existing, getMetrics()});
                    return new BasicLease(existing);
                } else {
                    totalClosedCount.incrementAndGet();
                    if (LOG.isDebugEnabled()) LOG.debug("{} not reusing entry {} as no longer viable; discarding and trying again", this, existing);
                    closer.apply(existing);
                }
            }
        } while (existing != null);
        
        T result = supplier.get();
        totalCreatedCount.incrementAndGet();
        currentLeasedCount.incrementAndGet();
        if (LOG.isDebugEnabled()) LOG.debug("{} acquired and returning new entry {} ({})", new Object[] {this, result, getMetrics()});
        return new BasicLease(result);
    }

    @Override
    public <R> R exec(Function<? super T,R> receiver) {
        Lease<T> lease = leaseObject();
        try {
            if (LOG.isTraceEnabled()) LOG.trace("{} executing {} with leasee {}", new Object[] {this, receiver, lease.leasedObject()});
            return receiver.apply(lease.leasedObject());
        } finally {
            Closeables.closeQuietly(lease);
        }
    }
    
    @Override
    public void close() throws IOException {
        synchronized (pool) {
            if (LOG.isDebugEnabled()) LOG.debug("{} closing, with {} resources ({})", new Object[] {this, pool.size(), getMetrics()});
            closed.set(true);
            for (T resource : pool) {
                totalClosedCount.incrementAndGet();
                closer.apply(resource);
            }
            pool.clear();
        }

    }
    
    private void returnLeasee(T val) {
        currentLeasedCount.decrementAndGet();
        synchronized (pool) {
            if (closed.get()) {
                totalClosedCount.incrementAndGet();
                if (LOG.isDebugEnabled()) LOG.debug("{} closing returned leasee {}, because pool closed ({})", new Object[] {this, val, getMetrics()});
                closer.apply(val);
            } else {
                if (LOG.isTraceEnabled()) LOG.trace("{} adding {} back into pool ({})", new Object[] {this, val, getMetrics()});
                pool.addLast(val);
            }
        }
    }
    
    private String getMetrics() {
        return String.format("currentLeased=%s; totalLeased=%s; totalCreated=%s; totalClosed=%s", 
                currentLeasedCount, totalLeasedCount, totalCreatedCount, totalClosedCount);

    }
    private class BasicLease implements Lease<T> {
        private final T val;

        BasicLease(T val) {
            this.val = val;
        }
        
        @Override
        public T leasedObject() {
            return val;
        }

        @Override
        public void close() {
            BasicPool.this.returnLeasee(val);
        }
    }
}
