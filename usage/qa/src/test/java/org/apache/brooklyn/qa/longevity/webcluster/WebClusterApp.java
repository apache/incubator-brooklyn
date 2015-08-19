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
package org.apache.brooklyn.qa.longevity.webcluster;

import java.util.List;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.api.sensor.EnricherSpec;
import org.apache.brooklyn.core.entity.AbstractApplication;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.StartableApplication;
import org.apache.brooklyn.core.internal.BrooklynProperties;
import org.apache.brooklyn.core.sensor.Sensors;
import org.apache.brooklyn.entity.proxy.nginx.NginxController;
import org.apache.brooklyn.entity.webapp.ControlledDynamicWebAppCluster;
import org.apache.brooklyn.entity.webapp.jboss.JBoss7Server;
import org.apache.brooklyn.launcher.BrooklynLauncher;
import org.apache.brooklyn.policy.autoscaling.AutoScalerPolicy;
import org.apache.brooklyn.sensor.enricher.Enrichers;
import org.apache.brooklyn.util.CommandLineUtil;

import com.google.common.collect.Lists;

public class WebClusterApp extends AbstractApplication {

    static BrooklynProperties config = BrooklynProperties.Factory.newDefault();

    public static final String WAR_PATH = "classpath://hello-world.war";

    private static final long loadCyclePeriodMs = 2 * 60 * 1000L;

    @Override
    public void initApp() {
        final AttributeSensor<Double> sinusoidalLoad =
                Sensors.newDoubleSensor("brooklyn.qa.sinusoidalLoad", "Sinusoidal server load");
        AttributeSensor<Double> averageLoad =
                Sensors.newDoubleSensor("brooklyn.qa.averageLoad", "Average load in cluster");

        NginxController nginxController = addChild(EntitySpec.create(NginxController.class)
                // .configure("domain", "webclusterexample.brooklyn.local")
                .configure("port", "8000+"));

        EntitySpec<JBoss7Server> jbossSpec = EntitySpec.create(JBoss7Server.class)
                .configure("httpPort", "8080+")
                .configure("war", WAR_PATH)
                .enricher(EnricherSpec.create(SinusoidalLoadGenerator.class)
                        .configure(SinusoidalLoadGenerator.TARGET, sinusoidalLoad)
                        .configure(SinusoidalLoadGenerator.PUBLISH_PERIOD_MS, 500L)
                        .configure(SinusoidalLoadGenerator.SIN_PERIOD_MS, loadCyclePeriodMs)
                        .configure(SinusoidalLoadGenerator.SIN_AMPLITUDE, 1d));

        ControlledDynamicWebAppCluster web = addChild(EntitySpec.create(ControlledDynamicWebAppCluster.class)
                .displayName("WebApp cluster")
                .configure("controller", nginxController)
                .configure("initialSize", 1)
                .configure("memberSpec", jbossSpec));

        web.getCluster().addEnricher(Enrichers.builder()
                .aggregating(sinusoidalLoad)
                .publishing(averageLoad)
                .fromMembers()
                .computingAverage()
                .build());
        web.getCluster().addPolicy(AutoScalerPolicy.builder()
                .metric(averageLoad)
                .sizeRange(1, 3)
                .metricRange(0.3, 0.7)
                .build());
    }
    
    public static void main(String[] argv) {
        List<String> args = Lists.newArrayList(argv);
        String port =  CommandLineUtil.getCommandLineOption(args, "--port", "8081+");
        String location = CommandLineUtil.getCommandLineOption(args, "--location", "localhost");

        BrooklynLauncher launcher = BrooklynLauncher.newInstance()
                .application(EntitySpec.create(StartableApplication.class, WebClusterApp.class).displayName("Brooklyn WebApp Cluster example"))
                .webconsolePort(port)
                .location(location)
                .start();
         
        Entities.dumpInfo(launcher.getApplications());
    }
}
