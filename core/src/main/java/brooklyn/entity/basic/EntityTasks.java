package brooklyn.entity.basic;

import brooklyn.entity.Entity;
import brooklyn.event.AttributeSensor;
import brooklyn.management.TaskAdaptable;
import brooklyn.util.collections.CollectionFunctionals;
import brooklyn.util.guava.Functionals;
import brooklyn.util.repeat.Repeater;
import brooklyn.util.task.Tasks;
import brooklyn.util.time.Duration;

import com.google.common.base.Functions;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;

/** Generally useful tasks related to entities */
public class EntityTasks {

    /** creates an (unsubmitted) task which waits for the attribute to satisfy the given predicate,
     * with an optional timeout */
    public static <T> TaskAdaptable<Boolean> awaitingAttribute(Entity entity, AttributeSensor<T> sensor, Predicate<T> condition, Duration timeout) {
        return Tasks.awaitingBuilder(Repeater.create("waiting on "+sensor.getName())
                .backoff(Duration.millis(10), 1.5, Duration.millis(200))
                .limitTimeTo(timeout==null ? Duration.PRACTICALLY_FOREVER : timeout)
//                TODO abort if entity is unmanaged
                .until(Functionals.callable(Functions.forPredicate(EntityPredicates.attributeSatisfies(sensor, condition)), entity)),
                true)
            .description("waiting on "+entity+" "+sensor.getName()+" "+condition+
                (timeout!=null ? ", timeout "+timeout : "")).build();
    }

    /** as {@link #awaitingAttribute(Entity, AttributeSensor, Predicate, Duration)} for multiple entities */
    public static <T> TaskAdaptable<Boolean> awaitingAttribute(Iterable<Entity> entities, AttributeSensor<T> sensor, Predicate<T> condition, Duration timeout) {
        return Tasks.awaitingBuilder(Repeater.create("waiting on "+sensor.getName())
                .backoff(Duration.millis(10), 1.5, Duration.millis(200))
                .limitTimeTo(timeout==null ? Duration.PRACTICALLY_FOREVER : timeout)
//                TODO abort if entity is unmanaged
                .until(Functionals.callable(Functions.forPredicate(
                    CollectionFunctionals.all(EntityPredicates.attributeSatisfies(sensor, condition))), entities)),
                true)
            .description("waiting on "+Iterables.size(entities)+", "+sensor.getName()+" "+condition+
                (timeout!=null ? ", timeout "+timeout : "")+
                ": "+entities).build();
    }
}
