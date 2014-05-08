package brooklyn.management.ha;

import java.net.URI;

import com.google.common.annotations.Beta;

/**
 * Represents the state of a management-node.
 * 
 * @see {@link ManagementPlaneSyncRecord#getManagementNodes()}
 * 
 * @since 0.7.0
 * 
 * @author aled
 */
@Beta
public interface ManagementNodeSyncRecord {

    // TODO Not setting URI currently; ManagementContext doesn't know its URI; only have one if web-console was enabled.
    
    // TODO Add getPlaneId(); but first need to set it in a sensible way
    
    String getBrooklynVersion();
    
    String getNodeId();
    
    URI getUri();
    
    ManagementNodeState getStatus();

    long getTimestampUtc();
    
    String toVerboseString();
}
