/*
 * Copyright 2012-2013 by Cloudsoft Corp.
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
import brooklyn.util.time.Duration;
import brooklyn.util.time.Time;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.netflix.astyanax.connectionpool.exceptions.ConnectionException;

/**
 * A live test of the {@link CassandraCluster} entity.
 *
 * Tests that a two node cluster can be started on Amazon EC2 and data written on one {@link CassandraNode}
 * can be read from another, using the Astyanax API.
 */
public class CassandraClusterLiveTest {

    private static final Logger log = LoggerFactory.getLogger(CassandraClusterLiveTest.class);
    
    private String provider = 
//            "rackspace-cloudservers-uk";
            "aws-ec2:eu-west-1";
//            "named:hpcloud-compute-at";
//            "localhost";

    protected TestApplication app;
    protected Location testLocation;
    protected CassandraCluster cluster;

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
            cluster = app.createAndManageChild(EntitySpec.create(CassandraCluster.class)
                    .configure("initialSize", 2)
                    .configure("clusterName", "CassandraClusterLiveTest"));
            assertEquals(cluster.getCurrentSize().intValue(), 0);

            app.start(ImmutableList.of(testLocation));

            EntityTestUtils.assertAttributeEqualsEventually(cluster, CassandraCluster.GROUP_SIZE, 2);
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
                if (fo && so && fc==Boolean.TRUE && sc==Boolean.TRUE && fp==2 && sp==2)
                    break;
                if (i==0) log.warn("NOT yet consistent, waiting");
                if (i==120)
                    Assert.fail("Did not become consistent in time: "+msg);
                Time.sleep(Duration.ONE_SECOND);
            }

            EntityTestUtils.assertAttributeEquals(first, CassandraNode.PEERS, 2);
            EntityTestUtils.assertAttributeEquals(second, CassandraNode.PEERS, 2);

            checkConnectionRepeatedly(1, first, second);
        } catch (Throwable e) {
            throw Throwables.propagate(e);
        }
    }

    protected void checkConnectionRepeatedly(int totalAttemptsAllowed, CassandraNode first, CassandraNode second) throws Exception {
        int attemptNum = 0;
        while (true) {
            try {
                checkConnection(first, second);
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

    protected void checkConnection(CassandraNode first, CassandraNode second) throws ConnectionException {
        // have been seeing intermittent SchemaDisagreementException errors on AWS, probably due to Astyanax / how we are using it
        // (confirmed that clocks are in sync)
        AstyanaxSample astyanaxFirst = new AstyanaxSample(first);
        Map<String, List<String>> versions = astyanaxFirst.getAstyanaxContextForCluster().getEntity().describeSchemaVersions();
        log.info("Cassandra schema versions are: "+versions);
        if (versions.size()>1) {
            Assert.fail("Inconsistent versions on Cassandra start: "+versions);
        }

        astyanaxFirst.writeData();

        AstyanaxSample astyanaxSecond = new AstyanaxSample(second);
        astyanaxSecond.readData();
    }

    protected Boolean areVersionsConsistent(CassandraNode node) {
        try {
            Map<String, List<String>> v = new AstyanaxSample(node).getAstyanaxContextForCluster().getEntity().describeSchemaVersions();
            return v.size() == 1;
        } catch (Exception e) {
            return null;
        }
    }

    protected boolean isSocketOpen(CassandraNode node) {
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
