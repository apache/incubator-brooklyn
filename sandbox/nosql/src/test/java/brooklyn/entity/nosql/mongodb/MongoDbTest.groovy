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
import com.mongodb.DBObject
import org.bson.types.ObjectId

public class MongoDbTest {

    private static final TEST_DB = "test-db";
    private static final TEST_COLLECTION = "test-collection";

    TestApplication testApplication;
    MongoDbServer entity;

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

        String id = insert(entity, "hello", "world!");
        DBObject docOut = getById(entity, id);
        assertEquals(docOut.get("hello"), "world!")

        entity.stop();
    }

    @Test(groups = "Integration")
    public void testPollInsertCountSensor() {
        this.entity = new MongoDbServer(owner: testApplication);
        entity.start([ new LocalhostMachineProvisioningLocation(name:'london')]);

        insert(entity, "a", Boolean.TRUE);
        insert(entity, "b", Boolean.FALSE);

        executeUntilSucceeds(timeout: 30*SECONDS) {
            assertEquals(entity.getAttribute(MongoDbServer.OPCOUNTERS_INSERTS), 2);
        }

        entity.stop();
    }

    /** Inserts new object with { key: value } at given server, returns new document's id */
    private String insert(MongoDbServer entity, String key, Object value) {
        MongoClient mongoClient = new MongoClient(entity.getAttribute(SoftwareProcessEntity.HOSTNAME));
        DB db = mongoClient.getDB(TEST_DB);
        DBCollection testCollection = db.getCollection(TEST_COLLECTION);
        BasicDBObject doc = new BasicDBObject(key, value);
        testCollection.insert(doc);
        mongoClient.close();
        return doc.get("_id");
    }

    /** Returns DBObject representing object with given id */
    private DBObject getById(MongoDbServer entity, String id) {
        MongoClient mongoClient = new MongoClient(entity.getAttribute(SoftwareProcessEntity.HOSTNAME));
        DB db = mongoClient.getDB(TEST_DB);
        DBCollection testCollection = db.getCollection(TEST_COLLECTION);
        DBObject doc = testCollection.findOne(new BasicDBObject("_id", new ObjectId(id)));
        mongoClient.close();
        return doc;
    }
}
