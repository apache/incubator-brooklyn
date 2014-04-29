package brooklyn.entity.nosql.mongodb;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;

import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.nosql.mongodb.sharding.MongoDBRouter;
import brooklyn.entity.nosql.mongodb.sharding.MongoDBRouterCluster;
import brooklyn.entity.nosql.mongodb.sharding.MongoDBShardedDeployment;
import brooklyn.entity.trait.Startable;
import brooklyn.event.basic.DependentConfiguration;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.management.Task;
import brooklyn.util.exceptions.Exceptions;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;

public class MongoDBClientSshDriver extends AbstractMongoDBSshDriver implements MongoDBClientDriver {
    
    private static final Logger LOG = LoggerFactory.getLogger(MongoDBClientSshDriver.class);

    private boolean isRunning = false;

    public MongoDBClientSshDriver(EntityLocal entity, SshMachineLocation machine) {
        super(entity, machine);
    }
    
    @Override
    public void customize() {
        String command = String.format("mkdir -p %s", getUserScriptDir());
        newScript(CUSTOMIZING)
            .updateTaskAndFailOnNonZeroResultCode()
            .body.append(command).execute();
        Map<String, String> scripts = entity.getConfig(MongoDBClient.JS_SCRIPTS);
        for (String scriptName : scripts.keySet()) {
            copyResource(scripts.get(scriptName), getUserScriptDir() + scriptName + ".js");
        }
    }

    @Override
    public void launch() {
        AbstractMongoDBServer server = getServer();
        String host = server.getAttribute(AbstractMongoDBServer.HOSTNAME);
        Integer port = server.getAttribute(AbstractMongoDBServer.PORT);
        try {
            for (String scriptName : entity.getConfig(MongoDBClient.STARTUP_JS_SCRIPTS)) {
                runScript("", scriptName, host, port);
            }
        } catch (NullPointerException e) {
            // FIXME avoid the null ptr, and do something more intelligent
            LOG.error("startupScripts not specified in MongoDBClientSshDriver launch method;", e);
            isRunning = false;
            return;
        }
        isRunning = true;
    }
    
    @Override
    public boolean isRunning() {
        return isRunning;
    }
    
    private String getUserScriptDir() {
        return getRunDir() + "/userScripts/" ;
    }
    
    public void runScript(String preStart, String scriptName) {
        AbstractMongoDBServer server = getServer();
        String host = server.getAttribute(AbstractMongoDBServer.HOSTNAME);
        Integer port = server.getAttribute(AbstractMongoDBServer.PORT);
        runScript(preStart, scriptName, host, port);
    }
    
    private void runScript(String preStart, String scriptName, String host, Integer port) {
        // TODO: escape preStart to prevent injection attack
        String command = String.format("%s/bin/mongo %s:%s --eval \"%s\" %s/%s > out.log 2> err.log < /dev/null", getExpandedInstallDir(), 
                host, port, preStart, getUserScriptDir(), scriptName + ".js");
        newScript(LAUNCHING)
            .updateTaskAndFailOnNonZeroResultCode()
            .body.append(command).execute();
    }
    
    private AbstractMongoDBServer getServer() {
        AbstractMongoDBServer server = entity.getConfig(MongoDBClient.SERVER);
        MongoDBShardedDeployment deployment = entity.getConfig(MongoDBClient.SHARDED_DEPLOYMENT);
        if (server == null) {
            Preconditions.checkNotNull(deployment, "Either server or shardedDeployment must be specified");
            Task<MongoDBRouter> task = DependentConfiguration.attributeWhenReady(deployment.getRouterCluster(),
                    MongoDBRouterCluster.ANY_ROUTER);
            try {
                server = DependentConfiguration.waitForTask(task, entity, "any available router");
            } catch (InterruptedException e) {
                throw Exceptions.propagate(e);
            }
            DependentConfiguration.waitInTaskForAttributeReady(server, MongoDBRouter.SHARD_COUNT, new Predicate<Integer>() {
                public boolean apply(Integer input) {
                    return input > 0;
                };
            });
        } else {
            if (deployment != null) {
                log.warn("Server and ShardedDeployment defined for {}; using server ({} instead of {})", 
                        new Object[] {this, server, deployment});
            }
            Task<Boolean> task = DependentConfiguration.attributeWhenReady(server, Startable.SERVICE_UP);
            try {
                DependentConfiguration.waitForTask(task, server);
            } catch (InterruptedException e) {
                throw Exceptions.propagate(e);
            }
            DependentConfiguration.waitInTaskForAttributeReady(server, Startable.SERVICE_UP, Predicates.equalTo(true));
        }
        return server;
    }
}
