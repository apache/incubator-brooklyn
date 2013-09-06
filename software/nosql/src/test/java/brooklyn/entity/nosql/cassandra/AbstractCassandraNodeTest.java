/*
 * Copyright 2012-2013 by Cloudsoft Corp.
 */
package brooklyn.entity.nosql.cassandra;

import org.testng.annotations.BeforeMethod;

import brooklyn.entity.BrooklynMgmtContextTestSupport;
import brooklyn.location.Location;

/**
 * Cassandra test framework for integration and live tests.
 */
public class AbstractCassandraNodeTest extends BrooklynMgmtContextTestSupport {

    protected Location testLocation;
    protected CassandraNode cassandra;

    @BeforeMethod(alwaysRun = true)
    public void setUp() throws Exception {
        super.setUp();
        testLocation = app.newLocalhostProvisioningLocation();
    }

}
