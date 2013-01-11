package brooklyn.entity.basic;

import groovy.lang.Closure;
import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.Group;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.entity.trait.Changeable;
import brooklyn.event.AttributeSensor;
import brooklyn.event.Sensor;
import brooklyn.event.SensorEvent;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.util.flags.SetFromFlag;

import com.google.common.base.Predicate;

@ImplementedBy(DynamicGroupImpl.class)
public interface DynamicGroup extends Group, Changeable {
    
    @SetFromFlag("entityFilter")
    public static final ConfigKey<Predicate<? super Entity>> ENTITY_FILTER = new BasicConfigKey(
            Predicate.class, "dynamicgroup.entityfilter", "Filter for which entities will automatically be in group", null);

    public static final AttributeSensor<Boolean> RUNNING = new BasicAttributeSensor<Boolean>(
            Boolean.class, "dynamicgroup.running", "Whether the entity is running, so will automatically update group membership");
    
    /**
     * Stops this group (but does not stop any of its members). De-activates the filter and unsubscribes to
     * entity-updates, so the membership of the group will not change.
     */
    public void stop();

    public void rescanEntities();
    
    public void setEntityFilter(Predicate<? super Entity> filter);
    
    public void setEntityFilter(Closure<Boolean> filter);
    
    public <T> void addSubscription(Entity producer, Sensor<T> sensor, final Predicate<? super SensorEvent<? super T>> filter);

    public <T> void addSubscription(Entity producer, Sensor<T> sensor);
}
