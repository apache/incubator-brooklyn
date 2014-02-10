package brooklyn.entity.nosql.mongodb;

import static com.google.common.base.Preconditions.checkState;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.net.Networking;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class MongoDBSshDriver extends AbstractMongoDBSshDriver implements MongoDBDriver {

    private static final Logger LOG = LoggerFactory.getLogger(MongoDBSshDriver.class);

    public MongoDBSshDriver(MongoDBServerImpl entity, SshMachineLocation machine) {
        super(entity, machine);
    }

    @Override
    public MongoDBServerImpl getEntity() {
        return MongoDBServerImpl.class.cast(super.getEntity());
    }

    public String getDataDirectory() {
        String result = entity.getConfig(MongoDBServer.DATA_DIRECTORY);
        if (result!=null) return result;
        return getRunDir() + "/data";
    }

    @Override
    public void customize() {
        Map<?,?> ports = ImmutableMap.of("port", getServerPort());
        Networking.checkPortsValid(ports);
        String command = String.format("mkdir -p %s", getDataDirectory());
        newScript(CUSTOMIZING)
                .updateTaskAndFailOnNonZeroResultCode()
                .body.append(command).execute();

        String templateUrl = entity.getConfig(MongoDBServer.MONGODB_CONF_TEMPLATE_URL);
        if (!Strings.isNullOrEmpty(templateUrl)) copyTemplate(templateUrl, getConfFile());
    }

    @Override
    public void launch() {
        MongoDBServer server = getEntity();
        Integer port = server.getAttribute(MongoDBServer.PORT);

        ImmutableList.Builder<String> argsBuilder = ImmutableList.<String>builder()
                .add("--config", getConfFile())
                .add("--pidfilepath", getPidFile())
                .add("--dbpath", getDataDirectory())
                .add("--logpath", getLogFile())
                .add("--port", port.toString())
                .add("--fork");

        if (server.isReplicaSetMember()) {
            String replicaSetName = server.getReplicaSet().getName();
            checkState(!Strings.isNullOrEmpty(replicaSetName), "Replica set name must not be null or empty");
            argsBuilder.add("--replSet", replicaSetName);
        }

        if (Boolean.TRUE.equals(server.getConfig(MongoDBServer.ENABLE_REST_INTERFACE)))
            argsBuilder.add("--rest");

        String args = Joiner.on(" ").join(argsBuilder.build());
        String command = String.format("%s/bin/mongod %s > out.log 2> err.log < /dev/null", getExpandedInstallDir(), args);
        LOG.info(command);
        newScript(LAUNCHING)
                .updateTaskAndFailOnNonZeroResultCode()
                .body.append(command).execute();

    }

    protected Integer getServerPort() {
        return entity.getAttribute(MongoDBServer.PORT);
    }

    private String getConfFile() {
        return getRunDir() + "/mongo.conf";
    }
}
