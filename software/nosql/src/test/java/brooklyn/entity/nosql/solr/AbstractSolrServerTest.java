/*
 * Copyright 2012-2014 by Cloudsoft Corp.
 */
package brooklyn.entity.nosql.solr;

import org.testng.annotations.BeforeMethod;

import brooklyn.entity.BrooklynMgmtContextTestSupport;
import brooklyn.location.Location;

/**
 * Solr test framework for integration and live tests.
 */
public class AbstractSolrServerTest extends BrooklynMgmtContextTestSupport {

    protected Location testLocation;
    protected SolrServer solr;

    @BeforeMethod(alwaysRun = true)
    public void setUp() throws Exception {
        super.setUp();
        testLocation = app.newLocalhostProvisioningLocation();
    }

}
