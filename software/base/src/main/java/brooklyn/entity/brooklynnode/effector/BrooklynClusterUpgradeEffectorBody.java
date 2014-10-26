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
package brooklyn.entity.brooklynnode.effector;

import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Effector;
import brooklyn.entity.Entity;
import brooklyn.entity.Group;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.EntityPredicates;
import brooklyn.entity.basic.EntityTasks;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.entity.brooklynnode.BrooklynCluster;
import brooklyn.entity.brooklynnode.BrooklynCluster.SelectMasterEffector;
import brooklyn.entity.brooklynnode.BrooklynCluster.UpgradeClusterEffector;
import brooklyn.entity.brooklynnode.BrooklynNode;
import brooklyn.entity.brooklynnode.BrooklynNode.SetHAModeEffector;
import brooklyn.entity.effector.EffectorBody;
import brooklyn.entity.effector.Effectors;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.management.TaskAdaptable;
import brooklyn.management.ha.HighAvailabilityMode;
import brooklyn.management.ha.ManagementNodeState;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.net.Urls;
import brooklyn.util.task.DynamicTasks;
import brooklyn.util.task.Tasks;
import brooklyn.util.time.Duration;

import com.google.api.client.util.Preconditions;
import com.google.common.base.Predicates;
import com.google.common.collect.Collections2;
import com.google.common.collect.Iterables;

public class BrooklynClusterUpgradeEffectorBody extends EffectorBody<Void> implements UpgradeClusterEffector {
    
    private static final Logger log = LoggerFactory.getLogger(BrooklynClusterUpgradeEffectorBody.class);
    
    public static final Effector<Void> UPGRADE_CLUSTER = Effectors.effector(UpgradeClusterEffector.UPGRADE_CLUSTER)
        .impl(new BrooklynClusterUpgradeEffectorBody()).build();

    private AtomicBoolean upgradeInProgress = new AtomicBoolean();

    @Override
    public Void call(ConfigBag parameters) {
        if (!upgradeInProgress.compareAndSet(false, true)) {
            throw new IllegalStateException("An upgrade is already in progress.");
        }

        EntitySpec<?> memberSpec = entity().getConfig(BrooklynCluster.MEMBER_SPEC);
        Preconditions.checkNotNull(memberSpec, BrooklynCluster.MEMBER_SPEC.getName() + " is required for " + UpgradeClusterEffector.class.getName());

        ConfigBag specCfg = ConfigBag.newInstance( memberSpec.getConfig() );
        ConfigBag origSpecCfg = ConfigBag.newInstanceCopying(specCfg);
        log.debug("Upgrading "+entity()+", changing "+BrooklynCluster.MEMBER_SPEC+" from "+memberSpec+" / "+origSpecCfg);
        
        try {
            String newDownloadUrl = parameters.get(DOWNLOAD_URL);
            specCfg.putIfNotNull(DOWNLOAD_URL, newDownloadUrl);
            specCfg.put(BrooklynNode.DISTRO_UPLOAD_URL, inferUploadUrl(newDownloadUrl));
            
            specCfg.putAll(ConfigBag.newInstance(parameters.get(EXTRA_CONFIG)).getAllConfigAsConfigKeyMap());
            
            Map<ConfigKey<?>, Object> cfgLive = memberSpec.getConfigLive();
            cfgLive.clear();
            cfgLive.putAll(specCfg.getAllConfigAsConfigKeyMap());
            // not necessary, but good practice
            entity().setConfig(BrooklynCluster.MEMBER_SPEC, memberSpec);
            log.debug("Upgrading "+entity()+", new "+BrooklynCluster.MEMBER_SPEC+": "+memberSpec+" / "+specCfg);
            
            upgrade(parameters);
        } catch (Exception e) {
            log.debug("Upgrading "+entity()+" failed, will rethrow after restoring "+BrooklynCluster.MEMBER_SPEC+" to: "+origSpecCfg);
            Map<ConfigKey<?>, Object> cfgLive = memberSpec.getConfigLive();
            cfgLive.clear();
            cfgLive.putAll(origSpecCfg.getAllConfigAsConfigKeyMap());
            // not necessary, but good practice
            entity().setConfig(BrooklynCluster.MEMBER_SPEC, memberSpec);
            
            throw Exceptions.propagate(e);
            
        } finally {
            upgradeInProgress.set(false);
        }
        return null;
    }

    private String inferUploadUrl(String newDownloadUrl) {
        if (newDownloadUrl==null) return null;
        boolean isLocal = "file".equals(Urls.getProtocol(newDownloadUrl)) || new File(newDownloadUrl).exists();
        if (isLocal) {
            return newDownloadUrl;
        } else {
            return null;
        }
    }

