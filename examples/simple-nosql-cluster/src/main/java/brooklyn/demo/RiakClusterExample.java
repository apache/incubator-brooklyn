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

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;

import brooklyn.catalog.Catalog;
import brooklyn.catalog.CatalogConfig;
import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.AbstractApplication;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.StartableApplication;
import brooklyn.entity.nosql.riak.RiakCluster;
import brooklyn.entity.nosql.riak.RiakNode;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.launcher.BrooklynLauncher;
import brooklyn.policy.PolicySpec;
import brooklyn.policy.ha.ServiceFailureDetector;
import brooklyn.policy.ha.ServiceRestarter;
import brooklyn.util.CommandLineUtil;

@Catalog(name = "Riak Cluster Application", description = "Riak ring deployment blueprint")
public class RiakClusterExample extends AbstractApplication {

    public static final String DEFAULT_LOCATION_SPEC = "aws-ec2:us-east-1";

    @CatalogConfig(label = "Riak Ring Size")
    public static final ConfigKey<Integer> RIAK_RING_SIZE = ConfigKeys.newConfigKey(
            "riak.ring.size", "Initial size of the Riak Ring", 4);

    public static void main(String[] argv) {
        List<String> args = Lists.newArrayList(argv);
        String port = CommandLineUtil.getCommandLineOption(args, "--port", "8081+");
        String location = CommandLineUtil.getCommandLineOption(args, "--location", DEFAULT_LOCATION_SPEC);
        Preconditions.checkArgument(args.isEmpty(), "Unsupported args: " + args);

        BrooklynLauncher launcher = BrooklynLauncher.newInstance()
                .application(EntitySpec.create(StartableApplication.class, RiakClusterExample.class))
                .webconsolePort(port)
                .location(location)
                .start();

        Entities.dumpInfo(launcher.getApplications());
    }

    public void init() {
        addChild(EntitySpec.create(RiakCluster.class)
                .configure(RiakCluster.INITIAL_SIZE, getConfig(RIAK_RING_SIZE))
                .configure(RiakCluster.MEMBER_SPEC, EntitySpec.create(RiakNode.class)
                        .policy(PolicySpec.create(ServiceFailureDetector.class))
                        .policy(PolicySpec.create(ServiceRestarter.class)
                                .configure(ServiceRestarter.FAILURE_SENSOR_TO_MONITOR, ServiceFailureDetector.ENTITY_FAILED))));
    }

}
