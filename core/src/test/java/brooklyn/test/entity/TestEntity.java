package brooklyn.test.entity;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.collections.Lists;

import brooklyn.entity.Effector;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.Description;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.entity.basic.MethodEffector;
import brooklyn.entity.basic.NamedParameter;
import brooklyn.entity.trait.Startable;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.event.basic.BasicNotificationSensor;
import brooklyn.event.basic.ListConfigKey;
import brooklyn.event.basic.MapConfigKey;
import brooklyn.location.Location;
import brooklyn.util.MutableMap;

/**
 * Mock entity for testing.
 */
public class TestEntity extends AbstractEntity implements Startable {
	protected static final Logger LOG = LoggerFactory.getLogger(TestEntity.class);

    public static final BasicConfigKey<String> CONF_NAME = new BasicConfigKey<String>(String.class, "test.confName", "Configuration key, my name", "defaultval");
    public static final BasicConfigKey<Map> CONF_MAP_PLAIN = new BasicConfigKey<Map>(Map.class, "test.confMapPlain", "Configuration key that's a plain map", MutableMap.of());
    public static final BasicConfigKey<List> CONF_LIST_PLAIN = new BasicConfigKey<List>(List.class, "test.confListPlain", "Configuration key that's a plain list", Lists.newArrayList());
    public static final MapConfigKey<String> CONF_MAP_THING = new MapConfigKey<String>(String.class, "test.confMapThing", "Configuration key that's a map thing");
    public static final ListConfigKey<String> CONF_LIST_THING = new ListConfigKey<String>(String.class, "test.confListThing", "Configuration key that's a list thing");
    
    public static final BasicAttributeSensor<Integer> SEQUENCE = new BasicAttributeSensor<Integer>(Integer.class, "test.sequence", "Test Sequence");
    public static final BasicAttributeSensor<String> NAME = new BasicAttributeSensor<String>(String.class, "test.name", "Test name");
    public static final BasicNotificationSensor<Integer> MY_NOTIF = new BasicNotificationSensor<Integer>(Integer.class, "test.myNotif", "Test notification");
    
    public static final BasicAttributeSensor<Lifecycle> SERVICE_STATE = Attributes.SERVICE_STATE;
    
    public static final Effector<Void> MY_EFFECTOR = new MethodEffector<Void>(TestEntity.class, "myEffector");
    public static final Effector<Object> IDENTITY_EFFECTOR = new MethodEffector<Object>(TestEntity.class, "identityEffector");
    
    int sequenceValue = 0;
    AtomicInteger counter = new AtomicInteger(0);
    Map constructorProperties;

    public TestEntity() {
        this(MutableMap.of(), null);
    }
    public TestEntity(Map properties) {
        this(properties, null);
    }
    public TestEntity(Entity parent) {
        this(MutableMap.of(), parent);
    }
    public TestEntity(Map properties, Entity parent) {
        super(properties, parent);
        this.constructorProperties = properties;
    }
    
    @Description("an example of a no-arg effector")
    public void myEffector() {
        if (LOG.isTraceEnabled()) LOG.trace("In myEffector for {}", this);
    }
    
    @Description("returns the arg passed in")
    public Object identityEffector(@NamedParameter("arg") @Description("val to return") Object arg) {
        if (LOG.isTraceEnabled()) LOG.trace("In identityEffector for {}", this);
        return arg;
    }
    
    public AtomicInteger getCounter() {
        return counter;
    }
    
    public int getCount() {
        return counter.get();
    }
    
    public Map getConstructorProperties() {
        return constructorProperties;
    }
    
    public synchronized int getSequenceValue() {
        return sequenceValue;
    }

    public synchronized void setSequenceValue(int value) {
        sequenceValue = value;
        setAttribute(SEQUENCE, value);
    }

    public void start(Collection<? extends Location> locs) {
        LOG.trace("Starting {}", this);
        setAttribute(SERVICE_STATE, Lifecycle.STARTING);
        counter.incrementAndGet();
        // FIXME: Shouldn't need to clear() the locations, but for the dirty workaround implemented in DynamicFabric
        getLocations().clear(); ;
        getLocations().addAll(locs);
        setAttribute(SERVICE_STATE, Lifecycle.RUNNING);
    }

    public void stop() { 
        LOG.trace("Stopping {}", this);
        setAttribute(SERVICE_STATE, Lifecycle.STOPPING);
        counter.decrementAndGet();
        setAttribute(SERVICE_STATE, Lifecycle.STOPPED);
    }

    public void restart() {
        throw new UnsupportedOperationException();
    }
    
    @Override
    public String toString() {
        String id = getId();
        return "Entity["+id.substring(Math.max(0, id.length()-8))+"]";
    }
    
    // TODO add more mock methods
}
