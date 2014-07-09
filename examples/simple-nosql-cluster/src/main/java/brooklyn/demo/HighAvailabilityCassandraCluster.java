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

import brooklyn.catalog.Catalog;
import brooklyn.catalog.CatalogConfig;
import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.AbstractApplication;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.StartableApplication;
import brooklyn.entity.nosql.cassandra.CassandraDatacenter;
import brooklyn.entity.nosql.cassandra.CassandraNode;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.launcher.BrooklynLauncher;
import brooklyn.policy.PolicySpec;
import brooklyn.policy.ha.ServiceFailureDetector;
import brooklyn.policy.ha.ServiceReplacer;
import brooklyn.policy.ha.ServiceRestarter;
import brooklyn.util.CommandLineUtil;

import com.google.common.collect.Lists;

@Catalog(name="HA Cassandra Cluster Application", description="Deploy a Cassandra cluster with a High Availability architecture.")
public class HighAvailabilityCassandraCluster extends AbstractApplication {

    public static final String DEFAULT_LOCATION_SPEC = "aws-ec2:us-east-1";
 
    @CatalogConfig(label="Number of Availability Zones", priority=1)
    public static final ConfigKey<Integer> NUM_AVAILABILITY_ZONES = ConfigKeys.newConfigKey(
        "cassandra.cluster.numAvailabilityZones", "Number of availability zones to spread the cluster across", 3); 
    
    @CatalogConfig(label="Initial Cluster Size", priority=2)
    public static final ConfigKey<Integer> CASSANDRA_CLUSTER_SIZE = ConfigKeys.newConfigKey(
        "cassandra.cluster.initialSize", "Initial size of the Cassandra cluster", 6);      
    
    
    @Override
    public void init() {
        addChild(EntitySpec.create(CassandraDatacenter.class)
                .configure(CassandraDatacenter.CLUSTER_NAME, "Brooklyn")
                .configure(CassandraDatacenter.INITIAL_SIZE, getConfig(CASSANDRA_CLUSTER_SIZE))
                .configure(CassandraDatacenter.ENABLE_AVAILABILITY_ZONES, true)
                .configure(CassandraDatacenter.NUM_AVAILABILITY_ZONES, getConfig(NUM_AVAILABILITY_ZONES))
                //See https://github.com/brooklyncentral/brooklyn/issues/973
                //.configure(CassandraCluster.AVAILABILITY_ZONE_NAMES, ImmutableList.of("us-east-1b", "us-east-1c", "us-east-1e"))
                .configure(CassandraDatacenter.ENDPOINT_SNITCH_NAME, "GossipingPropertyFileSnitch")
                .configure(CassandraDatacenter.MEMBER_SPEC, EntitySpec.create(CassandraNode.class)
                        .policy(PolicySpec.create(ServiceFailureDetector.class))
                        .policy(PolicySpec.create(ServiceRestarter.class)
                                .configure(ServiceRestarter.FAILURE_SENSOR_TO_MONITOR, ServiceFailureDetector.ENTITY_FAILED)))
                .policy(PolicySpec.create(ServiceReplacer.class)
                        .configure(ServiceReplacer.FAILURE_SENSOR_TO_MONITOR, ServiceRestarter.ENTITY_RESTART_FAILED)));
    }
    
    public static void main(String[] argv) {
        List<String> args = Lists.newArrayList(argv);
        String port =  CommandLineUtil.getCommandLineOption(args, "--port", "8081+");
        String location = CommandLineUtil.getCommandLineOption(args, "--location", DEFAULT_LOCATION_SPEC);
        
        BrooklynLauncher launcher = BrooklynLauncher.newInstance()
                 .application(EntitySpec.create(StartableApplication.class, HighAvailabilityCassandraCluster.class)
                         .displayName("Cassandra"))
                 .webconsolePort(port)
                 .location(location)
                 .start();
             
        Entities.dumpInfo(launcher.getApplications());
    }
}
