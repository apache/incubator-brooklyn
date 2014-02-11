package brooklyn.entity.nosql.mongodb;

import static com.google.common.base.Preconditions.checkState;
import brooklyn.location.basic.SshMachineLocation;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;

public class MongoDBSshDriver extends AbstractMongoDBSshDriver implements MongoDBDriver {

    public MongoDBSshDriver(MongoDBServerImpl entity, SshMachineLocation machine) {
        super(entity, machine);
    }

    @Override
    public MongoDBServerImpl getEntity() {
        return MongoDBServerImpl.class.cast(super.getEntity());
    }

    @Override
    public void launch() {
        MongoDBServer server = getEntity();

        ImmutableList.Builder<String> argsBuilder = getArgsBuilderWithDefaults(server);

        if (server.isReplicaSetMember()) {
            String replicaSetName = server.getReplicaSet().getName();
            checkState(!Strings.isNullOrEmpty(replicaSetName), "Replica set name must not be null or empty");
            argsBuilder.add("--replSet", replicaSetName);
        }

        if (Boolean.TRUE.equals(server.getConfig(MongoDBServer.ENABLE_REST_INTERFACE)))
            argsBuilder.add("--rest");

        launch(argsBuilder);
    }

}
