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

import static org.apache.brooklyn.sensor.core.DependentConfiguration.attributeWhenReady;
import static org.apache.brooklyn.sensor.core.DependentConfiguration.formatString;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.brooklyn.api.catalog.Catalog;
import org.apache.brooklyn.api.catalog.CatalogConfig;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.entity.webapp.ControlledDynamicWebAppCluster;
import org.apache.brooklyn.entity.webapp.DynamicWebAppCluster;
import org.apache.brooklyn.entity.webapp.JavaWebAppService;
import org.apache.brooklyn.entity.webapp.WebAppService;
import org.apache.brooklyn.entity.webapp.WebAppServiceConstants;
import org.apache.brooklyn.entity.core.AbstractApplication;
import org.apache.brooklyn.entity.core.Entities;
import org.apache.brooklyn.entity.core.StartableApplication;
import org.apache.brooklyn.entity.database.mysql.MySqlNode;
import org.apache.brooklyn.entity.group.DynamicCluster;

import brooklyn.entity.java.JavaEntityMethods;

import org.apache.brooklyn.launcher.BrooklynLauncher;
import org.apache.brooklyn.location.basic.PortRanges;
import org.apache.brooklyn.policy.autoscaling.AutoScalerPolicy;
import org.apache.brooklyn.policy.enricher.HttpLatencyDetector;
import org.apache.brooklyn.sensor.core.Sensors;
import org.apache.brooklyn.sensor.enricher.Enrichers;
import org.apache.brooklyn.util.CommandLineUtil;
import org.apache.brooklyn.util.core.BrooklynMavenArtifacts;
import org.apache.brooklyn.util.core.ResourceUtils;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;

/**
 * Launches a 3-tier app with nginx, clustered jboss, and mysql.
 * <p>
 * Includes some advanced features such as KPI / derived sensors,
 * and annotations for use in a catalog.
 * <p>
 * This variant also increases minimum size to 2.  
 * Note the policy min size must have the same value,
 * otherwise it fights with cluster set up trying to reduce the cluster size!
 **/
@Catalog(name="Elastic Java Web + DB",
    description="Deploys a WAR to a load-balanced elastic Java AppServer cluster, " +
            "with an auto-scaling policy, " +
            "wired to a database initialized with the provided SQL; " +
            "defaults to a 'Hello World' chatroom app.",
    iconUrl="classpath://brooklyn/demo/glossy-3d-blue-web-icon.png")
public class WebClusterDatabaseExampleApp extends AbstractApplication implements StartableApplication {
    
    public static final Logger LOG = LoggerFactory.getLogger(WebClusterDatabaseExampleApp.class);
    
    public static final String DEFAULT_LOCATION = "localhost";

    public static final String DEFAULT_WAR_PATH = ResourceUtils.create(WebClusterDatabaseExampleApp.class)
            // take this war, from the classpath, or via maven if not on the classpath
            .firstAvailableUrl(
                    "classpath://hello-world-sql-webapp.war",
                    BrooklynMavenArtifacts.localUrl("example", "brooklyn-example-hello-world-sql-webapp", "war"))
            .or("classpath://hello-world-sql-webapp.war");
    
    @CatalogConfig(label="WAR (URL)", priority=2)
    public static final ConfigKey<String> WAR_PATH = ConfigKeys.newConfigKey(
        "app.war", "URL to the application archive which should be deployed", 
        DEFAULT_WAR_PATH);    

    // TODO to expose in catalog we need to let the keystore url be specified (not hard)
    // and also confirm that this works for nginx (might be a bit fiddly);
    // booleans in the gui are working (With checkbox)
    @CatalogConfig(label="HTTPS")
    public static final ConfigKey<Boolean> USE_HTTPS = ConfigKeys.newConfigKey(
            "app.https", "Whether the application should use HTTPS only or just HTTP only (default)", false);
    
    public static final String DEFAULT_DB_SETUP_SQL_URL = "classpath://visitors-creation-script.sql";
    
