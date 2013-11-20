package brooklyn.management.internal;

import java.util.Collections;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.Group;
import brooklyn.event.Sensor;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.management.SubscriptionHandle;
import brooklyn.management.SubscriptionManager;

import com.google.common.base.Objects;
import com.google.common.base.Predicate;

public abstract class AbstractSubscriptionManager implements SubscriptionManager {

    // TODO Perhaps could use guava's SynchronizedSetMultimap? But need to check its synchronization guarantees.
    //      That would replace the utils used for subscriptionsBySubscriber etc.
    
    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(AbstractSubscriptionManager.class);

    /** performs the actual subscription; should return the subscription parameter as the handle */
    protected abstract <T> SubscriptionHandle subscribe(Map<String, Object> flags, Subscription<T> s);
    /** performs the actual publishing -- ie distribution to subscriptions */
    public abstract <T> void publish(final SensorEvent<T> event);

    public static class EntitySensorToken {
        Entity e;
        Sensor<?> s;
        public EntitySensorToken(Entity e, Sensor<?> s) {
            this.e = e;
            this.s = s;
        }
        @Override
        public int hashCode() {
            return Objects.hashCode(e, s);
        }
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof EntitySensorToken)) return false;
            if (!Objects.equal(e, ((EntitySensorToken)obj).e)) return false;
            if (!Objects.equal(s, ((EntitySensorToken)obj).s)) return false;
            return true;
        }
        @Override
        public String toString() {
            return (e != null ? e.getId() :  "*")+":"+(s != null ? s.getName() : "*");
        }
    }
    static Object makeEntitySensorToken(Entity e, Sensor<?> s) {
        return new EntitySensorToken(e, s);
    }
    static Object makeEntitySensorToken(SensorEvent<?> se) {
        return makeEntitySensorToken(se.getSource(), se.getSensor());
    }
    
    /** @see SubscriptionManager#subscribe(Map, Entity, Sensor, SensorEventListener) */
    public final <T> SubscriptionHandle subscribe(Entity producer, Sensor<T> sensor, SensorEventListener<? super T> listener) {
        return subscribe(Collections.<String,Object>emptyMap(), producer, sensor, listener);
    }
 
    /**
     * This implementation handles the following flags, in addition to those described in the {@link SubscriptionManager}
     * interface:
     * <ul>
     * <li>subscriberExecutionManagerTag - a tag to pass to execution manager (without setting any execution semantics / TaskPreprocessor);
     *      if not supplied and there is a subscriber, this will be inferred from the subscriber and set up with SingleThreadedScheduler
     * <li>eventFilter - a Predicate&lt;SensorEvent&gt; instance to filter what events are delivered
     * </ul>
     * 
     * @see SubscriptionManager#subscribe(Map, Entity, Sensor, SensorEventListener)
     */
    public final <T> SubscriptionHandle subscribe(Map<String, Object> flags, Entity producer, Sensor<T> sensor, SensorEventListener<? super T> listener) {
        return subscribe(flags, new Subscription<T>(producer, sensor, listener));
    }
        
    /** @see SubscriptionManager#subscribeToChildren(Map, Entity, Sensor, SensorEventListener) */
    public final <T> SubscriptionHandle subscribeToChildren(Entity parent, Sensor<T> sensor, SensorEventListener<? super T> listener) {
        return subscribeToChildren(Collections.<String,Object>emptyMap(), parent, sensor, listener);
    }

    /** @see SubscriptionManager#subscribe(Map, Entity, Sensor, SensorEventListener) */
    public final  <T> SubscriptionHandle subscribeToChildren(Map<String, Object> flags, final Entity parent, Sensor<T> sensor, SensorEventListener<? super T> listener) {
        Predicate<SensorEvent<T>> eventFilter = new Predicate<SensorEvent<T>>() {
            public boolean apply(SensorEvent<T> input) {
                return parent.getChildren().contains(input.getSource());
            }
        };
        flags.put("eventFilter", eventFilter);
        return subscribe(flags, null, sensor, listener);
    }

    /** @see SubscriptionManager#subscribeToChildren(Map, Entity, Sensor, SensorEventListener) */
    public final <T> SubscriptionHandle subscribeToMembers(Group parent, Sensor<T> sensor, SensorEventListener<? super T> listener) {
        return subscribeToMembers(Collections.<String,Object>emptyMap(), parent, sensor, listener);
    }

    /** @see SubscriptionManager#subscribe(Map, Entity, Sensor, SensorEventListener) */
    public final  <T> SubscriptionHandle subscribeToMembers(Map<String, Object> flags, final Group parent, Sensor<T> sensor, SensorEventListener<? super T> listener) {
        Predicate<SensorEvent<T>> eventFilter = new Predicate<SensorEvent<T>>() {
            public boolean apply(SensorEvent<T> input) {
                return parent.getMembers().contains(input.getSource());
            }
        };
        flags.put("eventFilter", eventFilter);
        return subscribe(flags, null, sensor, listener);
    }

    protected <T> Object getSubscriber(Map<String, Object> flags, Subscription<T> s) {
        return s.subscriber!=null ? s.subscriber : flags.containsKey("subscriber") ? flags.remove("subscriber") : s.listener;
    }

}
