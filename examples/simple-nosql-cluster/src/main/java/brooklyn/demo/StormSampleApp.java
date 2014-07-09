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
import brooklyn.entity.basic.AbstractApplication;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.StartableApplication;
import brooklyn.entity.messaging.storm.StormDeployment;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.launcher.BrooklynLauncher;
import brooklyn.util.CommandLineUtil;

import com.google.common.collect.Lists;

/**
 * Sample showing a storm analyticscluster
 **/
@Catalog(name="Storm Sample App",
description="Creates a Storm analytics cluster",
    iconUrl="classpath://brooklyn/demo/glossy-3d-blue-web-icon.png")
public class StormSampleApp extends AbstractApplication implements StartableApplication {

    public static final Logger LOG = LoggerFactory.getLogger(StormSampleApp.class);

    public static final String DEFAULT_LOCATION = "named:gce-europe-west1";

    @Override
    public void init() {
        addChild(EntitySpec.create(StormDeployment.class)
            .configure(StormDeployment.SUPERVISORS_COUNT, 2)
            .configure(StormDeployment.ZOOKEEPERS_COUNT, 1));
    }

    public static void main(String[] argv) {
        List<String> args = Lists.newArrayList(argv);
        String port =  CommandLineUtil.getCommandLineOption(args, "--port", "8081+");
        String location = CommandLineUtil.getCommandLineOption(args, "--location", DEFAULT_LOCATION);

        BrooklynLauncher launcher = BrooklynLauncher.newInstance()
            .application(EntitySpec.create(StartableApplication.class, StormSampleApp.class)
                .displayName("Storm App"))
                .webconsolePort(port)
                .location(location)
                .start();

        Entities.dumpInfo(launcher.getApplications());
    }
}
