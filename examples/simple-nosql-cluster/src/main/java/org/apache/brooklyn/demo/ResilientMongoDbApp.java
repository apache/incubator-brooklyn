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
package org.apache.brooklyn.demo;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.brooklyn.api.catalog.Catalog;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.sensor.SensorEvent;
import org.apache.brooklyn.api.sensor.SensorEventListener;
import org.apache.brooklyn.core.entity.AbstractApplication;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.StartableApplication;
import org.apache.brooklyn.enricher.stock.Enrichers;
import org.apache.brooklyn.entity.group.DynamicCluster;
import org.apache.brooklyn.entity.nosql.mongodb.MongoDBReplicaSet;
import org.apache.brooklyn.entity.nosql.mongodb.MongoDBServer;
import org.apache.brooklyn.entity.software.base.SoftwareProcess;
import org.apache.brooklyn.launcher.BrooklynLauncher;
import org.apache.brooklyn.policy.ha.ServiceFailureDetector;
import org.apache.brooklyn.policy.ha.ServiceReplacer;
import org.apache.brooklyn.policy.ha.ServiceRestarter;
import org.apache.brooklyn.util.CommandLineUtil;

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
        
        enrichers().add(Enrichers.builder()
                .propagating(MongoDBReplicaSet.REPLICA_SET_ENDPOINTS, MongoDBServer.REPLICA_SET_PRIMARY_ENDPOINT)
                .from(rs)
                .build());
    }
    
    /** this attaches a policy at each MongoDB node listening for ENTITY_FAILED,
     * attempting to _restart_ the process, and 
     * failing that attempting to _replace_ the entity (e.g. a new VM), and 
     * failing that setting the cluster "on-fire" */
    protected void initResilience(MongoDBReplicaSet rs) {
        subscriptions().subscribe(rs, DynamicCluster.MEMBER_ADDED, new SensorEventListener<Entity>() {
            @Override
            public void onEvent(SensorEvent<Entity> addition) {
                initSoftwareProcess((SoftwareProcess)addition.getValue());
            }
        });
        rs.policies().add(new ServiceReplacer(ServiceRestarter.ENTITY_RESTART_FAILED));
    }

    /** invoked whenever a new MongoDB server is added (the server may not be started yet) */
    protected void initSoftwareProcess(SoftwareProcess p) {
        p.enrichers().add(new ServiceFailureDetector());
        p.policies().add(new ServiceRestarter(ServiceFailureDetector.ENTITY_FAILED));
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
