package brooklyn.entity.basic;

import brooklyn.entity.Entity;

import com.google.common.base.CaseFormat;

/**
 * An enumeration representing the status of an {@link Entity}.
 */
public enum Status {
    UNINITIALIZED,  // TODO document
    READY,          // TODO document
    STARTING,       // TODO document
    STARTED,        // TODO document
    PAUSED,         // TODO document
    STOPPING,       // TODO document
    STOPPED,        // TODO document
    FAILED,         // TODO document
    ERROR,          // TODO document
    IDLE,           // TODO document
    TIMED_OUT,      // TODO document
    WORKING,        // TODO document
    IN_PROGRESS,    // TODO document
    NOT_FOUND,      // TODO document
    ACCESS_DENIED,  // TODO document
    ON_FIRE,        // The entity is on fire
    UNRECOGNIZED;   // Returned if the status text cannot be parsed

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
     * Creates a {@link Status} from a text representation.
     *
     * This accepts the text representations output by the {@link #value()} method for each entry.
     *
     * @see #value()
     */
    public static Status fromValue(String v) {
       try {
          return valueOf(CaseFormat.LOWER_HYPHEN.to(CaseFormat.UPPER_UNDERSCORE, v));
       } catch (IllegalArgumentException iae) {
          return UNRECOGNIZED;
       }
    }
}