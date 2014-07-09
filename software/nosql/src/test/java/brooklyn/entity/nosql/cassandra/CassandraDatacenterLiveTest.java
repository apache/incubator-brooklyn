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
package brooklyn.entity.nosql.cassandra;

import static org.testng.Assert.assertEquals;

import java.net.Socket;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.nosql.cassandra.AstyanaxSupport.AstyanaxSample;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.trait.Startable;
import brooklyn.location.Location;
import brooklyn.test.EntityTestUtils;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.text.Identifiers;
import brooklyn.util.time.Duration;
import brooklyn.util.time.Time;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.netflix.astyanax.connectionpool.exceptions.ConnectionException;

/**
 * A live test of the {@link CassandraDatacenter} entity.
 *
 * Tests that a two node cluster can be started on Amazon EC2 and data written on one {@link CassandraNode}
 * can be read from another, using the Astyanax API.
 */
public class CassandraDatacenterLiveTest {

    private static final Logger log = LoggerFactory.getLogger(CassandraDatacenterLiveTest.class);
    
    private String provider = 
//            "rackspace-cloudservers-uk";
            "aws-ec2:eu-west-1";
//            "named:hpcloud-compute-at";
//            "localhost";

    protected TestApplication app;
    protected Location testLocation;
    protected CassandraDatacenter cluster;

    @BeforeMethod(alwaysRun = true)
    public void setup() {
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        testLocation = app.getManagementContext().getLocationRegistry().resolve(provider);
    }

    @AfterMethod(alwaysRun = true)
    public void shutdown() {
        Entities.destroyAll(app.getManagementContext());
    }

    /**
     * Test that a two node cluster starts up and allows access via the Astyanax API through both nodes.
     */
    @Test(groups = "Live")
    public void canStartupAndShutdown() throws Exception {
        try {
            cluster = app.createAndManageChild(EntitySpec.create(CassandraDatacenter.class)
                    .configure("initialSize", 2)
                    .configure("clusterName", "CassandraClusterLiveTest"));
            assertEquals(cluster.getCurrentSize().intValue(), 0);

            app.start(ImmutableList.of(testLocation));

            EntityTestUtils.assertAttributeEqualsEventually(cluster, CassandraDatacenter.GROUP_SIZE, 2);
            Entities.dumpInfo(app);

            CassandraNode first = (CassandraNode) Iterables.get(cluster.getMembers(), 0);
            CassandraNode second = (CassandraNode) Iterables.get(cluster.getMembers(), 1);

            EntityTestUtils.assertAttributeEqualsEventually(first, Startable.SERVICE_UP, true);
            EntityTestUtils.assertAttributeEqualsEventually(second, Startable.SERVICE_UP, true);

            // may take some time to be consistent (with new thrift_latency checks on the node,
            // contactability should not be an issue, but consistency still might be) 
            for (int i=0; ; i++) {
                boolean fo = isSocketOpen(first);
                Boolean fc = fo ? areVersionsConsistent(first) : null;
                boolean so = isSocketOpen(second);
                Boolean sc = so ? areVersionsConsistent(second) : null;
                Integer fp = first.getAttribute(CassandraNode.PEERS);
                Integer sp = second.getAttribute(CassandraNode.PEERS);
                String msg = "consistency:  "
                        + "1: "+(!fo ? "unreachable" : fc==null ? "error" : fc)+"  "
                        + "2: "+(!so ? "unreachable" : sc==null ? "error" : sc)+";  "
                        + "peer group sizes: "+fp+","+sp;
                log.info(msg);
                if (fo && so && Boolean.TRUE.equals(fc) && Boolean.TRUE.equals(sc) && fp==2 && sp==2)
                    break;
                if (i==0) log.warn("NOT yet consistent, waiting");
                if (i==120)
                    Assert.fail("Did not become consistent in time: "+msg);
                Time.sleep(Duration.ONE_SECOND);
            }

            EntityTestUtils.assertAttributeEquals(first, CassandraNode.PEERS, 2);
            EntityTestUtils.assertAttributeEquals(second, CassandraNode.PEERS, 2);

            checkConnectionRepeatedly(2, 5, first, second);
        } catch (Throwable e) {
            throw Throwables.propagate(e);
        }
    }

    protected static void checkConnectionRepeatedly(int totalAttemptsAllowed, int numRetriesPerAttempt, CassandraNode first, CassandraNode second) throws Exception {
        int attemptNum = 0;
        while (true) {
            try {
                checkConnection(numRetriesPerAttempt, first, second);
                return;
            } catch (Exception e) {
                attemptNum++;
                if (attemptNum>=totalAttemptsAllowed) {
                    log.warn("Cassandra not usable, "+attemptNum+" attempts; failing: "+e, e);
                    throw e;                
                }
                log.warn("Cassandra not usable (attempt "+attemptNum+"), trying again after delay: "+e, e);
                Time.sleep(Duration.TEN_SECONDS);
            }
        }
    }

    protected static void checkConnection(int numRetries, CassandraNode first, CassandraNode second) throws ConnectionException {
        // have been seeing intermittent SchemaDisagreementException errors on AWS, probably due to Astyanax / how we are using it
        // (confirmed that clocks are in sync)
        String uniqueName = Identifiers.makeRandomId(8);
        AstyanaxSample astyanaxFirst = AstyanaxSample.builder().node(first).columnFamilyName(uniqueName).build();
        Map<String, List<String>> versions = astyanaxFirst.getAstyanaxContextForCluster().getEntity().describeSchemaVersions();
        log.info("Cassandra schema versions are: "+versions);
        if (versions.size()>1) {
            Assert.fail("Inconsistent versions on Cassandra start: "+versions);
        }

        astyanaxFirst.writeData(numRetries);

        AstyanaxSample astyanaxSecond = AstyanaxSample.builder().node(second).columnFamilyName(uniqueName).build();
        astyanaxSecond.readData(numRetries);
    }

    protected static Boolean areVersionsConsistent(CassandraNode node) {
        try {
            Map<String, List<String>> v = new AstyanaxSample(node).getAstyanaxContextForCluster().getEntity().describeSchemaVersions();
            return v.size() == 1;
        } catch (Exception e) {
            return null;
        }
    }

    protected static boolean isSocketOpen(CassandraNode node) {
        try {
            Socket s = new Socket(node.getAttribute(Attributes.HOSTNAME), node.getThriftPort());
            s.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    @Test(groups = "Live")
    public void tryTen() throws Exception {
        for (int i=0; i<10; i++) {
            log.info("RUN "+(i+1));
            canStartupAndShutdown();
            shutdown();
            setup();
        }
    }
    
}
