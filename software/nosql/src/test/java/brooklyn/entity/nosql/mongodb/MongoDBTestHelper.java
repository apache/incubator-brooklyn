package brooklyn.entity.nosql.mongodb;

import com.mongodb.*;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MongoDBTestHelper {

    private static final Logger LOG = LoggerFactory.getLogger(MongoDBTestHelper.class);

    private static final String TEST_DB = "brooklyn_test";
    private static final String TEST_COLLECTION = "test_collection";

    /**
     * Inserts a new object with { key: value } at given server.
     * @return The new document's id
     */
    public static String insert(MongoDBServer entity, String key, Object value) throws Exception {
        LOG.info("Inserting {}:{} at {}", new Object[]{key, value, entity});
        MongoClient mongoClient = new MongoClient(
                entity.getAttribute(MongoDBServer.HOSTNAME), entity.getAttribute(MongoDBServer.PORT));
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

    /** @return The {@link DBObject} representing the object with the given id */
    public static DBObject getById(MongoDBServer entity, String id) throws Exception {
        LOG.info("Getting {} from {}", new Object[]{id, entity});
        MongoClient mongoClient = new MongoClient(
                entity.getAttribute(MongoDBServer.HOSTNAME), entity.getAttribute(MongoDBServer.PORT));
        // Secondary preferred means the driver will let us read from secondaries too.
        mongoClient.setReadPreference(ReadPreference.secondaryPreferred());
        try {
            DB db = mongoClient.getDB(TEST_DB);
            DBCollection testCollection = db.getCollection(TEST_COLLECTION);
            return testCollection.findOne(new BasicDBObject("_id", new ObjectId(id)));
        } finally {
            mongoClient.close();
        }
    }

}
