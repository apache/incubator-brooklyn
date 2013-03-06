/*
 * Copyright 2012-2013 by Cloudsoft Corp.
 */
package brooklyn.entity.nosql.couchdb;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.location.Location;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.internal.TimeExtras;

/**
 * CouchDB test framework for integration and live tests.
 */
public class AbstractCouchDBNodeTest {

    protected static final Logger log = LoggerFactory.getLogger(AbstractCouchDBNodeTest.class);

    static {
        TimeExtras.init();
    }

    protected TestApplication app;
    protected Location testLocation;
    protected CouchDBNode couchdb;

    @BeforeMethod(alwaysRun = true)
    public void setup() throws Exception {
        app = ApplicationBuilder.builder(TestApplication.class).manage();
        testLocation = new LocalhostMachineProvisioningLocation();
        // testLocation = app.managementContext.locationRegistry.resolve("named:test");
    }

    @AfterMethod(alwaysRun = true)
    public void shutdown() {
        Entities.destroyAll(app);
    }
}
