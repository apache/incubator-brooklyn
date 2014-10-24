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
import java.util.concurrent.atomic.AtomicBoolean;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Effector;
import brooklyn.entity.Entity;
import brooklyn.entity.Group;
import brooklyn.entity.basic.EntityPredicates;
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
import brooklyn.event.AttributeSensor;
import brooklyn.management.ha.HighAvailabilityMode;
import brooklyn.management.ha.ManagementNodeState;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.net.Urls;
import brooklyn.util.repeat.Repeater;
import brooklyn.util.task.DynamicTasks;
import brooklyn.util.task.Tasks;
import brooklyn.util.time.Duration;

import com.google.api.client.util.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.Iterables;

public class BrooklynClusterUpgradeEffectorBody extends EffectorBody<Void> implements UpgradeClusterEffector {
    public static final Effector<Void> UPGRADE_CLUSTER = Effectors.effector(UpgradeClusterEffector.UPGRADE_CLUSTER).impl(new BrooklynClusterUpgradeEffectorBody()).build();

    private AtomicBoolean upgradeInProgress = new AtomicBoolean();

    @Override
    public Void call(ConfigBag parameters) {
        if (!upgradeInProgress.compareAndSet(false, true)) {
            throw new IllegalStateException("An upgrade is already in progress.");
        }

        EntitySpec<?> memberSpec = entity().getConfig(BrooklynCluster.MEMBER_SPEC);
        Preconditions.checkNotNull(memberSpec, BrooklynCluster.MEMBER_SPEC.getName() + " is required for " + UpgradeClusterEffector.class.getName());

        Map<ConfigKey<?>, Object> specCfg = memberSpec.getConfig();
        String oldDownloadUrl = (String) specCfg.get(BrooklynNode.DOWNLOAD_URL);
        String oldUploadUrl = (String) specCfg.get(BrooklynNode.DISTRO_UPLOAD_URL);
        String newDownloadUrl = parameters.get(BrooklynNode.DOWNLOAD_URL.getConfigKey());
        String newUploadUrl = inferUploadUrl(newDownloadUrl);
        try {
            memberSpec.configure(BrooklynNode.DOWNLOAD_URL, newUploadUrl);
            memberSpec.configure(BrooklynNode.DISTRO_UPLOAD_URL, newUploadUrl);
            upgrade(parameters);
        } catch (Exception e) {
            memberSpec.configure(BrooklynNode.DOWNLOAD_URL, oldDownloadUrl);
            memberSpec.configure(BrooklynNode.DISTRO_UPLOAD_URL, oldUploadUrl);
            throw Exceptions.propagate(e);
        } finally {
            upgradeInProgress.set(false);
        }
        return null;
    }

    private String inferUploadUrl(String newDownloadUrl) {
        boolean isLocal = "file".equals(Urls.getProtocol(newDownloadUrl)) || new File(newDownloadUrl).exists();
        if (isLocal) {
            return newDownloadUrl;
        } else {
            return null;
        }
    }

    private void upgrade(ConfigBag parameters) {
        //TODO might be worth separating each step in a task for better UI
        //TODO currently this will fight with auto-scaler policies; you should turn them off

        Group cluster = (Group)entity();
        Collection<Entity> initialMembers = cluster.getMembers();
        int initialClusterSize = initialMembers.size();

        //1. Initially create a single node to check if it will launch successfully
        Entity initialNode = Iterables.getOnlyElement(createNodes(1));

        //2. If everything is OK with the first node launch the rest as well
        Collection<Entity> remainingNodes = createNodes(initialClusterSize - 1);

        //3. Once we have all nodes running without errors switch master
        DynamicTasks.queue(Effectors.invocation(cluster, BrooklynCluster.SELECT_MASTER, MutableMap.of(SelectMasterEffector.NEW_MASTER_ID, initialNode.getId()))).asTask().getUnchecked();

        //4. Stop the nodes which were running at the start of the upgrade call, but keep them around.
        //   Should we create a quarantine-like zone for old stopped version?
        //   For members that were created meanwhile - they will be using the new version already. If the new version
        //   isn't good then they will fail to start as well, forcing the policies to retry (and succeed once the
        //   URL is reverted).
        //TODO can get into problem state if more old nodes are created; better might be to set the
        //version on this cluster before the above select-master call, and then delete any which are running the old
        //version (would require tracking the version number at the entity)
        HashSet<Entity> oldMembers = new HashSet<Entity>(initialMembers);
        oldMembers.removeAll(remainingNodes);
        oldMembers.remove(initialNode);
        DynamicTasks.queue(Effectors.invocation(BrooklynNode.STOP_NODE_BUT_LEAVE_APPS, Collections.emptyMap(), oldMembers)).asTask().getUnchecked();
    }

