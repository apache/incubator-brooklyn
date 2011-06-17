package brooklyn.management.internal;

import java.util.ArrayList;
import java.util.List;

import brooklyn.entity.Entity;
import brooklyn.entity.EntitySummary;
import brooklyn.event.EventListener;
import brooklyn.event.Sensor;
import brooklyn.event.basic.EventFilters;
import brooklyn.event.basic.SensorEvent;

import com.google.common.base.Predicate;

public class LocalSubscriptionManager implements SubscriptionManager {
    private static class Subscription<T> {
        Predicate<Entity> entities;
        Predicate<SensorEvent<?>> filter;
        EventListener<T> listener;

        public Subscription(Predicate<Entity> entities, Predicate<SensorEvent<?>> filter, EventListener<T> listener) {
            this.entities = entities;
            this.filter = filter;
            this.listener = listener;
        }
    }
    
    List<Subscription> subscriptions = new ArrayList<Subscription>();
    
    public void fire(SensorEvent<?> event) {
        //subscriptions findAll { s -> s.filter.apply(event) } findAll { s -> !s.entities || s.entities.apply(event) } each { s -> s.listener.onEvent(event) }
    }

    public void subscribe(String entityId, String sensorName, EventListener listener) {
        Subscription<?> sub = new Subscription(EventFilters.entityId(entityId), EventFilters.sensorName(sensorName), listener);
        subscriptions.add(sub);
    }

    public void subscribe(EntitySummary entity, Sensor sensor, EventListener listener) {
        subscribe(entity.getId(), sensor.getName(), listener);
    }
    
    /*

    public void subscribe(Predicate<SensorEvent> filter, EventListener listener) {
        // TODO Auto-generated method stub
    }

    public void subscribe(Predicate<Entity> entities, Predicate<SensorEvent> filter, EventListener listener) {
        // TODO Auto-generated method stub
    }

    public void addSUbscription(String sensor, EventListener listener) {
        // TODO Auto-generated method stub
    }
     
     */
}
