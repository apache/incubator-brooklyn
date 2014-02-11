package brooklyn.entity.nosql.mongodb;

import com.google.common.base.Throwables;
import com.mongodb.BasicDBObject;
import com.mongodb.CommandResult;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import com.mongodb.ReadPreference;

import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;

public class MongoDBTestHelper {

    private static final Logger LOG = LoggerFactory.getLogger(MongoDBTestHelper.class);

    private static final String TEST_DB = "brooklyn_test";
    private static final String TEST_COLLECTION = "test_collection";
    private static final String ADMIN_DB = "admin";

    /**
     * Inserts a new object with { key: value } at given server.
     * @return The new document's id
     */
    public static String insert(MongoDBServer entity, String key, Object value) {
        LOG.info("Inserting {}:{} at {}", new Object[]{key, value, entity});
        MongoClient mongoClient = clientForServer(entity);
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
    public static DBObject getById(MongoDBServer entity, String id) {
        LOG.info("Getting {} from {}", new Object[]{id, entity});
        MongoClient mongoClient = clientForServer(entity);
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
    
    public static List<String> getDatabaseNames(AbstractMongoDBServer entity) {
        LOG.info("Getting database names from {}", entity);
        MongoClient mongoClient = clientForServer(entity);
        try {
            return mongoClient.getDatabaseNames();
        } finally {
            mongoClient.close();
        }
    }
    
    public static boolean isConfigServer(AbstractMongoDBServer entity) {
        LOG.info("Checking if {} is a config server", entity);
        MongoClient mongoClient = clientForServer(entity);
        try {
            DB db = mongoClient.getDB(ADMIN_DB);
            CommandResult commandResult = db.command("getCmdLineOpts");
            Map<?, ?> parsedArgs = (Map<?, ?>)commandResult.get("parsed");
            if (parsedArgs == null) return false;
            Boolean configServer = (Boolean)parsedArgs.get("configsvr");
            return (configServer != null && configServer.equals(true));
        } finally {
            mongoClient.close();
        }
    }

    private static MongoClient clientForServer(AbstractMongoDBServer server) {
        try {
            return new MongoClient(server.getAttribute(MongoDBServer.HOSTNAME), server.getAttribute(MongoDBServer.PORT));
        } catch (UnknownHostException e) {
            // Fail whatever test called this method.
            throw Throwables.propagate(e);
        }
    }
}
