package brooklyn.management.ha;

import java.util.Map;

import brooklyn.mementos.BrooklynMemento;
import brooklyn.mementos.BrooklynMementoPersister;

import com.google.common.annotations.Beta;

/**
 * Meta-data about the management plane - the management nodes and who is currently master.
 * Does not contain any data about the entities under management.
 * <p>
 * This is very similar to how {@link BrooklynMemento} is used by {@link BrooklynMementoPersister},
 * but it is not a memento in the sense it does not reconstitute the entire management plane
 * (so is not called Memento although it can be used by the same memento-serializers).
 * 
 * @since 0.7.0
 * 
 * @author aled
 */
@Beta
public interface ManagementPlaneSyncRecord {

    // TODO Add getPlaneId(); but first need to set it sensibly on each management node
    
    String getMasterNodeId();
    
    /** returns map of {@link ManagementNodeSyncRecord} instances keyed by the nodes' IDs */
    Map<String, ManagementNodeSyncRecord> getManagementNodes();

    String toVerboseString();
}
