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

import static brooklyn.entity.java.JavaEntityMethods.javaSysProp
import static brooklyn.event.basic.DependentConfiguration.attributeWhenReady
import static brooklyn.event.basic.DependentConfiguration.formatString

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.basic.Entities
import brooklyn.entity.database.mysql.MySqlNode
import brooklyn.entity.proxying.EntitySpec
import brooklyn.entity.webapp.ControlledDynamicWebAppCluster
import brooklyn.entity.webapp.DynamicWebAppCluster
import brooklyn.launcher.BrooklynLauncher
import brooklyn.policy.autoscaling.AutoScalerPolicy
import brooklyn.util.CommandLineUtil

import com.google.common.collect.Lists

/**
 * Launches a 3-tier app with nginx, clustered jboss, and mysql.
 * <p>
 * This variant of {@link WebClusterDatabaseExample} demonstrates <i>Groovy</i> language conveniences.
 **/
public class WebClusterDatabaseExampleGroovy extends AbstractApplication {
    
    public static final Logger LOG = LoggerFactory.getLogger(WebClusterDatabaseExampleGroovy.class);
    
    public static final String DEFAULT_LOCATION = "localhost";

    public static final String WAR_PATH = "classpath://hello-world-sql-webapp.war";
    
    public static final String DB_SETUP_SQL_URL = "classpath://visitors-creation-script.sql";
    
    public static final String DB_TABLE = "visitors";
    public static final String DB_USERNAME = "brooklyn";
    public static final String DB_PASSWORD = "br00k11n";
    
    @Override
    public void initApp() {
        MySqlNode mysql = addChild(MySqlNode,
                creationScriptUrl: DB_SETUP_SQL_URL);
        
        ControlledDynamicWebAppCluster web = addChild(ControlledDynamicWebAppCluster,
                war: WAR_PATH,
                httpPort: "8080+",
                (javaSysProp("brooklyn.example.db.url")): 
                    formatString("jdbc:%s%s?user=%s\\&password=%s", 
                            attributeWhenReady(mysql, MySqlNode.DATASTORE_URL), 
                            DB_TABLE, DB_USERNAME, DB_PASSWORD));
    
        web.getCluster().addPolicy(AutoScalerPolicy.builder().
                metric(DynamicWebAppCluster.REQUESTS_PER_SECOND_LAST_PER_NODE).
                sizeRange(1, 5).
                metricRange(10, 100).
                build());
    }

    public static void main(String[] argv) {
        List<String> args = Lists.newArrayList(argv);
        String port =  CommandLineUtil.getCommandLineOption(args, "--port", "8081+");
        String location = CommandLineUtil.getCommandLineOption(args, "--location", DEFAULT_LOCATION);

        BrooklynLauncher launcher = BrooklynLauncher.newInstance()
                .application(EntitySpec.create(WebClusterDatabaseExampleGroovy.class).displayName("Brooklyn WebApp Cluster with Database example"))
                .webconsolePort(port)
                .location(location)
                .start();
         
        Entities.dumpInfo(launcher.getApplications());
    }
    
}
