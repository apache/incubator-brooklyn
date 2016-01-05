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
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.core.entity.AbstractApplication;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.internal.BrooklynProperties;
import org.apache.brooklyn.entity.proxy.nginx.NginxController;
import org.apache.brooklyn.entity.webapp.ControlledDynamicWebAppCluster;
import org.apache.brooklyn.entity.webapp.DynamicWebAppCluster;
import org.apache.brooklyn.entity.webapp.jboss.JBoss7Server;
import org.apache.brooklyn.launcher.BrooklynLauncher;
import org.apache.brooklyn.policy.autoscaling.AutoScalerPolicy;
import org.apache.brooklyn.util.CommandLineUtil;

import com.google.common.collect.Lists;

/**
 * Launches a clustered and load-balanced set of web servers.
 * Demonstrates syntax, so many of the options used here are the defaults.
 * (So the class could be much simpler, as in WebClusterExampleAlt.)
 * <p>
 * Requires: 
 * -Xmx512m -Xms128m -XX:MaxPermSize=256m
 * and brooklyn-all jar, and this jar or classes dir, on classpath. 
 **/
public class WebClusterExample extends AbstractApplication {
    public static final Logger LOG = LoggerFactory.getLogger(WebClusterExample.class);
    
    static BrooklynProperties config = BrooklynProperties.Factory.newDefault();

    public static final String DEFAULT_LOCATION = "localhost";

    public static final String WAR_PATH = "classpath://hello-world-webapp.war";

    private NginxController nginxController;
    private ControlledDynamicWebAppCluster web;
    
    @Override
    public void initApp() {
        nginxController = addChild(EntitySpec.create(NginxController.class)
                //.configure("domain", "webclusterexample.brooklyn.local")
                .configure("port", "8000+"));
          
        web = addChild(EntitySpec.create(ControlledDynamicWebAppCluster.class)
                .displayName("WebApp cluster")
                .configure(ControlledDynamicWebAppCluster.CONTROLLER, nginxController)
                .configure(ControlledDynamicWebAppCluster.INITIAL_SIZE, 1)
                .configure(ControlledDynamicWebAppCluster.MEMBER_SPEC, EntitySpec.create(JBoss7Server.class)
                        .configure("httpPort", "8080+")
                        .configure("war", WAR_PATH)));
        
        web.getCluster().policies().add(AutoScalerPolicy.builder()
                .metric(DynamicWebAppCluster.REQUESTS_PER_SECOND_IN_WINDOW_PER_NODE)
                .sizeRange(1, 5)
                .metricRange(10, 100)
                .build());
    }
    
    public static void main(String[] argv) {
        List<String> args = Lists.newArrayList(argv);
        String port =  CommandLineUtil.getCommandLineOption(args, "--port", "8081+");
        String location = CommandLineUtil.getCommandLineOption(args, "--location", DEFAULT_LOCATION);

        // TODO Want to parse, to handle multiple locations
        BrooklynLauncher launcher = BrooklynLauncher.newInstance()
                .application(EntitySpec.create(WebClusterExample.class).displayName("Brooklyn WebApp Cluster example"))
                .webconsolePort(port)
                .location(location)
                .start();
         
        Entities.dumpInfo(launcher.getApplications());
    }
}
