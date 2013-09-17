package brooklyn.rest.domain;

/**
 * @author Adam Lowe
 */
public enum Status {
    ACCEPTED,
    STARTING,
    RUNNING,
    STOPPING,
    STOPPED,
    TERMINATED,
    ERROR,
    UNKNOWN
}