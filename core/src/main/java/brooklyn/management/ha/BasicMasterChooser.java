package brooklyn.management.ha;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.management.ha.ManagerMemento.HealthStatus;
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

    public static class AlphabeticMasterChooser extends BasicMasterChooser {
        @Override
        public ManagerMemento choose(ManagementPlaneMemento memento, Duration heartbeatTimeout, String ownNodeId, long timeNow) {
            if (LOG.isDebugEnabled()) LOG.debug("Choosing new master from "+memento.getNodes());
            Map<String, ManagerMemento> contenders = filterHealthy(memento, heartbeatTimeout, timeNow);
            
            if (contenders.size() > 0) {
                List<String> contenderIds = Lists.newArrayList(contenders.keySet());
                Collections.sort(contenderIds);
                return contenders.get(contenderIds.get(0));
            } else {
                LOG.info("No valid management-node found for choosing new master: contender="+memento.getNodes());
                return null;
            }
        }
    }
    
    /**
     * Filters the {@link ManagementPlaneMemento#getNodes()} to only those in an appropriate state, 
     * and with heartbeats that have not timed out.
     */
    protected Map<String, ManagerMemento> filterHealthy(ManagementPlaneMemento memento, Duration heartbeatTimeout, long timeNow) {
        long oldestAcceptableTimestamp = (timeNow - heartbeatTimeout.toMilliseconds());

        Map<String, ManagerMemento> contenders = Maps.newLinkedHashMap();
        for (ManagerMemento contender : memento.getNodes().values()) {
            boolean statusOk = (contender.getStatus() == HealthStatus.STANDBY || contender.getStatus() == HealthStatus.MASTER);
            boolean heartbeatOk = contender.getTimestampUtc() >= oldestAcceptableTimestamp;
            if (statusOk && heartbeatOk) {
                contenders.put(contender.getNodeId(), contender);
            }
            if (LOG.isTraceEnabled()) LOG.trace("Filtering choices of new master: contender="+contender+"; statusOk="+statusOk+"; heartbeatOk="+heartbeatOk);
        }
        return contenders;
    }
}
