package brooklyn.entity.nosql.mongodb;

import static org.testng.Assert.assertEquals;

import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import brooklyn.entity.AbstractEc2LiveTest;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.proxying.EntitySpecs;
import brooklyn.location.Location;
import brooklyn.test.EntityTestUtils;

import com.google.common.collect.ImmutableList;
import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;

public class MongoDbEc2LiveTest extends AbstractEc2LiveTest {

    // TODO Remove duplication from MongoDbTest (for insert/getById utility methods)
    
    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(MongoDbEc2LiveTest.class);

    private static final String TEST_DB = "test-db";
    private static final String TEST_COLLECTION = "test-collection";

    @Override
    protected void doTest(Location loc) throws Exception {
        MongoDbServer entity = app.createAndManageChild(EntitySpecs.spec(MongoDbServer.class));
        app.start(ImmutableList.of(loc));
        
        EntityTestUtils.assertAttributeEqualsEventually(entity, MongoDbServer.SERVICE_UP, true);

        String id = insert(entity, "hello", "world!");
        DBObject docOut = getById(entity, id);
        assertEquals(docOut.get("hello"), "world!");
    }

    /** Inserts new object with { key: value } at given server, returns new document's id 
     */
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

    /** Returns DBObject representing object with given id 
     */
    private DBObject getById(MongoDbServer entity, String id) throws Exception {
        MongoClient mongoClient = new MongoClient(entity.getAttribute(SoftwareProcess.HOSTNAME));
        try {
            DB db = mongoClient.getDB(TEST_DB);
            DBCollection testCollection = db.getCollection(TEST_COLLECTION);
            return testCollection.findOne(new BasicDBObject("_id", new ObjectId(id)));
        } finally {
            mongoClient.close();
        }
    }
    
    @Test(enabled=false)
    public void testDummy() {} // Convince TestNG IDE integration that this really does have test methods
}
