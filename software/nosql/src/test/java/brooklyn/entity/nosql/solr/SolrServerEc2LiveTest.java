package brooklyn.entity.nosql.solr;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.AbstractEc2LiveTest;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.location.Location;
import brooklyn.test.EntityTestUtils;

import com.google.common.collect.ImmutableList;

public class SolrServerEc2LiveTest extends AbstractEc2LiveTest {

    private static final Logger log = LoggerFactory.getLogger(SolrServerEc2LiveTest.class);

    @Override
    protected void doTest(Location loc) throws Exception {
        log.info("Testing Solr on {}", loc);

        SolrServer solr = app.createAndManageChild(EntitySpec.create(SolrServer.class)
                .configure("solrPort", "9876+"));
        app.start(ImmutableList.of(loc));

        EntityTestUtils.assertAttributeEqualsEventually(solr, SolrServer.SERVICE_UP, true);
    }
}
