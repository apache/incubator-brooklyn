package brooklyn.test.entity;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.testng.collections.Lists;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.annotation.Effector;
import brooklyn.entity.annotation.EffectorParam;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.entity.basic.MethodEffector;
import brooklyn.entity.proxying.BasicEntitySpec;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.entity.trait.Startable;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.event.basic.BasicNotificationSensor;
import brooklyn.event.basic.ListConfigKey;
import brooklyn.event.basic.MapConfigKey;
import brooklyn.event.basic.Sensors;
import brooklyn.event.basic.SetConfigKey;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.flags.SetFromFlag;

/**
 * Mock entity for testing.
 */
//FIXME Don't want to extend EntityLocal, but tests call things like entity.subscribe(); how to deal with that elegantly?
@ImplementedBy(TestEntityImpl.class)
public interface TestEntity extends Entity, Startable, EntityLocal, EntityInternal {

    /**
     * @deprecated since 0.6; use {@link EntitySpec.create(Class)} directly
     */
    public static class Spec<T extends TestEntity, S extends Spec<T,S>> extends BasicEntitySpec<T,S> {

        private static class ConcreteSpec extends Spec<TestEntity, ConcreteSpec> {
            ConcreteSpec() {
                super(TestEntity.class);
            }
        }
        
        public static Spec<TestEntity, ?> newInstance() {
            return new ConcreteSpec();
        }
        
        protected Spec(Class<T> type) {
            super(type);
        }
    }
    
    @SetFromFlag("confName")
    public static final ConfigKey<String> CONF_NAME = ConfigKeys.newStringConfigKey("test.confName", "Configuration key, my name", "defaultval");
    public static final BasicConfigKey<Map> CONF_MAP_PLAIN = new BasicConfigKey<Map>(Map.class, "test.confMapPlain", "Configuration key that's a plain map", MutableMap.of());
    public static final BasicConfigKey<List> CONF_LIST_PLAIN = new BasicConfigKey<List>(List.class, "test.confListPlain", "Configuration key that's a plain list", Lists.newArrayList());
    public static final MapConfigKey<String> CONF_MAP_THING = new MapConfigKey<String>(String.class, "test.confMapThing", "Configuration key that's a map thing");
    public static final ListConfigKey<String> CONF_LIST_THING = new ListConfigKey<String>(String.class, "test.confListThing", "Configuration key that's a list thing");
    public static final SetConfigKey<String> CONF_SET_THING = new SetConfigKey<String>(String.class, "test.confSetThing", "Configuration key that's a set thing");
    
    public static final AttributeSensor<Integer> SEQUENCE = Sensors.newIntegerSensor("test.sequence", "Test Sequence");
    public static final AttributeSensor<String> NAME = Sensors.newStringSensor("test.name", "Test name");
    public static final BasicNotificationSensor<Integer> MY_NOTIF = new BasicNotificationSensor<Integer>(Integer.class, "test.myNotif", "Test notification");
    
    public static final AttributeSensor<Lifecycle> SERVICE_STATE = Attributes.SERVICE_STATE;
    
    public static final MethodEffector<Void> MY_EFFECTOR = new MethodEffector<Void>(TestEntity.class, "myEffector");
    public static final MethodEffector<Object> IDENTITY_EFFECTOR = new MethodEffector<Object>(TestEntity.class, "identityEffector");
    
    public boolean isLegacyConstruction();
    
    @Effector(description="an example of a no-arg effector")
    public void myEffector();
    
    @Effector(description="returns the arg passed in")
    public Object identityEffector(@EffectorParam(name="arg", description="val to return") Object arg);
    
    public AtomicInteger getCounter();
    
    public int getCount();
    
    public Map<?,?> getConstructorProperties();

    public Map<?,?> getConfigureProperties();

    public int getSequenceValue();

    public void setSequenceValue(int value);
    
    public <T extends Entity> T createChild(EntitySpec<T> spec);

    public <T extends Entity> T createAndManageChild(EntitySpec<T> spec);
}
