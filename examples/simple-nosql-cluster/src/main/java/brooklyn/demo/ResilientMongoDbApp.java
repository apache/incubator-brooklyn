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
package brooklyn.demo;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.catalog.Catalog;
import brooklyn.enricher.Enrichers;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractApplication;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.basic.StartableApplication;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.nosql.mongodb.MongoDBReplicaSet;
import brooklyn.entity.nosql.mongodb.MongoDBServer;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.launcher.BrooklynLauncher;
import brooklyn.policy.ha.ServiceFailureDetector;
import brooklyn.policy.ha.ServiceReplacer;
import brooklyn.policy.ha.ServiceRestarter;
import brooklyn.util.CommandLineUtil;

import com.google.common.collect.Lists;

/**
 * Sample showing a MongoDB replica set with resilience policies attached at nodes and the cluster.
 **/
@Catalog(name="Resilient MongoDB")
public class ResilientMongoDbApp extends AbstractApplication implements StartableApplication {
    
    public static final Logger LOG = LoggerFactory.getLogger(ResilientMongoDbApp.class);
    
    public static final String DEFAULT_LOCATION = "named:gce-europe-west1";

    @Override
    public void initApp() {
        MongoDBReplicaSet rs = addChild(
                EntitySpec.create(MongoDBReplicaSet.class)
                        .configure(MongoDBReplicaSet.INITIAL_SIZE, 3));
        
        initResilience(rs);
        
        addEnricher(Enrichers.builder()
                .propagating(MongoDBReplicaSet.REPLICA_SET_ENDPOINTS, MongoDBServer.REPLICA_SET_PRIMARY_ENDPOINT)
                .from(rs)
                .build());
    }
    
    /** this attaches a policy at each MongoDB node listening for ENTITY_FAILED,
     * attempting to _restart_ the process, and 
     * failing that attempting to _replace_ the entity (e.g. a new VM), and 
     * failing that setting the cluster "on-fire" */
    protected void initResilience(MongoDBReplicaSet rs) {
        subscribe(rs, DynamicCluster.MEMBER_ADDED, new SensorEventListener<Entity>() {
            @Override
            public void onEvent(SensorEvent<Entity> addition) {
                initSoftwareProcess((SoftwareProcess)addition.getValue());
            }
        });
        rs.addPolicy(new ServiceReplacer(ServiceRestarter.ENTITY_RESTART_FAILED));
    }

    /** invoked whenever a new MongoDB server is added (the server may not be started yet) */
    protected void initSoftwareProcess(SoftwareProcess p) {
        p.addEnricher(new ServiceFailureDetector());
        p.addPolicy(new ServiceRestarter(ServiceFailureDetector.ENTITY_FAILED));
    }
    
    public static void main(String[] argv) {
        List<String> args = Lists.newArrayList(argv);
        String port =  CommandLineUtil.getCommandLineOption(args, "--port", "8081+");
        String location = CommandLineUtil.getCommandLineOption(args, "--location", DEFAULT_LOCATION);

        BrooklynLauncher launcher = BrooklynLauncher.newInstance()
                 .application(EntitySpec.create(StartableApplication.class, ResilientMongoDbApp.class)
                         .displayName("Resilient MongoDB"))
                 .webconsolePort(port)
                 .location(location)
                 .start();
             
        Entities.dumpInfo(launcher.getApplications());
    }
    
}
