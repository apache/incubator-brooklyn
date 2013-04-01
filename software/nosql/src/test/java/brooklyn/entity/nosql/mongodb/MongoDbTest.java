package brooklyn.entity.nosql.mongodb;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;

import org.bson.types.ObjectId;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.proxying.EntitySpecs;
import brooklyn.entity.trait.Startable;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.test.EntityTestUtils;
import brooklyn.test.entity.TestApplication;

import com.google.common.collect.ImmutableList;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;

public class MongoDbTest {

    private static final String TEST_DB = "test-db";
    private static final String TEST_COLLECTION = "test-collection";

    private TestApplication app;
    private LocalhostMachineProvisioningLocation localhostProvisioningLocation;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
    	localhostProvisioningLocation = new LocalhostMachineProvisioningLocation();
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
    }

    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        if (app != null) Entities.destroyAll(app);
    }

    @Test(groups = "Integration")
    public void testCanStartAndStop() throws Exception {
        MongoDbServer entity = app.createAndManageChild(EntitySpecs.spec(MongoDbServer.class)
                .configure("mongodbConfTemplateUrl", "classpath:///test-mongodb.conf"));
        app.start(ImmutableList.of(localhostProvisioningLocation));

        EntityTestUtils.assertAttributeEqualsEventually(entity, Startable.SERVICE_UP, true);
        entity.stop();
        assertFalse(entity.getAttribute(Startable.SERVICE_UP));
    }

    @Test(groups = "Integration")
    public void testCanReadAndWrite() throws Exception {
        MongoDbServer entity = app.createAndManageChild(EntitySpecs.spec(MongoDbServer.class)
                .configure("mongodbConfTemplateUrl", "classpath:///test-mongodb.conf"));
        app.start(ImmutableList.of(localhostProvisioningLocation));

        String id = insert(entity, "hello", "world!");
        DBObject docOut = getById(entity, id);
        assertEquals(docOut.get("hello"), "world!");
    }

    @Test(groups = "Integration")
    public void testPollInsertCountSensor() throws Exception {
        MongoDbServer entity = app.createAndManageChild(EntitySpecs.spec(MongoDbServer.class)
                .configure("mongodbConfTemplateUrl", "classpath:///test-mongodb.conf"));
        app.start(ImmutableList.of(localhostProvisioningLocation));
        EntityTestUtils.assertAttributeEqualsEventually(entity, Startable.SERVICE_UP, true);

        EntityTestUtils.assertAttributeEqualsEventually(entity, MongoDbServer.OPCOUNTERS_INSERTS, new Long(0));
        insert(entity, "a", Boolean.TRUE);
        insert(entity, "b", Boolean.FALSE);
        EntityTestUtils.assertAttributeEqualsEventually(entity, MongoDbServer.OPCOUNTERS_INSERTS, new Long(2));
    }

    /** Inserts new object with { key: value } at given server, returns new document's id */
    private String insert(MongoDbServer entity, String key, Object value) throws Exception {
        MongoClient mongoClient = new MongoClient(entity.getAttribute(SoftwareProcess.HOSTNAME));
        try {
            DB db = mongoClient.getDB(TEST_DB);
            DBCollection testCollection = db.getCollection(TEST_COLLECTION);
            BasicDBObject doc = new BasicDBObject(key, value);
            testCollection.insert(doc);
            ObjectId id = (ObjectId) doc.get("_id");
            return id.toString();
        } finally {
            mongoClient.close();
        }
    }

    /** Returns DBObject representing object with given id */
    private DBObject getById(MongoDbServer entity, String id) throws Exception {
        MongoClient mongoClient = new MongoClient(entity.getAttribute(SoftwareProcess.HOSTNAME));
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
