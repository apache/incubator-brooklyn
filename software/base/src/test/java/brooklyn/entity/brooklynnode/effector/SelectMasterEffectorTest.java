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

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.Callable;

import org.apache.http.client.methods.HttpPost;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.Entity;
import brooklyn.entity.Group;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.BasicApplication;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.brooklynnode.BrooklynCluster;
import brooklyn.entity.brooklynnode.BrooklynCluster.SelectMasterEffector;
import brooklyn.entity.brooklynnode.BrooklynClusterImpl;
import brooklyn.entity.brooklynnode.BrooklynNode;
import brooklyn.entity.brooklynnode.effector.CallbackEntityHttpClient.Request;
import brooklyn.entity.effector.Effectors;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.event.feed.AttributePollHandler;
import brooklyn.event.feed.DelegatingPollHandler;
import brooklyn.event.feed.Poller;
import brooklyn.event.feed.function.FunctionFeed;
import brooklyn.management.ManagementContext;
import brooklyn.management.ha.ManagementNodeState;
import brooklyn.test.EntityTestUtils;
import brooklyn.test.entity.LocalManagementContextForTests;
import brooklyn.util.task.BasicExecutionContext;
import brooklyn.util.task.BasicExecutionManager;
import brooklyn.util.time.Duration;

import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;

public class SelectMasterEffectorTest {
    private static final Logger LOG = LoggerFactory.getLogger(BrooklynClusterImpl.class);

    protected ManagementContext mgmt;
    protected BasicApplication app;
    protected BasicExecutionContext ec;
    protected BrooklynCluster cluster;
    protected FunctionFeed scanMaster;
    protected Poller<Void> poller; 

    @BeforeMethod
    public void setUp() {
        mgmt = new LocalManagementContextForTests();
        EntitySpec<BasicApplication> appSpec = EntitySpec.create(BasicApplication.class)
                .child(EntitySpec.create(BrooklynCluster.class));
        app = ApplicationBuilder.newManagedApp(appSpec, mgmt);
        cluster = (BrooklynCluster)Iterables.getOnlyElement(app.getChildren());

        BasicExecutionManager em = new BasicExecutionManager("mycontext");
        ec = new BasicExecutionContext(em);

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
            Duration.millis(200));
        poller.start();
    }

    @AfterMethod
    public void tearDown() {
        poller.stop();
    }

    @Test
    public void testInvalidNewMasterIdFails() {
        try {
            BrooklynCluster cluster = app.addChild(EntitySpec.create(BrooklynCluster.class));
            selectMaster(cluster, "1234");
            fail("Non-existend entity ID provided.");
        } catch (Exception e) {
            assertTrue(e.toString().contains("1234 is not an ID of brooklyn node in this cluster"));
        }
    }

    @Test
    public void testSelectMaster() {
        HttpCallback cb = new HttpCallback();
        BrooklynNode node1 = cluster.addMemberChild(EntitySpec.create(BrooklynNode.class)
                .impl(TestHttpEntity.class)
                .configure(TestHttpEntity.HTTP_CLIENT_CALLBACK, cb));
        BrooklynNode node2 = cluster.addMemberChild(EntitySpec.create(BrooklynNode.class)
                .impl(TestHttpEntity.class)
                .configure(TestHttpEntity.HTTP_CLIENT_CALLBACK, cb));

        cluster.addMemberChild(node1);
        cluster.addMemberChild(node2);

        setManagementState(node1, ManagementNodeState.MASTER);
        EntityTestUtils.assertAttributeEqualsEventually(cluster, BrooklynCluster.MASTER_NODE, node1);

        selectMaster(cluster, node2.getId());
        checkMaster(cluster, node2);
    }

    @Test(groups="WIP")
    //after throwing an exception in HttpCallback tasks are no longer executed, why?
    public void testSelectMasterFailsAtChangeState() {
        HttpCallback cb = new HttpCallback();
        cb.setFailAtStateChange(true);

        BrooklynNode node1 = cluster.addMemberChild(EntitySpec.create(BrooklynNode.class)
                .impl(TestHttpEntity.class)
                .configure(TestHttpEntity.HTTP_CLIENT_CALLBACK, cb));
        BrooklynNode node2 = cluster.addMemberChild(EntitySpec.create(BrooklynNode.class)
                .impl(TestHttpEntity.class)
                .configure(TestHttpEntity.HTTP_CLIENT_CALLBACK, cb));

        cluster.addMemberChild(node1);
        cluster.addMemberChild(node2);

        setManagementState(node1, ManagementNodeState.MASTER);
        EntityTestUtils.assertAttributeEqualsEventually(cluster, BrooklynCluster.MASTER_NODE, node1);

        selectMaster(cluster, node2.getId());
        checkMaster(cluster, node1);
    }

    private void checkMaster(Group cluster, Entity node) {
        assertEquals(node.getAttribute(BrooklynNode.MANAGEMENT_NODE_STATE), ManagementNodeState.MASTER);
        assertEquals(cluster.getAttribute(BrooklynCluster.MASTER_NODE), node);
        for (Entity member : cluster.getMembers()) {
            if (member != node) {
                assertEquals(member.getAttribute(BrooklynNode.MANAGEMENT_NODE_STATE), ManagementNodeState.HOT_STANDBY);
            }
            assertEquals((int)member.getAttribute(TestHttpEntity.HA_PRIORITY), 0);
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
                    throw new RuntimeException("Testing failure at chaning node state");
                }

                checkRequest(input, HttpPost.METHOD_NAME, "/v1/server/ha/state", "mode", "HOT_STANDBY");
                Entity entity = input.getEntity();
                EntityTestUtils.assertAttributeEquals(entity, BrooklynNode.MANAGEMENT_NODE_STATE, ManagementNodeState.MASTER);
                EntityTestUtils.assertAttributeEquals(entity, TestHttpEntity.HA_PRIORITY, 0);

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

        public void checkRequest(Request input, String methodName, String path, String... keyValue) {
            if (!input.getMethod().equals(methodName) || !input.getPath().equals(path)) {
                throw new IllegalStateException("Request doesn't match expected state. Expected = " + input.getMethod() + " " + input.getPath() + ". " +
                        "Actual = " + methodName + " " + path);
            }
            for(int i = 0; i < keyValue.length / 2; i++) {
                String key = keyValue[i];
                String value = keyValue[i+1];
                String inputValue = input.getParams().get(key);
                if(!Objects.equal(value, inputValue)) {
                    throw new IllegalStateException("Request doesn't match expected parameter " + methodName + " " + path + ". Parameter " + key + 
                            " expected = " + value + ", actual = " + inputValue);
                }
            }
        }

        public void setFailAtStateChange(boolean failAtStateChange) {
            this.failAtStateChange = failAtStateChange;
        }

    }

    private void masterFailoverIfNeeded() {
        if (cluster.getAttribute(BrooklynCluster.MASTER_NODE) == null) {
            Collection<Entity> members = cluster.getMembers();
            if (members.size() > 0) {
                for (Entity member : members) {
                    if (member.getAttribute(TestHttpEntity.HA_PRIORITY) == 1) {
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
        ((EntityLocal)entity).setAttribute(TestHttpEntity.HA_PRIORITY, priority);
    }

    private void selectMaster(DynamicCluster cluster, String id) {
        ec.submit(Effectors.invocation(cluster, BrooklynCluster.SELECT_MASTER, ImmutableMap.of(SelectMasterEffector.NEW_MASTER_ID.getName(), id))).asTask().getUnchecked();
    }

}
