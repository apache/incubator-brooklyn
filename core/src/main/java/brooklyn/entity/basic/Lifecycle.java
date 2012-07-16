package brooklyn.entity.basic;

import com.google.common.base.CaseFormat;

/**
 * An enumeration representing the status of an {@link brooklyn.entity.Entity}.
 *
 * @startuml img/entity-lifecycle.png
 * title Entity Lifecycle
 *
 * (*) ->  [ @Create ]     "CREATED"
 *     if "Exception" then
 *     ->  [ @Error ]      "ON_FIRE"
 *     else
 *     --> [ @PreStart ]   "STARTING"
 *     -->                 "RUNNING"
 *     ->  [ @PreStop ]    "STOPPING"
 *     --> [ @PostStop ]   "STOPPED"
 *     -->                 "RUNNING"
 *     --> [ @Destroy ]    "DESTROYED"
 *     -left-> (*)
 * @enduml
 */
public enum Lifecycle {
    /**
     * The entity has just been created.
     *
     * This stage encompasses the contructors and any methods annotated as {@link brooklyn.entity.basic.Create}. Once this stage is
     * complete, the basic set of {@link brooklyn.event.Sensor}s will be available, apart from any that require the entity to be active or
     * deployed to a {@link brooklyn.location.Location}.
     */
    CREATED,

    /**
     * The entity is starting.
     *
     * This stage is entered when the {@link brooklyn.entity.trait.Startable#START} {@link brooklyn.entity.Effector} is called. Any methods annotated
     * as {@link brooklyn.entity.basic.PreStart} are run, and the entity will have its location set and and setup helper object created.
     */
    STARTING,

    /**
     * The entity service is running.
     *
     * This stage is entered when the service is fully active. All sensors should be producing data, and clients can
     * connect to the entity,
     */
    RUNNING,

    /**
     * The entity is stopping.
     *
     * This stage is activated when the {@link brooklyn.entity.trait.Startable#STOP} effector is called. The entity service is stopped, and any
     * methods annotated as {@link brooklyn.entity.basic.PreStop} or {@link brooklyn.entity.basic.PostStop} are called as appropriate. Sensors that provide data
     * from the running entity should be cleared and subscriptions cancelled.
     */
    STOPPING,

    /**
     * The entity is no longer running.
     *
     * The entity is now removed from the location it was started in, and is no longer providing any sensor data apart from
     * basic entity attributes.
     */
    STOPPED,

    /**
     * The entity is destroyed.
     *
     * Any methods annotated as {@link brooklyn.entity.basic.Destroy} will be run to clear up entity state and resources. The entity will then be
     * unmanaged and removed from any groups and its parent.
     */
    DESTROYED,

    /**
     * Entity error state.
     *
     * This stage is reachable from any other stage if an error occurs or an exception is thrown. It is not generally possible
     * to recover from this state, but any {@link brooklyn.entity.basic.Error} annotated methods will be run to provide feedback or housekeeping.
     */
    ON_FIRE;

    /**
     * The text representation of the {@link #name()}.
     *
     * This is formatted as lower case characters, with hyphens instead of spaces.
     */
    public String value() {
       return CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.LOWER_HYPHEN, name());
    }

    /** @see #value() */
    @Override
    public String toString() { return value(); }

    /**
     * Creates a {@link Lifecycle} from a text representation.
     *
     * This accepts the text representations output by the {@link #value()} method for each entry.
     *
     * @see #value()
     */
    public static Lifecycle fromValue(String v) {
       try {
          return valueOf(CaseFormat.LOWER_HYPHEN.to(CaseFormat.UPPER_UNDERSCORE, v));
       } catch (IllegalArgumentException iae) {
          return ON_FIRE;
       }
    }
}