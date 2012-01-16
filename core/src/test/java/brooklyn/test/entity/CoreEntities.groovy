package brooklyn.test.entity

import java.util.Collection
import java.util.Map
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.Entity
import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.basic.AbstractEntity
import brooklyn.entity.trait.Startable
import brooklyn.event.Sensor
import brooklyn.event.SensorEventListener
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.event.basic.BasicConfigKey
import brooklyn.event.basic.ListConfigKey
import brooklyn.event.basic.MapConfigKey
import brooklyn.location.Location
import brooklyn.management.SubscriptionHandle

/**
 * Mock application for testing.
 */
public class TestApplication extends AbstractApplication {
	protected static final Logger LOG = LoggerFactory.getLogger(TestApplication)

    public TestApplication(Map properties=[:]) {
        super(properties)
    }

    public <T> SubscriptionHandle subscribeToMembers(Entity parent, Sensor<T> sensor, SensorEventListener<T> listener) {
        subscriptionContext.subscribeToMembers(parent, sensor, listener)
    }

    @Override
    String toString() {
        return "Application["+id[-8..-1]+"]"
    }

    // TODO add more mock methods
}

/**
 * Mock entity for testing.
 */
public class TestEntity extends AbstractEntity implements Startable {
	protected static final Logger LOG = LoggerFactory.getLogger(TestEntity)

    public static final BasicConfigKey<String> CONF_NAME = [ String, "test.confName", "Configuration key, my name", "defaultval" ]
    public static final BasicConfigKey<Map> CONF_MAP_PLAIN = [ Map, "test.confMapPlain", "Configuration key that's a plain map", [:] ]
    public static final MapConfigKey<String> CONF_MAP_THING = [ String, "test.confMapThing", "Configuration key that's a map thing" ]
    public static final ListConfigKey<String> CONF_LIST_THING = [ String, "test.confListThing", "Configuration key that's a list thing" ]
    
    public static final BasicAttributeSensor<Integer> SEQUENCE = [ Integer, "test.sequence", "Test Sequence" ]
    public static final BasicAttributeSensor<String> NAME = [ String, "test.name", "Test name" ]
    
    int sequenceValue = 0
    AtomicInteger counter = new AtomicInteger(0)
    Map constructorProperties
    
    public TestEntity(Map properties=[:], Entity owner=null) {
        super(properties, owner)
        this.constructorProperties = properties
    }
    public TestEntity(Entity owner) {
        this([:], owner)
    }
    
    public synchronized int getSequenceValue() {
        sequenceValue
    }

    public synchronized void setSequenceValue(int value) {
        sequenceValue = value
        setAttribute(SEQUENCE, value)
    }

    public void start(Collection<? extends Location> locs) {
        LOG.trace "Starting {}", this
        counter.incrementAndGet();
        // FIXME: Shouldn't need to clear() the locations, but for the dirty workaround implemented in DynamicFabric
        locations.clear(); 
        locations.addAll(locs)
    }

    public void stop() { 
        LOG.trace "Stopping {}", this
        counter.decrementAndGet()
    }

    public void restart() {
        throw new UnsupportedOperationException()
    }
    
    @Override
    String toString() {
        return "Entity["+id[-8..-1]+"]"
    }
    
    // TODO add more mock methods
}

/**
 * Mock entity that blocks on startup via the {@link CountDownLatch} argument.
 */
public class BlockingEntity extends TestEntity {
    final CountDownLatch startupLatch
    
    public BlockingEntity(Map props=[:], CountDownLatch startupLatch) {
        super(props)
        this.startupLatch = startupLatch
    }

    @Override
    void start(Collection<? extends Location> locs) {
        startupLatch.await()
        super.start(locs)
    }
}
