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
import org.testng.annotations.Test

import brooklyn.entity.proxying.BasicEntitySpec
import brooklyn.entity.trait.Startable

import com.google.common.collect.ImmutableList

/**
 * CouchDB integration tests.
 *
 * Test the operation of the {@link CouchDBNode} class.
 */
public class CouchDBNodeIntegrationTest extends AbstractCouchDBNodeTest {
    private static final Logger log = LoggerFactory.getLogger(CouchDBNodeIntegrationTest.class)

    /**
     * Test that a node starts and sets SERVICE_UP correctly.
     */
    @Test(groups = "Integration")
    public void canStartupAndShutdown() {
        couchdb = app.createAndManageChild(BasicEntitySpec.newInstance(CouchDBNode.class));
        app.start(ImmutableList.of(testLocation))
        executeUntilSucceedsWithShutdown(couchdb, timeout:2*TimeUnit.MINUTES) {
            assertTrue couchdb.getAttribute(Startable.SERVICE_UP)
        }
        assertFalse couchdb.getAttribute(Startable.SERVICE_UP)
    }

    /**
     * Test that a node can be used with jcouchdb client.
     */
    @Test(groups = "Integration")
    public void testConnection() throws Exception {
        couchdb = app.createAndManageChild(BasicEntitySpec.newInstance(CouchDBNode.class)
                .configure("httpPort", "12345+"));
        app.start(ImmutableList.of(testLocation))
        executeUntilSucceeds(timeout:2*TimeUnit.MINUTES) {
            assertTrue couchdb.getAttribute(Startable.SERVICE_UP)
        }

        jcouchdbTest(couchdb)
    }
}
