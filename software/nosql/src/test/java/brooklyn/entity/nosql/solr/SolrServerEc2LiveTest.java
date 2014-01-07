/*
 * Copyright 2012-2014 by Cloudsoft Corp.
 */
package brooklyn.entity.nosql.solr;

import static org.testng.Assert.assertEquals;

import org.apache.solr.common.SolrDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.AbstractEc2LiveTest;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.trait.Startable;
import brooklyn.location.Location;
import brooklyn.test.EntityTestUtils;
import brooklyn.util.collections.MutableMap;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;

public class SolrServerEc2LiveTest extends AbstractEc2LiveTest {

    private static final Logger log = LoggerFactory.getLogger(SolrServerEc2LiveTest.class);

    @Override
    protected void doTest(Location loc) throws Exception {
        log.info("Testing Solr on {}", loc);

        SolrServer solr = app.createAndManageChild(EntitySpec.create(SolrServer.class)
                .configure(SolrServer.SOLR_CORE_CONFIG, ImmutableMap.of("example", "classpath://solr/example.tgz")));
        app.start(ImmutableList.of(loc));

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
