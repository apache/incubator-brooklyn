package brooklyn.management.ha;

/** Specifies the HA mode that a mgmt node should run in */
public enum HighAvailabilityMode {
    /** Means HA mode should not be operational. */
    DISABLED,
    
    /**
     * Means auto-detect whether to be master or standby; if there is already a master then start as standby, 
     * otherwise start as master.
     */
    AUTO,
    
    /**
     * Means node must be standby; if there is not already a master then fail fast on startup. 
     */
    STANDBY,
    
    /**
     * Means node must be master; if there is already a master then fail fast on startup.
     */
    MASTER;
}
