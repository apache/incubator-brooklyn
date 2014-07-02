package brooklyn.entity.nosql.solr;

import org.testng.annotations.BeforeMethod;

import brooklyn.entity.BrooklynAppLiveTestSupport;
import brooklyn.location.Location;

/**
 * Solr test framework for integration and live tests.
 */
public class AbstractSolrServerTest extends BrooklynAppLiveTestSupport {

    protected Location testLocation;
    protected SolrServer solr;

    @BeforeMethod(alwaysRun = true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        testLocation = app.newLocalhostProvisioningLocation();
    }

}
