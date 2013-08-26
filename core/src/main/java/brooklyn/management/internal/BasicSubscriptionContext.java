package brooklyn.management.internal;

import static brooklyn.util.JavaGroovyEquivalents.mapOf;
import groovy.lang.Closure;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.entity.Group;
import brooklyn.event.Sensor;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.management.SubscriptionContext;
import brooklyn.management.SubscriptionHandle;
import brooklyn.management.SubscriptionManager;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;

/**
 * A {@link SubscriptionContext} for an entity or other user of a {@link SubscriptionManager}.
 */
public class BasicSubscriptionContext implements SubscriptionContext {
    
    private static final Logger LOG = LoggerFactory.getLogger(BasicSubscriptionContext.class);

    private final SubscriptionManager manager;
    private final Object subscriber;
    private final Map<String,Object> flags;

    public BasicSubscriptionContext(SubscriptionManager manager, Object subscriber) {
        this(Collections.<String,Object>emptyMap(), manager, subscriber);
    }
    
    public BasicSubscriptionContext(Map<String, ?> flags, SubscriptionManager manager, Object subscriber) {
    	this.manager = manager;
        this.subscriber = subscriber;
        this.flags = mapOf("subscriber", subscriber);
    	if (flags!=null) this.flags.putAll(flags);
    }

    @SuppressWarnings("rawtypes")
    public <T> SubscriptionHandle subscribe(Entity producer, Sensor<T> sensor, Closure c) {
        return subscribe(Collections.<String,Object>emptyMap(), producer, sensor, c);
    }
    
    @SuppressWarnings("rawtypes")
    public <T> SubscriptionHandle subscribe(Map<String, Object> newFlags, Entity producer, Sensor<T> sensor, Closure c) {
        return subscribe(newFlags, producer, sensor, toSensorEventListener(c));        
    }

    @Override
    public <T> SubscriptionHandle subscribe(Entity producer, Sensor<T> sensor, SensorEventListener<? super T> listener) {
        return subscribe(Collections.<String,Object>emptyMap(), producer, sensor, listener);
    }
    
    @Override
    public <T> SubscriptionHandle subscribe(Map<String, Object> newFlags, Entity producer, Sensor<T> sensor, SensorEventListener<? super T> listener) {
        Map<String,Object> subscriptionFlags = Maps.newLinkedHashMap(flags);
        if (newFlags != null) subscriptionFlags.putAll(newFlags);
        return manager.subscribe(subscriptionFlags, producer, sensor, listener);
    }

    @SuppressWarnings("rawtypes")
    public <T> SubscriptionHandle subscribeToChildren(Entity parent, Sensor<T> sensor, Closure c) {
        return subscribeToChildren(Collections.<String,Object>emptyMap(), parent, sensor, c);
    }
    
    @SuppressWarnings("rawtypes")
    public <T> SubscriptionHandle subscribeToChildren(Map<String, Object> newFlags, Entity parent, Sensor<T> sensor, Closure c) {
        return subscribeToChildren(newFlags, parent, sensor, toSensorEventListener(c));
    }

    @Override
    public <T> SubscriptionHandle subscribeToChildren(Entity parent, Sensor<T> sensor, SensorEventListener<? super T> listener) {
        return subscribeToChildren(Collections.<String,Object>emptyMap(), parent, sensor, listener);
    }
    
    @Override
    public <T> SubscriptionHandle subscribeToChildren(Map<String, Object> newFlags, Entity parent, Sensor<T> sensor, SensorEventListener<? super T> listener) {
        Map<String,Object> subscriptionFlags = Maps.newLinkedHashMap(flags);
        if (newFlags != null) subscriptionFlags.putAll(newFlags);
        return manager.subscribeToChildren(subscriptionFlags, parent, sensor, listener);
    }

    @SuppressWarnings("rawtypes")
    public <T> SubscriptionHandle subscribeToMembers(Group parent, Sensor<T> sensor, Closure c) {
        return subscribeToMembers(Collections.<String,Object>emptyMap(), parent, sensor, c);
    }

    @SuppressWarnings("rawtypes")
    public <T> SubscriptionHandle subscribeToMembers(Map<String, Object> newFlags, Group parent, Sensor<T> sensor, Closure c) {
        return subscribeToMembers(newFlags, parent, sensor, toSensorEventListener(c));
    }
    
    @Override
    public <T> SubscriptionHandle subscribeToMembers(Group parent, Sensor<T> sensor, SensorEventListener<? super T> listener) {
        return subscribeToMembers(Collections.<String,Object>emptyMap(), parent, sensor, listener);
    }
    
    @Override
    public <T> SubscriptionHandle subscribeToMembers(Map<String, Object> newFlags, Group parent, Sensor<T> sensor, SensorEventListener<? super T> listener) {
        Map<String,Object> subscriptionFlags = Maps.newLinkedHashMap(flags);
        if (newFlags != null) subscriptionFlags.putAll(newFlags);
        return manager.subscribeToMembers(subscriptionFlags, parent, sensor, listener);
    }

    @SuppressWarnings("rawtypes")
    @Override
    public boolean unsubscribe(SubscriptionHandle subscriptionId) {
        Preconditions.checkNotNull(subscriptionId, "subscriptionId must not be null");
        Preconditions.checkArgument(Objects.equal(subscriber, ((Subscription) subscriptionId).subscriber), "The subscriptionId is for a different "+subscriber+"; expected "+((Subscription) subscriptionId).subscriber);
        return manager.unsubscribe(subscriptionId);
    }

    /** @see SubscriptionManager#publish(SensorEvent) */
    @Override
    public <T> void publish(SensorEvent<T> event) {
        manager.publish(event);
    }

    /** Return the subscriptions associated with this context */
    @Override
    public Set<SubscriptionHandle> getSubscriptions() {
        return manager.getSubscriptionsForSubscriber(subscriber);
    }

    @Override
    public int unsubscribeAll() {
        int count = 0;
        for (SubscriptionHandle s : ImmutableList.copyOf(getSubscriptions())) {
            count++; 
            boolean result = unsubscribe(s); 
            if (!result) LOG.warn("When unsubscribing from all of {}, unsubscribe of {} return false", subscriber, s);
        }
        return count;
    }
    
    @SuppressWarnings("rawtypes")
    private <T> SensorEventListener<T> toSensorEventListener(final Closure c) {
        return new SensorEventListener<T>() {
            @Override public void onEvent(SensorEvent<T> event) {
                c.call(event);
            }
        };
    }
}
