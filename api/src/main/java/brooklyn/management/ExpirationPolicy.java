package brooklyn.management;

/**
 * Options for describing how the execution manager will retain or expire a task after it has completed.
 */
public enum ExpirationPolicy {
    /**
     * When the task completes, immediately forget about it.
     */
    IMMEDIATE,
    /**
     * When the task completes, retain a permanent historical record.
     */
    NEVER
}