    private Collection<Entity> createNodes(int nodeCnt) {
        DynamicCluster cluster = (DynamicCluster)entity();

        //1. Create the nodes
        Collection<Entity> newNodes = cluster.resizeByDelta(nodeCnt);

        //2. Wait for them to be RUNNING
        waitAttributeNotEqualTo(
                newNodes,
                BrooklynNode.SERVICE_STATE_ACTUAL, Lifecycle.STARTING);

        //3. Set HOT_STANDBY in case it is not enabled on the command line ...
        DynamicTasks.queue(Effectors.invocation(
                BrooklynNode.SET_HA_MODE,
                MutableMap.of(SetHAModeEffector.MODE, HighAvailabilityMode.HOT_STANDBY), 
                newNodes)).asTask().getUnchecked();

        //4. ... and wait until all of the nodes change state
        //TODO if the REST call is blocking this is not needed
        waitAttributeEqualTo(
                newNodes,
                BrooklynNode.MANAGEMENT_NODE_STATE,
                ManagementNodeState.HOT_STANDBY);

        //5. Just in case check if all of the nodes are SERVICE_UP (which would rule out ON_FIRE as well)
        Collection<Entity> failedNodes = Collections2.filter(newNodes, EntityPredicates.attributeEqualTo(BrooklynNode.SERVICE_UP, Boolean.FALSE));
        if (!failedNodes.isEmpty()) {
            throw new IllegalStateException("Nodes " + failedNodes + " are not " + BrooklynNode.SERVICE_UP + " though successfully in " + ManagementNodeState.HOT_STANDBY);
        }
        return newNodes;
    }

    private <T> void waitAttributeEqualTo(Collection<Entity> nodes, AttributeSensor<T> sensor, T value) {
        waitPredicate(
                nodes, 
                EntityPredicates.attributeEqualTo(sensor, value),
                "Waiting for nodes " + nodes + ", sensor " + sensor + " to be " + value,
                "Timeout while waiting for nodes " + nodes + ", sensor " + sensor + " to change to " + value);
    }

    private <T> void waitAttributeNotEqualTo(Collection<Entity> nodes, AttributeSensor<T> sensor, T value) {
        waitPredicate(
                nodes, 
                EntityPredicates.attributeNotEqualTo(sensor, value),
                "Waiting for nodes " + nodes + ", sensor " + sensor + " to change from " + value,
                "Timeout while waiting for nodes " + nodes + ", sensor " + sensor + " to change from " + value);
    }

    private <T extends Entity> void waitPredicate(Collection<T> nodes, Predicate<T> waitPredicate, String blockingMsg, String errorMsg) {
        Tasks.setBlockingDetails(blockingMsg);
        boolean pollSuccess = Repeater.create(blockingMsg)
            .backoff(Duration.ONE_SECOND, 1.2, Duration.TEN_SECONDS)
            .limitTimeTo(Duration.ONE_HOUR)
            .until(nodes, allMatch(waitPredicate))
            .run();
        Tasks.resetBlockingDetails();

        if (!pollSuccess) {
            throw new IllegalStateException(errorMsg);
        }
    }

    public static <T> Predicate<Collection<T>> allMatch(final Predicate<T> predicate) {
        return new Predicate<Collection<T>>() {
            @Override
            public boolean apply(Collection<T> input) {
                return Iterables.all(input, predicate);
            }
        };
    }
}
