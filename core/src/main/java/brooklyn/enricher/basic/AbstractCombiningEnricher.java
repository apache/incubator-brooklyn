package brooklyn.enricher.basic;

import java.lang.reflect.Field;
import java.util.LinkedHashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.event.AttributeSensor;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.event.basic.BasicSensorEvent;
import brooklyn.management.SubscriptionHandle;

/**
 * Convenience base for transforming multiple sensors into a single new sensor.
 * Usage:
 * <code>
 * 
 * </code>
 */
public abstract class AbstractCombiningEnricher<T> extends AbstractEnricher {
    
    public static final Logger log = LoggerFactory.getLogger(AbstractCombiningEnricher.class);
    protected AttributeSensor<T> target;
    
    static class SubscriptionDetails<T2> {
        Field field;
        Entity source;
        AttributeSensor<T2> sensor;
        int count=0;
        SubscriptionHandle handle;
        
        public SubscriptionDetails(Field field, Entity source, AttributeSensor<T2> sensor) {
            this.field = field;
            this.source = source;
            this.sensor = sensor;
        }
    }
    
    Set<SubscriptionDetails> subscriptions;
    
    public AbstractCombiningEnricher(AttributeSensor<T> target) {
        this.target = target;
    }
    
    /** subscribes to a given sensor on the entity where this enricher is attached, 
     * setting the value in the variable name indicated,
     * which must be a field in this class */
    protected synchronized <T2> void subscribe(String varName, AttributeSensor<T2> sensor) {
        subscribe(varName, null, sensor);
    }

    /** @see #subscribe(String, AttributeSensor) */
    protected synchronized <T2> void subscribe(Field field, AttributeSensor<T2> sensor) {
        subscribe(field, null, sensor);
    }

    /** subscribes to a given sensor on the given entity, setting the value in the variable name indicated,
     * which must be a field in this class */
    protected synchronized <T2> void subscribe(String varName, Entity source, AttributeSensor<T2> sensor) {
        Field[] fields = AbstractCombiningEnricher.this.getClass().getDeclaredFields();
        for (Field f: fields) {
            if (f.getName().equals(varName)) {
                subscribe(f, source, sensor);
                return;
            }
        }
        throw new IllegalStateException("Field "+varName+" does not exist");
    }

    /** @see #subscribe(String, AttributeSensor) */
    protected synchronized <T2> void subscribe(Field field, Entity source, AttributeSensor<T2> sensor) {
        if (subscriptions==null) subscriptions = new LinkedHashSet<SubscriptionDetails>();
        subscriptions.add(new SubscriptionDetails(field, source, sensor));
    }
    @Override
    public void setEntity(final EntityLocal entity) {
        super.setEntity(entity);
        
        for (final SubscriptionDetails s: subscriptions) {
            final Entity source = (s.source==null ? entity : s.source);
            SensorEventListener listener = new SensorEventListener() {
                @Override
                public void onEvent(SensorEvent event) {
                    try {
                        s.field.setAccessible(true);
                        s.field.set(AbstractCombiningEnricher.this, event.getValue());
                        s.count++;
                        update();
                        return;
                    } catch (Exception e) {
                        log.error("Error setting "+s.field.getName()+" to "+event.getValue()+" from sensor "+s.sensor+"" +
                                "in enricher "+this+" on "+entity+": "+
                                "ensure variable exists and has the correct type and permissions ("+e+")", e);
                        if (s.handle!=null) unsubscribe(source, s.handle);
                    }
                }
            };
            s.handle = subscribe( source, s.sensor, listener);
            Object value = source.getAttribute(s.sensor);
            // TODO Aled didn't you write a convenience to "subscribeAndRunIfSet" ? (-Alex)
            if (value!=null)
                listener.onEvent(new BasicSensorEvent(s.sensor, source, value));
        }
    }
    
    public void update() {
        for (SubscriptionDetails s: subscriptions) {
            if (s.count==0) return;
        }
        entity.setAttribute(target, compute());
    }

    /** supply the computation in terms of subscribed variables; by default this is only invoked
     * once all fields have been set at least once, then subsequently on any change to either of them */
    public abstract T compute();
    
}
