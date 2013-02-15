/*
 * Copyright 2012-2013 by Cloudsoft Corp.
 */
package brooklyn.entity.nosql.couchdb

import static brooklyn.test.TestUtils.*
import static java.util.concurrent.TimeUnit.*
import static org.testng.Assert.*

import java.util.concurrent.TimeUnit

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.Entity
import brooklyn.entity.basic.ApplicationBuilder
import brooklyn.entity.basic.Attributes
import brooklyn.entity.basic.Entities
import brooklyn.entity.proxying.BasicEntitySpec
import brooklyn.entity.trait.Startable
import brooklyn.location.Location
import brooklyn.test.entity.TestApplication
import brooklyn.util.internal.TimeExtras

import com.google.common.base.Throwables
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.google.common.collect.Iterables
import com.netflix.astyanax.AstyanaxContext
import com.netflix.astyanax.Keyspace
import com.netflix.astyanax.MutationBatch
import com.netflix.astyanax.connectionpool.NodeDiscoveryType
import com.netflix.astyanax.connectionpool.OperationResult
import com.netflix.astyanax.connectionpool.exceptions.ConnectionException
import com.netflix.astyanax.connectionpool.impl.ConnectionPoolConfigurationImpl
import com.netflix.astyanax.connectionpool.impl.CountingConnectionPoolMonitor
import com.netflix.astyanax.impl.AstyanaxConfigurationImpl
import com.netflix.astyanax.model.Column
import com.netflix.astyanax.model.ColumnFamily
import com.netflix.astyanax.model.ColumnList
import com.netflix.astyanax.serializers.StringSerializer
import com.netflix.astyanax.thrift.ThriftFamilyFactory

/**
 * A live test of the {@link CouchDBCluster} entity.
 *
 * Tests that a two node cluster can be started on Amazon EC2 and data written on one {@link CouchDBNode}
 * can be read from another, using the Astyanax API.
 */
class CouchDBClusterLiveTest {
    protected static final Logger LOG = LoggerFactory.getLogger(CouchDBClusterLiveTest.class)

    static {
        TimeExtras.init()
    }

    // private String provider = "rackspace-cloudservers-uk"
    private String provider = "aws-ec2:eu-west-1"

    protected TestApplication app
    protected Location testLocation
    protected CouchDBCluster cluster

    @BeforeMethod(alwaysRun = true)
    public void setup() {
        app = ApplicationBuilder.builder(TestApplication.class).manage();
        testLocation = app.getManagementContext().getLocationRegistry().resolve(provider)
    }

    @AfterMethod(alwaysRun = true)
    public void shutdown() {
        Entities.destroyAll(app)
    }

    /**
     * Test that a two node cluster starts up and allows access via the Astyanax API through both nodes.
     */
    @Test(groups = "Live")
    public void canStartupAndShutdown() {
        cluster = app.createAndManageChild(BasicEntitySpec.newInstance(CouchDBCluster.class)
                .configure("initialSize", 2)
                .configure("clusterName", "AmazonCluster"));
        assertEquals cluster.currentSize, 0

        app.start(ImmutableList.of(testLocation))

        executeUntilSucceeds(timeout:2*TimeUnit.MINUTES) {
            assertEquals cluster.currentSize, 2
            cluster.members.each { Entity e ->
                assertTrue e.getAttribute(Startable.SERVICE_UP)
                assertEquals e.getAttribute(CouchDBNode.PEERS), 2
            }
        }

        Entities.dumpInfo(app)

        CouchDBNode first = Iterables.get(cluster.members, 0)
        CouchDBNode second = Iterables.get(cluster.members, 1)

        writeData(first)
        readData(second)
    }

    /**
     * Write to a {@link CouchDBNode} using the jcouchdb API.
     */
    protected void writeData(CouchDBNode couchdb) throws Exception {
    }

    /**
     * Read from a {@link CouchDBNode} using the jcouchdb API.
     */
    protected void readData(CouchDBNode couchdb) throws Exception {
    }
}
