package brooklyn.demo;

import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.nosql.mongodb.MongoDBReplicaSet;
import brooklyn.entity.nosql.mongodb.MongoDBServer;
import brooklyn.entity.proxying.EntitySpecs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SimpleMongoDBReplicaSet extends ApplicationBuilder {

    protected void doBuild() {
        addChild(EntitySpecs.spec(MongoDBReplicaSet.class)
            .configure("name", "Simple MongoDB replica set")
            .configure("initialSize", 3)
            .configure("replicaSetName", "simple-replica-set")
            .configure("memberSpec", EntitySpecs.spec(MongoDBServer.class)
                    .configure("mongodbConfTemplateUrl", "classpath:///mongodb.conf")
                    .configure("port", "27017+")));
    }

}
