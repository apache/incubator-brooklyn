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

import java.util.Arrays;
import java.util.List;

import brooklyn.entity.basic.AbstractApplication;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.StartableApplication;
import brooklyn.entity.nosql.cassandra.CassandraCluster;
import brooklyn.entity.nosql.cassandra.CassandraFabric;
import brooklyn.entity.nosql.cassandra.CassandraNode;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.launcher.BrooklynLauncher;
import brooklyn.policy.PolicySpec;
import brooklyn.policy.ha.ServiceFailureDetector;
import brooklyn.policy.ha.ServiceReplacer;
import brooklyn.policy.ha.ServiceRestarter;
import brooklyn.util.CommandLineUtil;

import com.google.common.collect.Lists;

public class WideAreaCassandraCluster extends AbstractApplication {

    @Override
    public void init() {
        addChild(EntitySpec.create(CassandraFabric.class)
                .configure(CassandraCluster.CLUSTER_NAME, "Brooklyn")
                .configure(CassandraCluster.INITIAL_SIZE, 2) // per location
                .configure(CassandraCluster.ENDPOINT_SNITCH_NAME, "brooklyn.entity.nosql.cassandra.customsnitch.MultiCloudSnitch")
                .configure(CassandraNode.CUSTOM_SNITCH_JAR_URL, "classpath://brooklyn/entity/nosql/cassandra/cassandra-multicloud-snitch.jar")
                .configure(CassandraFabric.MEMBER_SPEC, EntitySpec.create(CassandraCluster.class)
                        .configure(CassandraCluster.MEMBER_SPEC, EntitySpec.create(CassandraNode.class)
                                .policy(PolicySpec.create(ServiceFailureDetector.class))
                                .policy(PolicySpec.create(ServiceRestarter.class)
                                        .configure(ServiceRestarter.FAILURE_SENSOR_TO_MONITOR, ServiceFailureDetector.ENTITY_FAILED)))
                        .policy(PolicySpec.create(ServiceReplacer.class)
                                .configure(ServiceReplacer.FAILURE_SENSOR_TO_MONITOR, ServiceRestarter.ENTITY_RESTART_FAILED))));
    }
    
    public static void main(String[] argv) {
        List<String> args = Lists.newArrayList(argv);
        String port =  CommandLineUtil.getCommandLineOption(args, "--port", "8081+");
        String locations = CommandLineUtil.getCommandLineOption(args, "--location");
        if (locations == null) {
            throw new IllegalArgumentException("Locations must be supplied (with --location <comma-separate-location-specs>)");
        }

        BrooklynLauncher launcher = BrooklynLauncher.newInstance()
                 .application(EntitySpec.create(StartableApplication.class, WideAreaCassandraCluster.class)
                         .displayName("Cassandra"))
                 .webconsolePort(port)
                 .locations(Arrays.asList(locations))
                 .start();
             
        Entities.dumpInfo(launcher.getApplications());
    }
}
