package brooklyn.entity.nosql.mongodb;

import static org.testng.Assert.assertFalse
import static org.testng.Assert.assertEquals

import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.SoftwareProcessEntity;
import brooklyn.entity.trait.Startable;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.test.entity.TestApplication
import brooklyn.test.EntityTestUtils

import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;
import org.testng.annotations.BeforeMethod

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

    @BeforeMethod(alwaysRun = true)
    public void newTestApplication() {
        testApplication = new TestApplicationImpl()
        Entities.startManagement(testApplication);
    }

    @AfterMethod(alwaysRun = true)
    public void shutdownApp() {
        if (testApplication != null) {
            Entities.destroyAll(testApplication)
        }
    }

    @Test(groups = "Integration")
    public void testCanStartAndStop() {
        this.entity = new MongoDbServer(owner: testApplication);

        entity.start([ new LocalhostMachineProvisioningLocation(name:'london')]);
        EntityTestUtils.assertAttributeEqualsEventually(entity, Startable.SERVICE_UP, true);
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
        EntityTestUtils.assertAttributeEqualsEventually(entity, Startable.SERVICE_UP, true);

        insert(entity, "a", Boolean.TRUE);
        insert(entity, "b", Boolean.FALSE);
        EntityTestUtils.assertAttributeEqualsEventually(entity, MongoDbServer.OPCOUNTERS_INSERTS, new Long(2));

        entity.stop();
    }

    /** Inserts new object with { key: value } at given server, returns new document's id */
    private String insert(MongoDbServer entity, String key, Object value) {
        MongoClient mongoClient = new MongoClient(entity.getAttribute(SoftwareProcessEntity.HOSTNAME));
        try {
            DB db = mongoClient.getDB(TEST_DB);
            DBCollection testCollection = db.getCollection(TEST_COLLECTION);
            BasicDBObject doc = new BasicDBObject(key, value);
            testCollection.insert(doc);
            return doc.get("_id");
        } finally {
            mongoClient.close();
        }
    }

    /** Returns DBObject representing object with given id */
    private DBObject getById(MongoDbServer entity, String id) {
        MongoClient mongoClient = new MongoClient(entity.getAttribute(SoftwareProcessEntity.HOSTNAME));
        try {
            DB db = mongoClient.getDB(TEST_DB);
            DBCollection testCollection = db.getCollection(TEST_COLLECTION);
            DBObject doc = testCollection.findOne(new BasicDBObject("_id", new ObjectId(id)));
            return doc;
        } finally {
            mongoClient.close();
        }
    }
}
