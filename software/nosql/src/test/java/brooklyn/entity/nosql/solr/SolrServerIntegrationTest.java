/*
 * Copyright 2012-2013 by Cloudsoft Corp.
 */
package brooklyn.entity.nosql.solr;

import org.testng.annotations.Test;

import brooklyn.entity.basic.Entities;
import brooklyn.entity.nosql.solr.SolrJSupport.SolrJSample;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.trait.Startable;
import brooklyn.test.EntityTestUtils;

import com.google.common.collect.ImmutableList;

/**
 * Solr integration tests.
 *
 * Test the operation of the {@link SolrServer} class.
 */
public class SolrServerIntegrationTest extends AbstractSolrServerTest {

    /**
     * Test that a node starts and sets SERVICE_UP correctly.
     */
    @Test(groups = "Integration")
    public void canStartupAndShutdown() {
        solr = app.createAndManageChild(EntitySpec.create(SolrServer.class));
        app.start(ImmutableList.of(testLocation));

        EntityTestUtils.assertAttributeEqualsEventually(solr, Startable.SERVICE_UP, true);
        Entities.dumpInfo(app);

        solr.stop();

        EntityTestUtils.assertAttributeEqualsEventually(solr, Startable.SERVICE_UP, false);
    }

    /**
     * Test that a node starts and sets SERVICE_UP correctly when a jmx port is supplied.
     */
    @Test(groups = "Integration")
    public void canStartupAndShutdownWithCustomJmx() {
        solr = app.createAndManageChild(EntitySpec.create(SolrServer.class)
                .configure("jmxPort", "11099+")
                .configure("rmiServerPort", "19001+"));
        app.start(ImmutableList.of(testLocation));

        EntityTestUtils.assertAttributeEqualsEventually(solr, Startable.SERVICE_UP, true);

        solr.stop();

        EntityTestUtils.assertAttributeEqualsEventually(solr, Startable.SERVICE_UP, false);
    }

    /**
     * Test that a keyspace and column family can be created and used with SolrJ client.
     */
    @Test(groups = "Integration")
    public void testConnection() throws Exception {
        solr = app.createAndManageChild(EntitySpec.create(SolrServer.class)
                .configure("solrPort", "9876+"));
        app.start(ImmutableList.of(testLocation));

        EntityTestUtils.assertAttributeEqualsEventually(solr, Startable.SERVICE_UP, true);
    }
}
