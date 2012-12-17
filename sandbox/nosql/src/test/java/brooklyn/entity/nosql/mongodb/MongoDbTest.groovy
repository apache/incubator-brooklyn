package brooklyn.entity.nosql.mongodb;

import brooklyn.entity.basic.AbstractApplication;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.SoftwareProcessEntity;
import brooklyn.entity.trait.Startable;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import static java.util.concurrent.TimeUnit.SECONDS
import brooklyn.test.entity.TestApplication

import static brooklyn.test.TestUtils.executeUntilSucceeds
import static org.testng.Assert.assertTrue
import static org.testng.Assert.assertFalse
import org.testng.annotations.BeforeMethod

import static org.testng.Assert.assertEquals
import com.mongodb.MongoClient
import com.mongodb.DB
import com.mongodb.DBCollection
import com.mongodb.BasicDBObject
import com.mongodb.DBObject;

public class MongoDbTest {

    TestApplication testApplication;
    SoftwareProcessEntity entity;

    @BeforeMethod(groups = "Integration")
    public void newTestApplication() {
        TestApplication ta = new TestApplication()
        Entities.startManagement(ta);
        testApplication = ta;
    }

    @AfterMethod(groups = "Integration", alwaysRun = true)
    public void shutdownApp() {
        if (entity != null) {
            AbstractApplication app = entity.getApplication();
            try {
                entity.stop();
            } finally {
                if (app != null) Entities.destroy(app);
            }
        }
    }

    @Test(groups = "Integration")
    public void testCanStartAndStop() {
        this.entity = new MongoDbServer(owner: testApplication);

        entity.start([ new LocalhostMachineProvisioningLocation(name:'london')]);
        executeUntilSucceeds(timeout: 30*SECONDS) {
            assertTrue entity.getAttribute(Startable.SERVICE_UP);
        }

        entity.stop();
        assertFalse entity.getAttribute(Startable.SERVICE_UP);
    }

    @Test(groups = "Integration")
    public void testCanReadAndWrite() {
        this.entity = new MongoDbServer(owner: testApplication);

        entity.start([ new LocalhostMachineProvisioningLocation(name:'london')]);

        MongoClient mongoClient = new MongoClient(entity.getAttribute(SoftwareProcessEntity.HOSTNAME));
        DB db = mongoClient.getDB("test-db");
        DBCollection testCollection = db.getCollection("testCollection");
        BasicDBObject doc = new BasicDBObject("hello", "world!")

        testCollection.insert(doc);
        assertEquals(testCollection.getCount(), 1L);

        DBObject docOut = testCollection.findOne();
        assertEquals(docOut.get("hello"), "world!")

        entity.stop();
    }
}
