package brooklyn.management.ha;

import java.net.URI;

import com.google.common.annotations.Beta;

/**
 * Represents the state of a management-node.
 * 
 * @see {@link ManagementPlaneMemento#getNodes()}
 * 
 * @since 0.7.0
 * 
 * @author aled
 */
@Beta
public interface ManagerMemento {

    // TODO Not setting URI currently; ManagementContext doesn't know its URI; only have one if web-console was enabled.
    
    // TODO Add getPlaneId(); but first need to set it in a sensible way
    
    public enum HealthStatus {
        MASTER,
        STANDBY,
        TERMINATED,
        FAILED;
    }
    
    String getBrooklynVersion();
    
    String getNodeId();
    
    URI getUri();
    
    HealthStatus getStatus();

    long getTimestampUtc();
    
    String toVerboseString();
}