    @CatalogConfig(label="DB Setup SQL (URL)", priority=1)
    public static final ConfigKey<String> DB_SETUP_SQL_URL = ConfigKeys.newConfigKey(
        "app.db_sql", "URL to the SQL script to set up the database", 
        DEFAULT_DB_SETUP_SQL_URL);
    
    public static final String DB_TABLE = "visitors";
    public static final String DB_USERNAME = "brooklyn";
    public static final String DB_PASSWORD = "br00k11n";
    
    public static final AttributeSensor<Integer> APPSERVERS_COUNT = Sensors.newIntegerSensor( 
            "appservers.count", "Number of app servers deployed");
    public static final AttributeSensor<Double> REQUESTS_PER_SECOND_IN_WINDOW = 
            WebAppServiceConstants.REQUESTS_PER_SECOND_IN_WINDOW;
    public static final AttributeSensor<String> ROOT_URL = WebAppServiceConstants.ROOT_URL;

    @Override
    public void initApp() {
        MySqlNode mysql = addChild(
                EntitySpec.create(MySqlNode.class)
                        .configure(MySqlNode.CREATION_SCRIPT_URL, Entities.getRequiredUrlConfig(this, DB_SETUP_SQL_URL)));

        ControlledDynamicWebAppCluster web = addChild(
                EntitySpec.create(ControlledDynamicWebAppCluster.class)
                        .configure(WebAppService.HTTP_PORT, PortRanges.fromString("8080+"))
                        // to specify a diferrent appserver:
//                        .configure(ControlledDynamicWebAppCluster.MEMBER_SPEC, EntitySpec.create(TomcatServer.class))
                        .configure(JavaWebAppService.ROOT_WAR, Entities.getRequiredUrlConfig(this, WAR_PATH))
                        .configure(JavaEntityMethods.javaSysProp("brooklyn.example.db.url"), 
                                formatString("jdbc:%s%s?user=%s\\&password=%s", 
                                        attributeWhenReady(mysql, MySqlNode.DATASTORE_URL), DB_TABLE, DB_USERNAME, DB_PASSWORD))
                        .configure(DynamicCluster.INITIAL_SIZE, 2)
                        .configure(WebAppService.ENABLED_PROTOCOLS, ImmutableSet.of(getConfig(USE_HTTPS) ? "https" : "http")) );

        web.addEnricher(HttpLatencyDetector.builder().
                url(ROOT_URL).
                rollup(10, TimeUnit.SECONDS).
                build());
        
        web.getCluster().addPolicy(AutoScalerPolicy.builder().
                metric(DynamicWebAppCluster.REQUESTS_PER_SECOND_IN_WINDOW_PER_NODE).
                metricRange(10, 100).
                sizeRange(2, 5).
                build());

        addEnricher(Enrichers.builder()
                .propagating(WebAppServiceConstants.ROOT_URL,
                        DynamicWebAppCluster.REQUESTS_PER_SECOND_IN_WINDOW,
                        HttpLatencyDetector.REQUEST_LATENCY_IN_SECONDS_IN_WINDOW)
                .from(web)
                .build());

        addEnricher(Enrichers.builder()
                .propagating(ImmutableMap.of(DynamicWebAppCluster.GROUP_SIZE, APPSERVERS_COUNT))
                .from(web)
                .build());
    }
    
    public static void main(String[] argv) {
        List<String> args = Lists.newArrayList(argv);
        String port =  CommandLineUtil.getCommandLineOption(args, "--port", "8081+");
        String location = CommandLineUtil.getCommandLineOption(args, "--location", DEFAULT_LOCATION);

        BrooklynLauncher launcher = BrooklynLauncher.newInstance()
                 .application(EntitySpec.create(StartableApplication.class, WebClusterDatabaseExampleApp.class)
                         .displayName("Brooklyn WebApp Cluster with Database example"))
                 .webconsolePort(port)
                 .location(location)
                 .start();
             
        Entities.dumpInfo(launcher.getApplications());
    }
}
