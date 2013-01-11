package brooklyn.test.entity;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import brooklyn.location.Location;
import brooklyn.util.MutableMap;

import com.google.common.base.Throwables;

/**
 * Mock entity that blocks on startup via the {@link CountDownLatch} argument.
 */
public class BlockingEntity extends TestEntityImpl {
    
    public static class Builder {
        private final Map props;
        private CountDownLatch startupLatch;
        private CountDownLatch shutdownLatch;
        private CountDownLatch executingStartupNotificationLatch;
        private CountDownLatch executingShutdownNotificationLatch;
        
        public Builder() {
            this(MutableMap.of());
        }
        public Builder(Map props) {
            this.props = props;
        }
        public Builder startupLatch(CountDownLatch val) {
            startupLatch = val; return this;
        }
        public Builder shutdownLatch(CountDownLatch val) {
            shutdownLatch = val; return this;
        }
        public Builder executingStartupNotificationLatch(CountDownLatch val) {
            executingStartupNotificationLatch = val; return this;
        }
        public Builder executingShutdownNotificationLatch(CountDownLatch val) {
            executingShutdownNotificationLatch = val; return this;
        }
        public BlockingEntity build() {
            return new BlockingEntity(this);
        }
    }
    
    final CountDownLatch startupLatch;
    final CountDownLatch shutdownLatch;
    final CountDownLatch executingStartupNotificationLatch;
    final CountDownLatch executingShutdownNotificationLatch;
    
    public BlockingEntity(CountDownLatch startupLatch) {
        this(MutableMap.of(), startupLatch);
    }
    public BlockingEntity(Map props, CountDownLatch startupLatch) {
        this(new Builder(props).startupLatch(startupLatch));
    }
    
    public BlockingEntity(Builder builder) {
        super(builder.props);
        this.startupLatch = builder.startupLatch;
        this.shutdownLatch = builder.shutdownLatch;
        this.executingStartupNotificationLatch = builder.executingStartupNotificationLatch;
        this.executingShutdownNotificationLatch = builder.executingShutdownNotificationLatch;
    }

    @Override
    public void start(Collection<? extends Location> locs) {
        try {
            if (executingStartupNotificationLatch != null) executingStartupNotificationLatch.countDown();
            if (startupLatch != null) startupLatch.await();
            super.start(locs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw Throwables.propagate(e);
        }
    }
    
    @Override
    public void stop() {
        try {
            if (executingShutdownNotificationLatch != null) executingShutdownNotificationLatch.countDown();
            if (shutdownLatch != null) shutdownLatch.await();
            super.stop();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw Throwables.propagate(e);
        }
    }
}
