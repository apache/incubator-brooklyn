package brooklyn.management.internal;

import java.util.Map;

import brooklyn.entity.Entity;
import brooklyn.event.Sensor;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.management.SubscriptionHandle;
import brooklyn.util.internal.LanguageUtils;

import com.google.common.base.Predicate;

class Subscription<T> implements SubscriptionHandle {
    public final String id = LanguageUtils.newUid();
    
    public Object subscriber;
    public Object subscriberExecutionManagerTag;
    /** whether the tag was supplied by user, in which case we should not clear execution semantics */
    public boolean subscriberExecutionManagerTagSupplied;
    public Entity producer;
    public Sensor<T> sensor;
    public SensorEventListener<T> listener;
    public Map<String,Object> flags;
    public Predicate<SensorEvent<T>> eventFilter;

    public Subscription() {
    }
    
    public Subscription(Entity producer, Sensor<T> sensor, SensorEventListener<T> listener) {
        this.producer = producer;
        this.sensor = sensor;
        this.listener = listener;
    }
    
    @Override
    public boolean equals(Object other) {
        return (other instanceof Subscription && ((Subscription<?>)other).id==id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
    
    @Override
    public String toString() {
        return "Subscription["+id+";"+subscriber+"@"+LocalSubscriptionManager.makeEntitySensorToken(producer,sensor)+"]";
    }
}
