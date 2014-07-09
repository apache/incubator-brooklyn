package brooklyn.entity.nosql.mongodb.sharding;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.nosql.mongodb.AbstractMongoDBSshDriver;
import brooklyn.location.basic.SshMachineLocation;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;

public class MongoDBRouterSshDriver extends AbstractMongoDBSshDriver implements MongoDBRouterDriver {
    
    private static final Logger LOG = LoggerFactory.getLogger(MongoDBRouterSshDriver.class);

    public MongoDBRouterSshDriver(MongoDBRouterImpl entity, SshMachineLocation machine) {
        super(entity, machine);
    }
    
    @Override
    public void launch() {
        String configdb = Joiner.on(",").join(getEntity().getConfig(MongoDBRouter.CONFIG_SERVERS));
        ImmutableList.Builder<String> argsBuilder = getArgsBuilderWithDefaults(MongoDBRouterImpl.class.cast(getEntity()))
                .add("--configdb", configdb);
        
        String args = Joiner.on(" ").join(argsBuilder.build());
        String command = String.format("%s/bin/mongos %s > out.log 2> err.log < /dev/null", getExpandedInstallDir(), args);
        LOG.info(command);
        newScript(LAUNCHING)
                .updateTaskAndFailOnNonZeroResultCode()
                .body.append(command).execute();
    }

}
