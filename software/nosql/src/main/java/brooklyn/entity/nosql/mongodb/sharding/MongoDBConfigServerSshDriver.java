package brooklyn.entity.nosql.mongodb.sharding;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.nosql.mongodb.AbstractMongoDBSshDriver;
import brooklyn.entity.nosql.mongodb.MongoDBDriver;
import brooklyn.location.basic.SshMachineLocation;

public class MongoDBConfigServerSshDriver extends AbstractMongoDBSshDriver implements MongoDBDriver {
    
    private static final Logger LOG = LoggerFactory.getLogger(MongoDBConfigServerSshDriver.class);

    public MongoDBConfigServerSshDriver(MongoDBConfigServerImpl entity, SshMachineLocation machine) {
        super(entity, machine);
    }

    @Override
    public void customize() {
        // TODO Auto-generated method stub

    }

    @Override
    public void launch() {
        // TODO Auto-generated method stub

    }

}
