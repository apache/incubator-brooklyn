/*
 * Copyright 2012-2014 by Cloudsoft Corp.
 */
package brooklyn.entity.nosql.solr;

import static org.testng.Assert.assertEquals;

import org.apache.solr.common.SolrDocument;
import org.testng.annotations.Test;

import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.trait.Startable;
import brooklyn.test.EntityTestUtils;
import brooklyn.util.collections.MutableMap;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;

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
     * Test that a core can be created and used with SolrJ client.
     */
    @Test(groups = "Integration")
    public void testConnection() throws Exception {
        solr = app.createAndManageChild(EntitySpec.create(SolrServer.class)
                .configure(SolrServer.SOLR_CORE_CONFIG, ImmutableMap.of("example", "classpath://solr/example.tgz")));
        app.start(ImmutableList.of(testLocation));

        EntityTestUtils.assertAttributeEqualsEventually(solr, Startable.SERVICE_UP, true);

        SolrJSupport client = new SolrJSupport(solr);

        Iterable<SolrDocument> results = client.getDocuments();
        assertEquals(0, Iterables.size(results));

        client.addDocument(MutableMap.<String, Object>of("id", "1", "description", "first"));
        client.addDocument(MutableMap.<String, Object>of("id", "2", "description", "second"));
        client.addDocument(MutableMap.<String, Object>of("id", "3", "description", "third"));

        assertEquals(3, Iterables.size(results));
    }
}