    protected void upgrade(ConfigBag parameters) {
        //TODO currently this will fight with auto-scaler policies; they must be turned off for upgrade to work

        Group cluster = (Group)entity();
        Collection<Entity> initialMembers = cluster.getMembers();
        int initialClusterSize = initialMembers.size();
        
        if (!BrooklynNodeUpgradeEffectorBody.isPersistenceModeEnabled(cluster)) {
            // would could try a `forcePersistNow`, but that's sloppy; 
            // for now, require HA/persistence for upgrading 
            DynamicTasks.queue( Tasks.warning("Check persistence", 
                new IllegalStateException("Persistence does not appear to be enabled at this cluster. "
                + "Cluster upgrade will not succeed unless a custom launch script enables it.")) );
        }

        //1. Initially create a single node to check if it will launch successfully
        TaskAdaptable<Collection<Entity>> initialNodeTask = DynamicTasks.queue(newCreateNodesTask(1, "Creating first upgraded version node"));

        //2. If everything is OK with the first node launch the rest as well
        TaskAdaptable<Collection<Entity>> remainingNodesTask = DynamicTasks.queue(newCreateNodesTask(initialClusterSize - 1, "Creating remaining upgraded version nodes ("+(initialClusterSize - 1)+")"));

        //3. Once we have all nodes running without errors switch master
        DynamicTasks.queue(Effectors.invocation(cluster, BrooklynCluster.SELECT_MASTER, MutableMap.of(SelectMasterEffector.NEW_MASTER_ID, 
            Iterables.getOnlyElement(initialNodeTask.asTask().getUnchecked()).getId()))).asTask().getUnchecked();

        //4. Stop the nodes which were running at the start of the upgrade call, but keep them around.
        //   Should we create a quarantine-like zone for old stopped version?
        //   For members that were created meanwhile - they will be using the new version already. If the new version
        //   isn't good then they will fail to start as well, forcing the policies to retry (and succeed once the
        //   URL is reverted).
        //TODO can get into problem state if more old nodes are created; better might be to set the
        //version on this cluster before the above select-master call, and then delete any which are running the old
        //version (would require tracking the version number at the entity)
        HashSet<Entity> oldMembers = new HashSet<Entity>(initialMembers);
        oldMembers.removeAll(remainingNodesTask.asTask().getUnchecked());
        oldMembers.removeAll(initialNodeTask.asTask().getUnchecked());
        DynamicTasks.queue(Effectors.invocation(BrooklynNode.STOP_NODE_BUT_LEAVE_APPS, Collections.emptyMap(), oldMembers)).asTask().getUnchecked();
    }

    private TaskAdaptable<Collection<Entity>> newCreateNodesTask(int size, String name) {
        return Tasks.<Collection<Entity>>builder().name(name).body(new CreateNodesCallable(size)).build();
    }

    protected class CreateNodesCallable implements Callable<Collection<Entity>> {
        private final int size;
        public CreateNodesCallable(int size) {
            this.size = size;
        }
        @Override
        public Collection<Entity> call() throws Exception {
            return createNodes(size);
        }
    }

    protected Collection<Entity> createNodes(int nodeCnt) {
        DynamicCluster cluster = (DynamicCluster)entity();

        //1. Create the nodes
        Collection<Entity> newNodes = cluster.resizeByDelta(nodeCnt);

        //2. Wait for them to be RUNNING (or at least STARTING to have completed)
        // (should already be the case, because above is synchronous and, we think, it will fail if start does not succeed)
        DynamicTasks.queue(EntityTasks.awaitingAttribute(newNodes, Attributes.SERVICE_STATE_ACTUAL, 
                Predicates.not(Predicates.equalTo(Lifecycle.STARTING)), Duration.minutes(30)));

        //3. Set HOT_STANDBY in case it is not enabled on the command line ...
        DynamicTasks.queue(Effectors.invocation(
                BrooklynNode.SET_HA_MODE,
                MutableMap.of(SetHAModeEffector.MODE, HighAvailabilityMode.HOT_STANDBY), 
                newNodes)).asTask().getUnchecked();
        //... and wait until all of the nodes change state
        // TODO fail quicker if state changes to FAILED
        DynamicTasks.queue(EntityTasks.awaitingAttribute(newNodes, BrooklynNode.MANAGEMENT_NODE_STATE, 
                Predicates.equalTo(ManagementNodeState.HOT_STANDBY), Duration.FIVE_MINUTES));

        //5. Just in case check if all of the nodes are SERVICE_UP (which would rule out ON_FIRE as well)
        Collection<Entity> failedNodes = Collections2.filter(newNodes, EntityPredicates.attributeEqualTo(BrooklynNode.SERVICE_UP, Boolean.FALSE));
        if (!failedNodes.isEmpty()) {
            throw new IllegalStateException("Nodes " + failedNodes + " are not " + BrooklynNode.SERVICE_UP + " though successfully in " + ManagementNodeState.HOT_STANDBY);
        }
        return newNodes;
    }

}
