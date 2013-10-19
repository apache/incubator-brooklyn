/*
 * Copyright 2012-2013 by Cloudsoft Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package brooklyn.demo;

import java.util.List;

import brooklyn.entity.basic.AbstractApplication;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.StartableApplication;
import brooklyn.entity.nosql.cassandra.CassandraCluster;
import brooklyn.entity.nosql.cassandra.CassandraNode;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.launcher.BrooklynLauncher;
import brooklyn.policy.PolicySpec;
import brooklyn.policy.ha.ServiceFailureDetector;
import brooklyn.policy.ha.ServiceReplacer;
import brooklyn.policy.ha.ServiceRestarter;
import brooklyn.util.CommandLineUtil;

import com.google.common.collect.Lists;

public class HighAvailabilityCassandraCluster extends AbstractApplication {

    public static final String DEFAULT_LOCATION_SPEC = "aws-ec2:us-east-1";
    
    @Override
    public void init() {
        addChild(EntitySpec.create(CassandraCluster.class)
                .configure(CassandraCluster.CLUSTER_NAME, "Brooklyn")
                .configure(CassandraCluster.INITIAL_SIZE, 2)
//                .configure(CassandraCluster.ENABLE_AVAILABILITY_ZONES, true)
//                .configure(CassandraCluster.NUM_AVAILABILITY_ZONES, 3)
                .configure(CassandraCluster.ENDPOINT_SNITCH_NAME, "GossipingPropertyFileSnitch")
                .configure(CassandraCluster.MEMBER_SPEC, EntitySpec.create(CassandraNode.class)
                        .policy(PolicySpec.create(ServiceFailureDetector.class))
                        .policy(PolicySpec.create(ServiceRestarter.class)
                                .configure(ServiceRestarter.FAILURE_SENSOR_TO_MONITOR, ServiceFailureDetector.ENTITY_FAILED)))
                .policy(PolicySpec.create(ServiceReplacer.class)
                        .configure(ServiceReplacer.FAILURE_SENSOR_TO_MONITOR, ServiceRestarter.ENTITY_RESTART_FAILED)));
    }
    
    public static void main(String[] argv) {
        List<String> args = Lists.newArrayList(argv);
        String port =  CommandLineUtil.getCommandLineOption(args, "--port", "8081+");
        String location = CommandLineUtil.getCommandLineOption(args, "--location");
        if (location == null) {
            throw new IllegalArgumentException("Location must be supplied (with --location <location-spec>)");
        }
        
        BrooklynLauncher launcher = BrooklynLauncher.newInstance()
                 .application(EntitySpec.create(StartableApplication.class, HighAvailabilityCassandraCluster.class)
                         .displayName("Cassandra"))
                 .webconsolePort(port)
                 .location(location)
                 .start();
             
        Entities.dumpInfo(launcher.getApplications());
    }
}
