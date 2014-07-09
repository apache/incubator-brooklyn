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
package brooklyn.entity.webapp;

import static brooklyn.test.HttpTestUtils.connectToUrl;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.webapp.tomcat.TomcatServer;
import brooklyn.entity.webapp.tomcat.TomcatServerImpl;
import brooklyn.location.LocationSpec;
import brooklyn.location.PortRange;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.location.basic.PortRanges;
import brooklyn.management.ManagementContext;
import brooklyn.policy.autoscaling.AutoScalerPolicy;
import brooklyn.test.Asserts;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.collections.MutableMap;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

public class TomcatAutoScalerPolicyTest {

    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(TomcatAutoScalerPolicyTest.class);

    // TODO Test is time-sensitive: we send two web-requests in rapid succession, and expect the average workrate to
    // be 2 msgs/sec; we then expect resizing to kick-in.
    // P speculate that... if for some reason things are running slow (e.g. GC during that one second), then brooklyn 
    // may not report the 2 msgs/sec.

    private LocalhostMachineProvisioningLocation loc;
    private TestApplication app;
    private ManagementContext managementContext;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        managementContext = app.getManagementContext();
        loc = managementContext.getLocationManager().createLocation(LocationSpec.create(LocalhostMachineProvisioningLocation.class)
                .configure("name", "london"));
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }
    
    @Test(groups="Integration")
    public void testWithTomcatServers() throws Exception {
        /*
         * One DynamicWebAppClster with auto-scaler policy
         * AutoScaler listening to DynamicWebAppCluster.TOTAL_REQS
         * AutoScaler minSize 1
         * AutoScaler upper metric 1
         * AutoScaler lower metric 0
         * .. send one request
         * wait til auto-scaling complete
         * assert cluster size 2
         */
        
        PortRange httpPort = PortRanges.fromString("7880+");
        PortRange jmxP = PortRanges.fromString("32199+");
        PortRange shutdownP = PortRanges.fromString("31880+");
        
        final DynamicWebAppCluster cluster = app.createAndManageChild(EntitySpec.create(DynamicWebAppCluster.class)
                .configure(DynamicWebAppCluster.INITIAL_SIZE, 1)
                .configure(DynamicWebAppCluster.MEMBER_SPEC, EntitySpec.create(TomcatServer.class)
                        .configure(TomcatServer.HTTP_PORT.getConfigKey(), httpPort)
                        .configure(TomcatServer.JMX_PORT.getConfigKey(), jmxP)
                        .configure(TomcatServer.SHUTDOWN_PORT, shutdownP)));

        final AutoScalerPolicy policy = AutoScalerPolicy.builder()
                .metric(DynamicWebAppCluster.REQUEST_COUNT_PER_NODE)
                .metricRange(0, 1)
                .minPoolSize(1)
                .build();
        cluster.addPolicy(policy);
        
        app.start(ImmutableList.of(loc));
        
        assertEquals(cluster.getCurrentSize(), (Integer)1);
        
        // Scaling based on *total requests* processed, rather than the requests per second.
        // So just hit it with 2 requests.
        // Alternatively could hit each tomcat server's URL twice per second; but that's less deterministic.
        TomcatServer tc = (TomcatServer) Iterables.getOnlyElement(cluster.getMembers());
        for (int i = 0; i < 2; i++) {
            connectToUrl(tc.getAttribute(TomcatServerImpl.ROOT_URL));
        }
        
        // We'll scale to two members as soon as the policy detects it.
        // But the second member won't count in the requests-per-node until it has started up.
        // Expect to see (if we polled at convenient times):
        //  - zero requests per node (because haven't yet retrieved over JMX the metric)
        //  - two requests per node, with one member
        //  - two requests per node, with two members (one of whom is still starting up, so doesn't count)
        //  - one request per node (i.e. two divided across the two active members)
        Asserts.succeedsEventually(MutableMap.of("timeout", 5*60*1000), new Runnable() {
            @Override public void run() {
                String err = "policy="+policy.isRunning()+"; size="+cluster.getCurrentSize()+"; reqCountPerNode="+cluster.getAttribute(DynamicWebAppCluster.REQUEST_COUNT_PER_NODE);
                assertTrue(policy.isRunning(), "err="+err);
                assertEquals(cluster.getCurrentSize(), (Integer)2, "err="+err);
                assertEquals(cluster.getAttribute(DynamicWebAppCluster.REQUEST_COUNT_PER_NODE), 1.0d, "err="+err);
            }});
    }
}
