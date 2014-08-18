/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package brooklyn.entity.nosql.mongodb;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.nosql.mongodb.sharding.MongoDBRouter;
import brooklyn.entity.nosql.mongodb.sharding.MongoDBRouterCluster;
import brooklyn.entity.nosql.mongodb.sharding.MongoDBShardedDeployment;
import brooklyn.entity.trait.Startable;
import brooklyn.event.basic.DependentConfiguration;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.management.Task;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.math.MathPredicates;

import com.google.common.base.Preconditions;
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
        
        List<String> scripts = entity.getConfig(MongoDBClient.STARTUP_JS_SCRIPTS);
        if (scripts!=null) {
            for (String scriptName : scripts) {
                try {
                    LOG.debug("Running MongoDB script "+scriptName+" at "+getEntity());
                    runScript("", scriptName, host, port);
                } catch (Exception e) {
                    LOG.warn("Error running MongoDB script "+scriptName+" at "+getEntity()+", throwing: "+e);
                    isRunning = false;
                    Exceptions.propagateIfFatal(e);
                    throw new IllegalStateException("Error running MongoDB script "+scriptName+" at "+entity+": "+e, e);
                }
            }
        }
        isRunning = true;
    }
    
    @Override
    public boolean isRunning() {
        // TODO better would be to get some confirmation
        return isRunning;
    }
    
    @Override
    public void stop() {
        try {
            super.stop();
        } finally {
            isRunning = false;
        }
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
            Preconditions.checkNotNull(deployment, "Either server or shardedDeployment must be specified for %s", this);
            server = DependentConfiguration.builder()
                    .attributeWhenReady(deployment.getRouterCluster(), MongoDBRouterCluster.ANY_ROUTER)
                    .blockingDetails("any available router")
                    .runNow();
            DependentConfiguration.builder()
                    .attributeWhenReady(server, MongoDBRouter.SHARD_COUNT)
                    .readiness(MathPredicates.<Integer>greaterThan(0))
                    .runNow();
        } else {
            if (deployment != null) {
                log.warn("Server and ShardedDeployment defined for {}; using server ({} instead of {})", 
                        new Object[] {this, server, deployment});
            }
            DependentConfiguration.builder()
                    .attributeWhenReady(server, Startable.SERVICE_UP)
                    .readiness(Predicates.equalTo(true))
                    .runNow();
        }
        return server;
    }
}
