package brooklyn.management.ha;

import brooklyn.management.ha.ManagerMemento.HealthStatus;

import com.google.common.annotations.Beta;
import com.google.common.annotations.VisibleForTesting;

/**
 * Monitors other management nodes (via the {@link ManagementPlaneMementoPersister}) to detect
 * if the current master has failed or stopped. If so, then deterministically chooses a new master.
 * If that master is self, then promotes.

 * Users are not expected to implement this class, or to call methods on it directly.
 * 
 * Expected lifecycle of methods calls on this is:
 * <ol>
 *   <li>{@link #setPersister(ManagementPlaneMementoPersister)}
 *   <li>Exactly one of {@link #disabled()} or {@link #start(StartMode)}
 *   <li>Exactly one of {@link #stop()} or {@link #terminate()}
 * </ol>
 * 
 * @since 0.7.0
 */
@Beta
public interface HighAvailabilityManager {

    public static enum StartMode {
        /**
         * Means auto-detect whether to be master or standby; if there is already a master then start as standby, 
         * atherwise start as master.
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

    public static enum NodeStatus {
        UNINITIALISED,
        STANDBY,
        MASTER,
        TERMINATED;
    }

    NodeStatus getNodeStatus();
    
    /**
     * @param persister
     * @return self
     */
    HighAvailabilityManager setPersister(ManagementPlaneMementoPersister persister);

    /**
     * Indicates that HA is disabled - i.e. this is the only management node in this management plane.
     * <p>
     * Subsequently can expect {@link #getNodeStatus()} to be {@link NodeStatus#MASTER} 
     * and {@link #getManagementPlaneStatus()} to show just this one node.
     */
    void disabled();

    /**
     * Starts the monitoring of other nodes (and thus potential promotion of this node from standby to master).
     * <p>
     * By the time this method returns, then if appropriate this node will already be {@link NodeStatus#MASTER}. 
     * Otherwise it will be {@link NodeStatus#STANDBY}.
     * 
     * @throws IllegalStateException if current state of the management-plane doesn't match that desired by {@code startMode} 
     */
    void start(StartMode startMode);

    /**
     * Indicates that this node is stopping - first calls {@link #terminated()} to stop monitoring other nodes,
     * then publishes own status (via {@link ManagementPlaneMementoPersister} of {@link HealthStatus#TERMINATED}.
     */
    void stop();

    /**
     * Terminates all activity - stops monitoring other nodes, etc. Does not publish that the node is stopping.
     */
    void terminate();

    /**
     * Returns a snapshot of the management-plane's status.
     */
    ManagementPlaneMemento getManagementPlaneStatus();
    
    @VisibleForTesting
    ManagementPlaneMementoPersister getPersister();
}
