package brooklyn.management.ha;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.util.time.Duration;

import com.google.common.annotations.Beta;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

/**
 * @since 0.7.0
 * 
 * @author aled
 */
@Beta
public abstract class BasicMasterChooser implements MasterChooser {

    private static final Logger LOG = LoggerFactory.getLogger(BasicMasterChooser.class);

    // advantage of this over taking most recent is that nodes will agree on this
    // (where heartbeat timeout is reasonably large)
    public static class AlphabeticMasterChooser extends BasicMasterChooser {
        @Override
        public ManagementNodeSyncRecord choose(ManagementPlaneSyncRecord memento, Duration heartbeatTimeout, String ownNodeId) {
            if (LOG.isDebugEnabled()) LOG.debug("Choosing new master from "+memento.getManagementNodes());
            ManagementNodeSyncRecord me = memento.getManagementNodes().get(ownNodeId);
            if (me==null) {
                LOG.warn("Management node details not known when choosing new master: "+memento+" / "+ownNodeId);
                return null;
            }
            Long nowIsh = me.getRemoteTimestamp();
            if (nowIsh==null) {
                LOG.warn("Management node for self missing timestamp when choosing new master: "+memento);
                return null;
            }
            
            Map<String, ManagementNodeSyncRecord> contenders = filterHealthy(memento, heartbeatTimeout, nowIsh);
            
            if (contenders.size() > 0) {
                List<String> contenderIds = Lists.newArrayList(contenders.keySet());
                Collections.sort(contenderIds);
                return contenders.get(contenderIds.get(0));
            } else {
                LOG.info("No valid management node found for choosing new master: contender="+memento.getManagementNodes());
                return null;
            }
        }
    }
    
    /**
     * Filters the {@link ManagementPlaneSyncRecord#getManagementNodes()} to only those in an appropriate state, 
     * and with heartbeats that have not timed out.
     */
    protected Map<String, ManagementNodeSyncRecord> filterHealthy(ManagementPlaneSyncRecord memento, Duration heartbeatTimeout, long nowIsh) {
        long oldestAcceptableTimestamp = nowIsh - heartbeatTimeout.toMilliseconds();
        Map<String, ManagementNodeSyncRecord> contenders = Maps.newLinkedHashMap();
        for (ManagementNodeSyncRecord contender : memento.getManagementNodes().values()) {
            boolean statusOk = (contender.getStatus() == ManagementNodeState.STANDBY || contender.getStatus() == ManagementNodeState.MASTER);
            Long remoteTimestamp = contender.getRemoteTimestamp();
            boolean heartbeatOk;
            if (remoteTimestamp==null) {
                throw new IllegalStateException("Missing remote timestamp when performing master election: "+this+" / "+contender);
                // if the above exception happens in some contexts we could either fallback to local, or fail:
//                remoteTimestamp = contender.getLocalTimestamp();
                // or
//                heartbeatOk=false;
            } else {
                heartbeatOk = remoteTimestamp >= oldestAcceptableTimestamp;
            }
            if (statusOk && heartbeatOk) {
                contenders.put(contender.getNodeId(), contender);
            }
            if (LOG.isTraceEnabled()) LOG.trace("Filtering choices of new master: contender="+contender+"; statusOk="+statusOk+"; heartbeatOk="+heartbeatOk);
        }
        return contenders;
    }
}
