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

import java.util.NoSuchElementException;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.brooklyn.management.ha.HighAvailabilityMode;
import org.apache.brooklyn.management.ha.ManagementNodeState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Effector;
import brooklyn.entity.Entity;
import brooklyn.entity.Group;
import brooklyn.entity.basic.EntityPredicates;
import brooklyn.entity.brooklynnode.BrooklynCluster;
import brooklyn.entity.brooklynnode.BrooklynCluster.SelectMasterEffector;
import brooklyn.entity.brooklynnode.BrooklynNode;
import brooklyn.entity.brooklynnode.BrooklynNode.SetHighAvailabilityModeEffector;
import brooklyn.entity.brooklynnode.BrooklynNode.SetHighAvailabilityPriorityEffector;
import brooklyn.entity.effector.EffectorBody;
import brooklyn.entity.effector.Effectors;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.repeat.Repeater;
import brooklyn.util.task.DynamicTasks;
import brooklyn.util.time.Duration;

import com.google.common.base.Preconditions;
import com.google.common.base.Objects;
import com.google.common.collect.Iterables;

public class SelectMasterEffectorBody extends EffectorBody<Void> implements SelectMasterEffector {
    public static final Effector<Void> SELECT_MASTER = Effectors.effector(SelectMasterEffector.SELECT_MASTER).impl(new SelectMasterEffectorBody()).build();
    
    private static final Logger LOG = LoggerFactory.getLogger(SelectMasterEffectorBody.class);

    private static final int HA_STANDBY_PRIORITY = 0;
    private static final int HA_MASTER_PRIORITY = 1;

    private AtomicBoolean selectMasterInProgress = new AtomicBoolean();

    @Override
    public Void call(ConfigBag parameters) {
        if (!selectMasterInProgress.compareAndSet(false, true)) {
            throw new IllegalStateException("A master change is already in progress.");
        }

        try {
            selectMaster(parameters);
        } finally {
            selectMasterInProgress.set(false);
        }
        return null;
    }

    private void selectMaster(ConfigBag parameters) {
        String newMasterId = parameters.get(NEW_MASTER_ID);
        Preconditions.checkNotNull(newMasterId, NEW_MASTER_ID.getName() + " parameter is required");

        final Entity oldMaster = entity().getAttribute(BrooklynCluster.MASTER_NODE);
        if (oldMaster != null && oldMaster.getId().equals(newMasterId)) {
            LOG.info(newMasterId + " is already the current master, no change needed.");
            return;
        }

        final Entity newMaster = getMember(newMasterId);

        //1. Increase the priority of the node we wish to become master
        toggleNodePriority(newMaster, HA_MASTER_PRIORITY);

        //2. Demote the existing master so a new election takes place
        try {
            // this allows the finally block to run even on failure
            DynamicTasks.swallowChildrenFailures();
            
            if (oldMaster != null) {
                demoteOldMaster(oldMaster, HighAvailabilityMode.HOT_STANDBY);
            }

            waitMasterHandover(oldMaster, newMaster);
        } finally { 
            //3. Revert the priority of the node once it has become master
            toggleNodePriority(newMaster, HA_STANDBY_PRIORITY);
        }

        checkMasterSelected(newMaster);
    }

    private void waitMasterHandover(final Entity oldMaster, final Entity newMaster) {
        boolean masterChanged = Repeater.create()
            .backoff(Repeater.DEFAULT_REAL_QUICK_PERIOD, 1.5, Duration.FIVE_SECONDS)
            .limitTimeTo(Duration.ONE_MINUTE)
            .until(new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    Entity master = getMasterNode();
                    return !Objects.equal(master, oldMaster) && master != null;
                }
            })
            .run();
        if (!masterChanged) {
            LOG.warn("Timeout waiting for node to become master: " + newMaster + ".");
        }
    }

    private void demoteOldMaster(Entity oldMaster, HighAvailabilityMode mode) {
        ManagementNodeState oldState = DynamicTasks.queue(
                Effectors.invocation(
                        oldMaster,
                        BrooklynNode.SET_HIGH_AVAILABILITY_MODE,
                        MutableMap.of(SetHighAvailabilityModeEffector.MODE, mode))
            ).asTask().getUnchecked();

        if (oldState != ManagementNodeState.MASTER) {
            LOG.warn("The previous HA state on node " + oldMaster.getId() + " was " + oldState +
                    ", while the expected value is " + ManagementNodeState.MASTER + ".");
        }
    }

    private void toggleNodePriority(Entity node, int newPriority) {
        Integer oldPriority = DynamicTasks.queue(
                Effectors.invocation(
                    node,
                    BrooklynNode.SET_HIGH_AVAILABILITY_PRIORITY,
                    MutableMap.of(SetHighAvailabilityPriorityEffector.PRIORITY, newPriority))
            ).asTask().getUnchecked();

        Integer expectedPriority = (newPriority == HA_MASTER_PRIORITY ? HA_STANDBY_PRIORITY : HA_MASTER_PRIORITY);
        if (oldPriority != expectedPriority) {
            LOG.warn("The previous HA priority on node " + node.getId() + " was " + oldPriority +
                    ", while the expected value is " + expectedPriority + " (while setting priority " +
                    newPriority + ").");
        }
    }

    private void checkMasterSelected(Entity newMaster) {
        Entity actualMaster = getMasterNode();
        if (actualMaster != newMaster) {
            throw new IllegalStateException("Expected node " + newMaster + " to be master, but found that " +
                    "master is " + actualMaster + " instead.");
        }
    }

    private Entity getMember(String memberId) {
        Group cluster = (Group)entity();
        try {
            return Iterables.find(cluster.getMembers(), EntityPredicates.idEqualTo(memberId));
        } catch (NoSuchElementException e) {
            throw new IllegalStateException(memberId + " is not an ID of brooklyn node in this cluster");
        }
    }

    private Entity getMasterNode() {
        return entity().getAttribute(BrooklynCluster.MASTER_NODE);
    }
}
