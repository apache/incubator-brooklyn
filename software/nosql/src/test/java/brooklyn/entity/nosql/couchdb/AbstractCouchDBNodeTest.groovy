/*
 * Copyright 2012-2013 by Cloudsoft Corp.
 */
package brooklyn.entity.nosql.couchdb

import static brooklyn.test.TestUtils.*
import static java.util.concurrent.TimeUnit.*
import static org.testng.Assert.*

import org.jcouchdb.db.Database
import org.jcouchdb.db.Server
import org.jcouchdb.db.ServerImpl
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod

import brooklyn.entity.basic.ApplicationBuilder
import brooklyn.entity.basic.Attributes
import brooklyn.entity.basic.Entities
import brooklyn.location.Location
import brooklyn.location.basic.LocalhostMachineProvisioningLocation
import brooklyn.test.entity.TestApplication
import brooklyn.util.internal.TimeExtras

/**
 * CouchDB test framework for integration and live tests, using Astyanax API.
 */
public class AbstractCouchDBNodeTest {
    private static final Logger log = LoggerFactory.getLogger(AbstractCouchDBNodeTest.class)

    static {
        TimeExtras.init()
    }

    protected TestApplication app
    protected Location testLocation
    protected CouchDBNode couchdb

    @BeforeMethod(alwaysRun = true)
    public void setup() throws Exception {
        app = ApplicationBuilder.builder(TestApplication.class).manage();
        testLocation = new LocalhostMachineProvisioningLocation()
        // testLocation = app.managementContext.locationRegistry.resolve("named:test")
    }

    @AfterMethod(alwaysRun = true)
    public void shutdown() {
        Entities.destroyAll(app)
    }

    /**
     * Exercise the {@link CouchDBNode} using the jcouchdb API.
     */
    protected void jcouchdbTest(CouchDBNode node) throws Exception {
        Server server = new ServerImpl(node.getAttribute(Attributes.HOSTNAME), node.getHttpPort());
        assertTrue server.createDatabase("brooklyn");

        Database db = new Database(node.getAttribute(Attributes.HOSTNAME), node.getHttpPort(), "brooklyn");

        // create a hash map document with two fields
        Map<String,String> doc = new HashMap<String, String>();
        doc.put("first", "one");
        doc.put("second", "two");

        // create the document in couchdb
        int before = db.listDocuments(null, null).getTotalRows();
        db.createDocument(doc);
        int after = db.listDocuments(null, null).getTotalRows();

        assertEquals(before + 1, after);
    }
}
