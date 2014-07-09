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
package brooklyn.extras.whirr;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.AbstractApplication;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.StartableApplication;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.extras.whirr.hadoop.WhirrHadoopCluster;
import brooklyn.launcher.BrooklynLauncher;
import brooklyn.util.CommandLineUtil;

import com.google.common.collect.Lists;

public class WhirrHadoopExample extends AbstractApplication {

    private static final Logger LOG = LoggerFactory.getLogger(WhirrHadoopExample.class);

    public static final String DEFAULT_LOCATION = "aws-ec2:eu-west-1";

    @Override
    public void init() {
        addChild(EntitySpec.create(WhirrHadoopCluster.class)
                .displayName("brooklyn-hadoop-example")
                .configure("size", 2)
                .configure("memory", 2048));
    }

    public static void main(String[] argv) throws Exception {
        List<String> args = Lists.newArrayList(argv);
        String port =  CommandLineUtil.getCommandLineOption(args, "--port", "8081+");
        String location = CommandLineUtil.getCommandLineOption(args, "--location", DEFAULT_LOCATION);

        BrooklynLauncher launcher = BrooklynLauncher.newInstance()
                .application(EntitySpec.create(StartableApplication.class, WhirrHadoopExample.class))
                .webconsolePort(port)
                .location(location)
                .start();
         
        StartableApplication app = (StartableApplication) launcher.getApplications().get(0);
        Entities.dumpInfo(app);
        
        LOG.info("Press return to shut down the cluster");
        System.in.read(); //wait for the user to type a key
        app.stop();
    }
}
