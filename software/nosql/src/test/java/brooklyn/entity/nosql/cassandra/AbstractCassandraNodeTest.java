package brooklyn.entity.nosql.cassandra;

import org.testng.annotations.BeforeMethod;

import brooklyn.entity.BrooklynAppLiveTestSupport;
import brooklyn.location.Location;

/**
 * Cassandra test framework for integration and live tests.
 */
public class AbstractCassandraNodeTest extends BrooklynAppLiveTestSupport {

    protected Location testLocation;
    protected CassandraNode cassandra;

    @BeforeMethod(alwaysRun = true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        testLocation = app.newLocalhostProvisioningLocation();
    }

}
