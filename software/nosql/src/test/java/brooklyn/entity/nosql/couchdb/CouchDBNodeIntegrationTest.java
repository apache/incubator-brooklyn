/*
 * Copyright 2012-2013 by Cloudsoft Corp.
 */
package brooklyn.entity.nosql.couchdb;

import org.testng.annotations.Test;

import brooklyn.entity.proxying.BasicEntitySpec;
import brooklyn.entity.trait.Startable;
import brooklyn.test.EntityTestUtils;

import com.google.common.collect.ImmutableList;

/**
 * CouchDB integration tests.
 *
 * Test the operation of the {@link CouchDBNode} class.
 */
public class CouchDBNodeIntegrationTest extends AbstractCouchDBNodeTest {

    /**
     * Test that a node starts and sets SERVICE_UP correctly.
     */
    @Test(groups = "Integration")
    public void canStartupAndShutdown() {
        couchdb = app.createAndManageChild(BasicEntitySpec.newInstance(CouchDBNode.class)
                .configure("httpPort", "8000+"));
        app.start(ImmutableList.of(testLocation));

        EntityTestUtils.assertAttributeEqualsEventually(couchdb, Startable.SERVICE_UP, true);

        couchdb.stop();

        EntityTestUtils.assertAttributeEquals(couchdb, Startable.SERVICE_UP, false);
    }

    /**
     * Test that a node can be used with jcouchdb client.
     */
    @Test(groups = "Integration")
    public void testConnection() throws Exception {
        couchdb = app.createAndManageChild(BasicEntitySpec.newInstance(CouchDBNode.class)
                .configure("httpPort", "8000+"));
        app.start(ImmutableList.of(testLocation));

        EntityTestUtils.assertAttributeEqualsEventually(couchdb, Startable.SERVICE_UP, true);

        JcouchdbSupport jcouchdb = new JcouchdbSupport(couchdb);
        jcouchdb.jcouchdbTest();
    }
}
