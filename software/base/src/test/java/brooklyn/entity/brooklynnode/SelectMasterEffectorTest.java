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

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;

import org.apache.brooklyn.management.ha.ManagementNodeState;
import org.apache.brooklyn.test.EntityTestUtils;
import org.apache.http.client.methods.HttpPost;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.BrooklynAppUnitTestSupport;
import brooklyn.entity.Entity;
import brooklyn.entity.Group;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.brooklynnode.BrooklynCluster.SelectMasterEffector;
import brooklyn.entity.brooklynnode.CallbackEntityHttpClient.Request;
import brooklyn.entity.effector.Effectors;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.event.feed.AttributePollHandler;
import brooklyn.event.feed.DelegatingPollHandler;
import brooklyn.event.feed.Poller;
import brooklyn.util.collections.MutableList;
import brooklyn.util.time.Duration;

import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableMap;

public class SelectMasterEffectorTest extends BrooklynAppUnitTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(BrooklynClusterImpl.class);

    protected BrooklynCluster cluster;
    protected HttpCallback http; 
    protected Poller<Void> poller;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        super.setUp();

        // because the effector calls wait for a state change, use a separate thread to drive that 
        poller = new Poller<Void>((EntityLocal)app, false);
        poller.scheduleAtFixedRate(
            new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    masterFailoverIfNeeded();
                    return null;
                }
            },
            new DelegatingPollHandler<Void>(Collections.<AttributePollHandler<? super Void>>emptyList()),
            Duration.millis(20));
        poller.start();
    }

    @Override
    protected void setUpApp() {
        super.setUpApp();
        http = new HttpCallback();
        cluster = app.createAndManageChild(EntitySpec.create(BrooklynCluster.class)
            .location(app.newLocalhostProvisioningLocation())
            .configure(BrooklynCluster.MEMBER_SPEC, EntitySpec.create(BrooklynNode.class)
                .impl(MockBrooklynNode.class)
                .configure(MockBrooklynNode.HTTP_CLIENT_CALLBACK, http)));
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        poller.stop();
        super.tearDown();
    }

    @Test
    public void testInvalidNewMasterIdFails() {
        try {
            selectMaster(cluster, "1234");
            fail("Non-existend entity ID provided.");
        } catch (Exception e) {
            assertTrue(e.toString().contains("1234 is not an ID of brooklyn node in this cluster"));
        }
    }

    @Test(groups="Integration") // because slow, due to sensor feeds
    public void testSelectMasterAfterChange() {
        List<Entity> nodes = makeTwoNodes();
        EntityTestUtils.assertAttributeEqualsEventually(cluster, BrooklynCluster.MASTER_NODE, (BrooklynNode)nodes.get(0));

        selectMaster(cluster, nodes.get(1).getId());
        checkMaster(cluster, nodes.get(1));
    }

    @Test
    public void testFindMaster() {
        List<Entity> nodes = makeTwoNodes();
        Assert.assertEquals(((BrooklynClusterImpl)Entities.deproxy(cluster)).findMasterChild(), nodes.get(0));
    }
    
    @Test(groups="Integration") // because slow, due to sensor feeds
    public void testSelectMasterFailsAtChangeState() {
        http.setFailAtStateChange(true);

        List<Entity> nodes = makeTwoNodes();
        
        EntityTestUtils.assertAttributeEqualsEventually(cluster, BrooklynCluster.MASTER_NODE, (BrooklynNode)nodes.get(0));

        try {
            selectMaster(cluster, nodes.get(1).getId());
            fail("selectMaster should have failed");
        } catch (Exception e) {
            // expected
        }
        checkMaster(cluster, nodes.get(0));
    }

    private List<Entity> makeTwoNodes() {
        List<Entity> nodes = MutableList.copyOf(cluster.resizeByDelta(2));
        setManagementState(nodes.get(0), ManagementNodeState.MASTER);
        setManagementState(nodes.get(1), ManagementNodeState.HOT_STANDBY);
        return nodes;
    }

    private void checkMaster(Group cluster, Entity node) {
        assertEquals(node.getAttribute(BrooklynNode.MANAGEMENT_NODE_STATE), ManagementNodeState.MASTER);
        assertEquals(cluster.getAttribute(BrooklynCluster.MASTER_NODE), node);
        for (Entity member : cluster.getMembers()) {
            if (member != node) {
                assertEquals(member.getAttribute(BrooklynNode.MANAGEMENT_NODE_STATE), ManagementNodeState.HOT_STANDBY);
            }
            assertEquals((int)member.getAttribute(MockBrooklynNode.HA_PRIORITY), 0);
        }
    }

    private static class HttpCallback implements Function<CallbackEntityHttpClient.Request, String> {
        private enum State {
            INITIAL,
            PROMOTED
        }
        private State state = State.INITIAL;
        private boolean failAtStateChange;

        @Override
        public String apply(Request input) {
            if ("/v1/server/ha/state".equals(input.getPath())) {
                if (failAtStateChange) {
                    throw new RuntimeException("Testing failure at changing node state");
                }

                checkRequest(input, HttpPost.METHOD_NAME, "/v1/server/ha/state", "mode", "HOT_STANDBY");
                Entity entity = input.getEntity();
                EntityTestUtils.assertAttributeEquals(entity, BrooklynNode.MANAGEMENT_NODE_STATE, ManagementNodeState.MASTER);
                EntityTestUtils.assertAttributeEquals(entity, MockBrooklynNode.HA_PRIORITY, 0);

                setManagementState(entity, ManagementNodeState.HOT_STANDBY);

                return "MASTER";
            } else {
                switch(state) {
                case INITIAL:
                    checkRequest(input, HttpPost.METHOD_NAME, "/v1/server/ha/priority", "priority", "1");
                    state = State.PROMOTED;
                    setPriority(input.getEntity(), Integer.parseInt(input.getParams().get("priority")));
                    return "0";
                case PROMOTED:
                    checkRequest(input, HttpPost.METHOD_NAME, "/v1/server/ha/priority", "priority", "0");
                    state = State.INITIAL;
                    setPriority(input.getEntity(), Integer.parseInt(input.getParams().get("priority")));
                    return "1";
                default: throw new IllegalStateException("Illegal call at state " + state + ". Request = " + input.getMethod() + " " + input.getPath());
                }
            }
        }

        public void checkRequest(Request input, String methodName, String path, String key, String value) {
            if (!input.getMethod().equals(methodName) || !input.getPath().equals(path)) {
                throw new IllegalStateException("Request doesn't match expected state. Expected = " + input.getMethod() + " " + input.getPath() + ". " +
                        "Actual = " + methodName + " " + path);
            }

            String inputValue = input.getParams().get(key);
            if(!Objects.equal(value, inputValue)) {
                throw new IllegalStateException("Request doesn't match expected parameter " + methodName + " " + path + ". Parameter " + key + 
                    " expected = " + value + ", actual = " + inputValue);
            }
        }

        public void setFailAtStateChange(boolean failAtStateChange) {
            this.failAtStateChange = failAtStateChange;
        }

    }

    private void masterFailoverIfNeeded() {
        if (!Entities.isManaged(cluster)) return;
        if (cluster.getAttribute(BrooklynCluster.MASTER_NODE) == null) {
            Collection<Entity> members = cluster.getMembers();
            if (members.size() > 0) {
                for (Entity member : members) {
                    if (member.getAttribute(MockBrooklynNode.HA_PRIORITY) == 1) {
                        masterFailover(member);
                        return;
                    }
                }
                masterFailover(members.iterator().next());
            }
        }
    }

    private void masterFailover(Entity member) {
        LOG.debug("Master failover to " + member);
        setManagementState(member, ManagementNodeState.MASTER);
        EntityTestUtils.assertAttributeEqualsEventually(cluster, BrooklynCluster.MASTER_NODE, (BrooklynNode)member);
        return;
    }

    public static void setManagementState(Entity entity, ManagementNodeState state) {
        ((EntityLocal)entity).setAttribute(BrooklynNode.MANAGEMENT_NODE_STATE, state);
    }

    public static void setPriority(Entity entity, int priority) {
        ((EntityLocal)entity).setAttribute(MockBrooklynNode.HA_PRIORITY, priority);
    }

    private void selectMaster(DynamicCluster cluster, String id) {
        app.getExecutionContext().submit(Effectors.invocation(cluster, BrooklynCluster.SELECT_MASTER, ImmutableMap.of(SelectMasterEffector.NEW_MASTER_ID.getName(), id))).asTask().getUnchecked();
    }

}
