package brooklyn.test.entity;

import java.util.Collection;
import java.util.concurrent.CountDownLatch;

import brooklyn.location.Location;

import com.google.common.base.Throwables;

/**
 * Mock entity that blocks on startup via the {@link CountDownLatch} argument.
 */
public class BlockingEntityImpl extends TestEntityImpl implements BlockingEntity {
    
    public BlockingEntityImpl() {
    }
    
    @Override
    public void start(Collection<? extends Location> locs) {
        try {
            if (getConfig(EXECUTING_STARTUP_NOTIFICATION_LATCH) != null) getConfig(EXECUTING_STARTUP_NOTIFICATION_LATCH).countDown();
            if (getConfig(STARTUP_LATCH) != null) getConfig(STARTUP_LATCH).await();
            super.start(locs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw Throwables.propagate(e);
        }
    }
    
    @Override
    public void stop() {
        try {
            if (getConfig(EXECUTING_SHUTDOWN_NOTIFICATION_LATCH) != null) getConfig(EXECUTING_SHUTDOWN_NOTIFICATION_LATCH).countDown();
            if (getConfig(SHUTDOWN_LATCH) != null) getConfig(SHUTDOWN_LATCH).await();
            super.stop();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw Throwables.propagate(e);
        }
    }
}
