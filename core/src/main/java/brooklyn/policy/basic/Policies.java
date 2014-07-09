package brooklyn.policy.basic;

import groovy.lang.Closure;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.event.Sensor;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.policy.Policy;
import brooklyn.policy.basic.AbstractPolicy;

@SuppressWarnings({"rawtypes","unchecked"})
public class Policies {

    public static SensorEventListener listenerFromValueClosure(final Closure code) {
        return new SensorEventListener() {
            @Override
            public void onEvent(SensorEvent event) {
                code.call(event.getValue());
            }
        };
    }
    
    public static <T> Policy newSingleSensorValuePolicy(final Sensor<T> sensor, final Closure code) {
        return new AbstractPolicy() {
            @Override
            public void setEntity(EntityLocal entity) {
                super.setEntity(entity);
                entity.subscribe(entity, sensor, listenerFromValueClosure(code));
            }
        };
    }
    
    public static <S,T> Policy newSingleSensorValuePolicy(final Entity remoteEntity, final Sensor<T> remoteSensor, 
            final Closure code) {
        return new AbstractPolicy() {
            @Override
            public void setEntity(EntityLocal entity) {
                super.setEntity(entity);
                entity.subscribe(remoteEntity, remoteSensor, listenerFromValueClosure(code));
            }
        };
    }

    public static Lifecycle getPolicyStatus(Policy p) {
        if (p.isRunning()) return Lifecycle.RUNNING;
        if (p.isDestroyed()) return Lifecycle.DESTROYED;
        if (p.isSuspended()) return Lifecycle.STOPPED;
        // TODO could policy be in an error state?
        return Lifecycle.CREATED;        
    }
    
}
