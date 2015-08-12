/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package brooklyn.management.ha;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.brooklyn.management.ha.ManagementNodeState;
import org.apache.brooklyn.management.ha.ManagementNodeSyncRecord;
import org.apache.brooklyn.management.ha.ManagementPlaneSyncRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.trait.Identifiable;
import brooklyn.util.collections.MutableList;
import brooklyn.util.text.NaturalOrderComparator;
import brooklyn.util.time.Duration;

import com.google.common.annotations.Beta;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ComparisonChain;
import com.google.common.collect.Ordering;

/**
 * @since 0.7.0
 * 
 * @author aled
 */
@Beta
public abstract class BasicMasterChooser implements MasterChooser {

    private static final Logger LOG = LoggerFactory.getLogger(BasicMasterChooser.class);

    protected static class ScoredRecord<T extends Comparable<T>> implements Identifiable, Comparable<ScoredRecord<T>> {
        String id;
        ManagementNodeSyncRecord record;
        T score;
        
        @Override
        public String getId() {
            return id;
        }

        @Override
        public int compareTo(ScoredRecord<T> o) {
            return score.compareTo(o.score);
        }
    }
    
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
        
        List<ScoredRecord<?>> contenders = filterHealthy(memento, heartbeatTimeout, nowIsh);
        
        if (!contenders.isEmpty()) {
            return pick(contenders);
        } else {
            LOG.info("No valid management node found for choosing new master: contender="+memento.getManagementNodes());
            return null;
        }        
    }

    /** pick the best contender; argument guaranteed to be non-null and non-empty,
     * filtered for health reasons */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    protected ManagementNodeSyncRecord pick(List<ScoredRecord<?>> contenders) {
        ScoredRecord min = null;
        for (ScoredRecord x: contenders) {
            if (min==null || x.score.compareTo(min.score)<0) min = x;
        }
        return min.record;
    }

    public static class AlphabeticChooserScore implements Comparable<AlphabeticChooserScore> {
        long priority;
        int versionBias;
        String brooklynVersion;
        int statePriority;
        String nodeId;
        
        @Override
        public int compareTo(AlphabeticChooserScore o) {
            if (o==null) return -1;
            return ComparisonChain.start()
                // invert the order where we prefer higher values
                .compare(o.priority, this.priority)
                .compare(o.versionBias, this.versionBias)
                .compare(o.brooklynVersion, this.brooklynVersion, 
                    Ordering.from(NaturalOrderComparator.INSTANCE).nullsFirst())
                .compare(o.statePriority, this.statePriority)
                .compare(this.nodeId, o.nodeId, Ordering.usingToString().nullsLast())
                .result();
        }
    }
    
    /** comparator which prefers, in order:
     * <li> higher explicit priority
     * <li> non-snapshot Brooklyn version, then any Brooklyn version, and lastly null version 
     *      (using {@link NaturalOrderComparator} so e.g. v10 > v3.20 > v3.9 )
     * <li> higher version (null last)
     * <li> node which reports it's master, hot standby, then standby 
     * <li> finally (common case): lower (alphabetically) node id
     */
    public static class AlphabeticMasterChooser extends BasicMasterChooser {
        final boolean preferHotStandby;
        public AlphabeticMasterChooser(boolean preferHotStandby) { this.preferHotStandby = preferHotStandby; }
        public AlphabeticMasterChooser() { this.preferHotStandby = true; }
        @Override
        protected AlphabeticChooserScore score(ManagementNodeSyncRecord contender) {
            AlphabeticChooserScore score = new AlphabeticChooserScore();
            score.priority = contender.getPriority()!=null ? contender.getPriority() : 0;
            score.brooklynVersion = contender.getBrooklynVersion();
            score.versionBias = contender.getBrooklynVersion()==null ? -2 :
                contender.getBrooklynVersion().toLowerCase().indexOf("snapshot")>=0 ? -1 :
                0;
            if (preferHotStandby) {
                // other master should be preferred before we get invoked, but including for good measure
                score.statePriority = contender.getStatus()==ManagementNodeState.MASTER ? 3 :
                    contender.getStatus()==ManagementNodeState.HOT_STANDBY ? 2 :
                        contender.getStatus()==ManagementNodeState.STANDBY ? 1 : -1;
            } else {
                score.statePriority = 0;
            }
            score.nodeId = contender.getNodeId();
            return score;
        }
    }
    
    /**
     * Filters the {@link ManagementPlaneSyncRecord#getManagementNodes()} to only those in an appropriate state, 
     * and with heartbeats that have not timed out.
     */
    protected List<ScoredRecord<?>> filterHealthy(ManagementPlaneSyncRecord memento, Duration heartbeatTimeout, long nowIsh) {
        long oldestAcceptableTimestamp = nowIsh - heartbeatTimeout.toMilliseconds();
        List<ScoredRecord<?>> contenders = MutableList.of();
        for (ManagementNodeSyncRecord contender : memento.getManagementNodes().values()) {
            boolean statusOk = (contender.getStatus() == ManagementNodeState.STANDBY || contender.getStatus() == ManagementNodeState.HOT_STANDBY || contender.getStatus() == ManagementNodeState.MASTER);
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
                contenders.add(newScoredRecord(contender));
            }
            if (LOG.isTraceEnabled()) LOG.trace("Filtering choices of new master: contender="+contender+"; statusOk="+statusOk+"; heartbeatOk="+heartbeatOk);
        }
        return contenders;
    }

    @VisibleForTesting
    //Java 6 compiler workaround, using parameterized types fails
    @SuppressWarnings({ "unchecked", "rawtypes" })
    protected List<ScoredRecord<?>> sort(List<ScoredRecord<?>> input) {
        ArrayList copy = new ArrayList<ScoredRecord<?>>(input);
        Collections.sort(copy);
        return copy;
    }
    
    @SuppressWarnings({ "unchecked", "rawtypes" })
    protected ScoredRecord<?> newScoredRecord(ManagementNodeSyncRecord contender) {
        ScoredRecord r = new ScoredRecord();
        r.id = contender.getNodeId();
        r.record = contender;
        r.score = score(contender);
        return r;
    }

    protected abstract Comparable<?> score(ManagementNodeSyncRecord contender);
    
}
