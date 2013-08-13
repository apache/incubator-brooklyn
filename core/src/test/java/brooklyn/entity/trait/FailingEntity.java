package brooklyn.entity.trait;

import java.util.List;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.test.entity.TestEntity;
import brooklyn.util.flags.SetFromFlag;

import com.google.common.base.Predicate;
import com.google.common.collect.Lists;

@ImplementedBy(FailingEntityImpl.class)
public interface FailingEntity extends TestEntity {

    @SetFromFlag("listener")
    ConfigKey<EventListener> LISTENER = ConfigKeys.newConfigKey(EventListener.class, "listener", "Whether to throw exception on call to start", EventListener.NOOP);
    
    @SetFromFlag("failOnStart")
    ConfigKey<Boolean> FAIL_ON_START = ConfigKeys.newBooleanConfigKey("failOnStart", "Whether to throw exception on call to start", false);
    
    @SetFromFlag("failOnStop")
    ConfigKey<Boolean> FAIL_ON_STOP = ConfigKeys.newBooleanConfigKey("failOnStop", "Whether to throw exception on call to stop", false);
    
    @SetFromFlag("failOnStartCondition")
    ConfigKey<Predicate<? super FailingEntity>> FAIL_ON_START_CONDITION = (ConfigKey) ConfigKeys.newConfigKey(Predicate.class, "failOnStartCondition", "Whether to throw exception on call to start", null);
    
    @SetFromFlag("failOnStopCondition")
    ConfigKey<Predicate<? super FailingEntity>> FAIL_ON_STOP_CONDITION = (ConfigKey) ConfigKeys.newConfigKey(Predicate.class, "failOnStopCondition", "Whether to throw exception on call to stop", null);
    
    @SetFromFlag("exceptionClazz")
    ConfigKey<Class<? extends RuntimeException>> EXCEPTION_CLAZZ = (ConfigKey) ConfigKeys.newConfigKey(Class.class, "exceptionClazz", "Type of exception to throw", IllegalStateException.class);
    
    public interface EventListener {
        public static final EventListener NOOP = new EventListener() {
            @Override public void onEvent(Entity entity, String action, Object[] args) {}
        };
        
        public void onEvent(Entity entity, String action, Object[] args);
    }
    
    public static class RecordingEventListener implements EventListener {
        public final List<Object[]> events = Lists.newCopyOnWriteArrayList();
        
        @Override
        public void onEvent(Entity entity, String action, Object[] args) {
            events.add(new Object[] {entity, action, args});
        }
    }
}
