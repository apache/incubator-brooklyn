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
package org.apache.brooklyn.qa.load;

import static org.apache.brooklyn.sensor.core.DependentConfiguration.attributeWhenReady;
import static org.apache.brooklyn.sensor.core.DependentConfiguration.formatString;

import java.util.Collection;
import java.util.List;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.entity.AbstractApplication;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.StartableApplication;
import org.apache.brooklyn.core.entity.trait.Startable;
import org.apache.brooklyn.entity.database.mysql.MySqlNode;
import org.apache.brooklyn.entity.group.DynamicCluster;
import org.apache.brooklyn.entity.java.JavaEntityMethods;
import org.apache.brooklyn.entity.proxy.nginx.NginxController;
import org.apache.brooklyn.entity.webapp.ControlledDynamicWebAppCluster;
import org.apache.brooklyn.entity.webapp.DynamicWebAppCluster;
import org.apache.brooklyn.entity.webapp.JavaWebAppService;
import org.apache.brooklyn.entity.webapp.WebAppService;
import org.apache.brooklyn.entity.webapp.WebAppServiceConstants;
import org.apache.brooklyn.entity.webapp.jboss.JBoss7Server;
import org.apache.brooklyn.launcher.BrooklynLauncher;
import org.apache.brooklyn.location.core.PortRanges;
import org.apache.brooklyn.policy.autoscaling.AutoScalerPolicy;
import org.apache.brooklyn.policy.enricher.HttpLatencyDetector;
import org.apache.brooklyn.sensor.enricher.Enrichers;
import org.apache.brooklyn.util.CommandLineUtil;
import org.apache.brooklyn.util.collections.MutableSet;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;

/**
 * A 3-tier app where all components are just "simulated" - they don't actually run 
 * real app-servers or databases, instead just executing a "sleep" command to simulate 
 * the running process.
 * 
 * This is useful for load testing, where we want to test the performance of Brooklyn
 * rather than the ability to host many running app-servers.
 * 
 * The app is based on WebClusterDatabaseExampleApp
 * 
 * @see SimulatedJBoss7ServerImpl for description of purpose and configuration options.
 */
public class SimulatedTheeTierApp extends AbstractApplication {

    public static final ConfigKey<Boolean> SIMULATE_ENTITY = ConfigKeys.newBooleanConfigKey("simulateEntity", "", true);
    
    public static final ConfigKey<Boolean> SIMULATE_EXTERNAL_MONITORING = ConfigKeys.newBooleanConfigKey("simulateExternalMonitoring", "", true);

    public static final ConfigKey<Boolean> SKIP_SSH_ON_START = ConfigKeys.newBooleanConfigKey("skipSshOnStart", "", false);

    public static final String WAR_PATH = "classpath://hello-world.war";
    public static final String DB_TABLE = "visitors";
    public static final String DB_USERNAME = "brooklyn";
    public static final String DB_PASSWORD = "br00k11n";
    public static final boolean USE_HTTPS = false;
    
    @Override
    public void init() {
        MySqlNode mysql = addChild(
                EntitySpec.create(MySqlNode.class)
                .impl(SimulatedMySqlNodeImpl.class));

        ControlledDynamicWebAppCluster web = addChild(
                EntitySpec.create(ControlledDynamicWebAppCluster.class)
                .configure(ControlledDynamicWebAppCluster.MEMBER_SPEC, EntitySpec.create(JBoss7Server.class).impl(SimulatedJBoss7ServerImpl.class))
                .configure(ControlledDynamicWebAppCluster.CONTROLLER_SPEC, EntitySpec.create(NginxController.class).impl(SimulatedNginxControllerImpl.class))
                        .configure(WebAppService.HTTP_PORT, PortRanges.fromString("8080+"))
                        .configure(JavaWebAppService.ROOT_WAR, WAR_PATH)
                        .configure(JavaEntityMethods.javaSysProp("brooklyn.example.db.url"), 
                                formatString("jdbc:%s%s?user=%s\\&password=%s", 
                                        attributeWhenReady(mysql, MySqlNode.DATASTORE_URL), DB_TABLE, DB_USERNAME, DB_PASSWORD))
                        .configure(DynamicCluster.INITIAL_SIZE, 2)
                        .configure(WebAppService.ENABLED_PROTOCOLS, ImmutableSet.of(USE_HTTPS ? "https" : "http")) );

        web.getCluster().addPolicy(AutoScalerPolicy.builder().
                metric(DynamicWebAppCluster.REQUESTS_PER_SECOND_IN_WINDOW_PER_NODE).
                metricRange(10, 100).
                sizeRange(2, 5).
                build());

        addEnricher(Enrichers.builder()
                .propagating(Attributes.MAIN_URI, WebAppServiceConstants.ROOT_URL,
                        DynamicWebAppCluster.REQUESTS_PER_SECOND_IN_WINDOW,
                        HttpLatencyDetector.REQUEST_LATENCY_IN_SECONDS_IN_WINDOW)
                .from(web)
                .build());
        
        addEnricher(Enrichers.builder()
                .aggregating(Startable.SERVICE_UP)
                .publishing(Startable.SERVICE_UP)
                .fromHardcodedProducers(ImmutableList.of(web, mysql))
                .computing(new Function<Collection<Boolean>, Boolean>() {
                    @Override public Boolean apply(Collection<Boolean> input) {
                        return input != null && input.size() == 2 && MutableSet.copyOf(input).equals(ImmutableSet.of(true));
                    }})
                .build());
    }
    
    public static void main(String[] argv) {
        List<String> args = Lists.newArrayList(argv);
        String port =  CommandLineUtil.getCommandLineOption(args, "--port", "8081+");
        String location = CommandLineUtil.getCommandLineOption(args, "--location", "localhost");

        BrooklynLauncher launcher = BrooklynLauncher.newInstance()
                 .application(EntitySpec.create(StartableApplication.class, SimulatedTheeTierApp.class)
                         .displayName("Brooklyn WebApp Cluster with Database example"))
                 .webconsolePort(port)
                 .location(location)
                 .start();
             
        Entities.dumpInfo(launcher.getApplications());
    }
}
