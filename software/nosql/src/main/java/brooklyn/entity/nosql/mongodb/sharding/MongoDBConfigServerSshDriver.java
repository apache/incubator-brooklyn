package brooklyn.entity.nosql.mongodb.sharding;

import brooklyn.entity.nosql.mongodb.AbstractMongoDBSshDriver;
import brooklyn.entity.nosql.mongodb.MongoDBDriver;
import brooklyn.location.basic.SshMachineLocation;

public class MongoDBConfigServerSshDriver extends AbstractMongoDBSshDriver implements MongoDBDriver {
    
    public MongoDBConfigServerSshDriver(MongoDBConfigServerImpl entity, SshMachineLocation machine) {
        super(entity, machine);
    }
    
    @Override
    public MongoDBConfigServerImpl getEntity() {
        return MongoDBConfigServerImpl.class.cast(super.getEntity());
    }

    @Override
    public void launch() {
        launch(getArgsBuilderWithDefaults(getEntity()).add("--configsvr"));
    }

}
