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
     * Indicates that HA is disabled: this node will act as the only management node in this management plane,
     * and will not persist HA meta-information (meaning other nodes cannot join). 
     * <p>
     * Subsequently can expect {@link #getNodeStatus()} to be {@link NodeStatus#MASTER} 
     * and {@link #getManagementPlaneStatus()} to show just this one node --
     * as if it were running HA with just one node --
     * but {@link #isRunning()} will return false.
     * <p>
     * Currently this method is intended to be called early in the lifecycle,
     * instead of {@link #start(HighAvailabilityMode)}. It may be an error if
     * this is called after this HA Manager is started.
     */
    void disabled();

    /** Whether HA mode is operational */
    boolean isRunning();
    
    /**
     * Starts the monitoring of other nodes (and thus potential promotion of this node from standby to master).
     * <p>
     * By the time this method returns, then if appropriate this node will already be {@link NodeStatus#MASTER}. 
     * Otherwise it will be {@link NodeStatus#STANDBY}.
     * 
     * @throws IllegalStateException if current state of the management-plane doesn't match that desired by {@code startMode} 
     */
    void start(HighAvailabilityMode startMode);

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
