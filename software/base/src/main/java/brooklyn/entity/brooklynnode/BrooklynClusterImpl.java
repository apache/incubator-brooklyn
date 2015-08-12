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
package brooklyn.entity.brooklynnode;

import java.util.Collection;
import java.util.concurrent.Callable;

import org.apache.brooklyn.management.ha.ManagementNodeState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.enricher.Enrichers;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.EntityFunctions;
import brooklyn.entity.basic.EntityPredicates;
import brooklyn.entity.basic.ServiceStateLogic;
import brooklyn.entity.basic.ServiceStateLogic.ServiceProblemsLogic;
import brooklyn.entity.brooklynnode.effector.BrooklynClusterUpgradeEffectorBody;
import brooklyn.entity.brooklynnode.effector.SelectMasterEffectorBody;
import brooklyn.entity.group.DynamicClusterImpl;
import brooklyn.event.feed.function.FunctionFeed;
import brooklyn.event.feed.function.FunctionPollConfig;
import brooklyn.util.time.Duration;

import com.google.common.collect.FluentIterable;
import com.google.common.collect.Iterables;

public class BrooklynClusterImpl extends DynamicClusterImpl implements BrooklynCluster {

    private static final String MSG_NO_MASTER = "No master node in cluster";
    private static final String MSG_TOO_MANY_MASTERS = "Too many master nodes in cluster";

    private static final Logger LOG = LoggerFactory.getLogger(BrooklynClusterImpl.class);

    // TODO should we set a default MEMBER_SPEC ?  difficult though because we'd need to set a password

    @Override
    public void init() {
        super.init();
        getMutableEntityType().addEffector(SelectMasterEffectorBody.SELECT_MASTER);
        getMutableEntityType().addEffector(BrooklynClusterUpgradeEffectorBody.UPGRADE_CLUSTER);

        ServiceProblemsLogic.updateProblemsIndicator(this, MASTER_NODE, MSG_NO_MASTER);
        addFeed(FunctionFeed.builder()
                .entity(this)
                .poll(new FunctionPollConfig<Object, BrooklynNode>(MASTER_NODE)
                        .period(Duration.ONE_SECOND)
                        .callable(new MasterChildFinder()))
                .build());
        
        addEnricher( Enrichers.builder().transforming(MASTER_NODE)
            .uniqueTag("master-node-web-uri")
            .publishing(BrooklynNode.WEB_CONSOLE_URI)
            .computing(EntityFunctions.attribute(BrooklynNode.WEB_CONSOLE_URI))
            .build() );
    }

    private final class MasterChildFinder implements Callable<BrooklynNode> {
        @Override
        public BrooklynNode call() throws Exception {
            return findMasterChild();
        }
    }

    BrooklynNode findMasterChild() {
        Collection<Entity> masters = FluentIterable.from(getMembers())
                .filter(EntityPredicates.attributeEqualTo(BrooklynNode.MANAGEMENT_NODE_STATE, ManagementNodeState.MASTER))
                .toList();

        if (masters.size() == 0) {
            ServiceProblemsLogic.updateProblemsIndicator(this, MASTER_NODE, MSG_NO_MASTER);
            return null;
            
        } else if (masters.size() == 1) {
            ServiceStateLogic.ServiceProblemsLogic.clearProblemsIndicator(this, MASTER_NODE);
            return (BrooklynNode)Iterables.getOnlyElement(masters);
            
        } else if (masters.size() == 2) {
            LOG.warn("Two masters detected, probably a handover just occured: " + masters);

            //Don't clearProblemsIndicator - if there were no masters previously why have two now.
            //But also don't set it. Probably hit a window where we have a new master
            //its BrooklynNode picked it up, but the BrooklynNode
            //for the old master hasn't refreshed its state yet.
            //Just pick one of them, should sort itself out in next update.
            
            //TODO Do set such indicator if this continues for an extended period of time
            
            return (BrooklynNode)masters.iterator().next();
            
        } else {
            ServiceProblemsLogic.updateProblemsIndicator(this, MASTER_NODE, MSG_TOO_MANY_MASTERS);
            String msg = "Multiple (>=3) master nodes in cluster: " + masters;
            LOG.error(msg);
            throw new IllegalStateException(msg);
            
        }
    }

}
