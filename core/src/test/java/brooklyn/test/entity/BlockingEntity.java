package brooklyn.test.entity;

import java.util.concurrent.CountDownLatch;

import brooklyn.config.ConfigKey;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.util.flags.SetFromFlag;

/**
 * Mock entity that blocks on startup via the {@link CountDownLatch} argument.
 */
@ImplementedBy(BlockingEntityImpl.class)
public interface BlockingEntity extends TestEntity {
    
    @SetFromFlag("startupLatch")
    public static final ConfigKey<CountDownLatch> STARTUP_LATCH = new BasicConfigKey<CountDownLatch>(CountDownLatch.class, "test.startupLatch", "Latch that blocks startup");
    
    @SetFromFlag("shutdownLatch")
    public static final ConfigKey<CountDownLatch> SHUTDOWN_LATCH = new BasicConfigKey<CountDownLatch>(CountDownLatch.class, "test.shutdownLatch", "Latch that blocks shutdown");
    
    @SetFromFlag("executingStartupNotificationLatch")
    public static final ConfigKey<CountDownLatch> EXECUTING_STARTUP_NOTIFICATION_LATCH = new BasicConfigKey<CountDownLatch>(CountDownLatch.class, "test.executingStartupNotificationLatch", "");
    
    @SetFromFlag("executingShutdownNotificationLatch")
    public static final ConfigKey<CountDownLatch> EXECUTING_SHUTDOWN_NOTIFICATION_LATCH = new BasicConfigKey<CountDownLatch>(CountDownLatch.class, "test.executingShutdownNotificationLatch", "");
}
