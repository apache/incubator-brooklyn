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

import static brooklyn.entity.java.JavaEntityMethods.javaSysProp;
import static brooklyn.event.basic.DependentConfiguration.attributeWhenReady;
import static brooklyn.event.basic.DependentConfiguration.formatString;

import java.io.IOException;
import java.util.List;

import org.marre.sms.SmsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.enricher.basic.SensorPropagatingEnricher;
import brooklyn.enricher.basic.SensorTransformingEnricher;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.StartableApplication;
import brooklyn.entity.database.mysql.MySqlNode;
import brooklyn.entity.webapp.ControlledDynamicWebAppCluster;
import brooklyn.entity.webapp.DynamicWebAppCluster;
import brooklyn.entity.webapp.JavaWebAppService;
import brooklyn.entity.webapp.WebAppService;
import brooklyn.entity.webapp.WebAppServiceConstants;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.basic.BasicNotificationSensor;
import brooklyn.launcher.BrooklynLauncher;
import brooklyn.launcher.BrooklynServerDetails;
import brooklyn.location.Location;
import brooklyn.location.basic.PortRanges;
import brooklyn.policy.autoscaling.AutoScalerPolicy;
import brooklyn.policy.autoscaling.MaxPoolSizeReachedEvent;
import brooklyn.util.CommandLineUtil;

import com.google.common.base.Functions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

/**
 * Launches a 3-tier app with nginx, clustered jboss, and mysql.
 **/
public class WebClusterDatabaseOpenLoopExample extends ApplicationBuilder {
    
    public static final Logger LOG = LoggerFactory.getLogger(WebClusterDatabaseOpenLoopExample.class);
    
    public static final String WAR_PATH = "classpath://hello-world-sql-webapp.war";
    
    public static final String DB_SETUP_SQL_URL = "classpath://visitors-creation-script.sql";
    
    public static final String DB_TABLE = "visitors";
    public static final String DB_USERNAME = "brooklyn";
    public static final String DB_PASSWORD = "br00k11n";
    
    // TODO Replace these with your details, for a clickatell.com account that has credit to send SMS messages
    public static final String CLICKATELL_USERNAME = "brooklyndemo.uk";
    public static final String CLICKATELL_PASSWORD = "NAYLLHRLZEBYYA";
    public static final String CLICKATELL_APIID = "3411519";
    public static final String SMS_RECEIVER = "441234567890";

    public static final BasicAttributeSensor<Integer> APPSERVERS_COUNT = Sensors.newIntegerSensor( 
            "appservers.count", "Number of app servers deployed");

    public static final BasicNotificationSensor<MaxPoolSizeReachedEvent> MAX_SIZE_REACHED = new BasicNotificationSensor<MaxPoolSizeReachedEvent>(
            MaxPoolSizeReachedEvent.class, "cluster.maxPoolSizeReached", "");

    protected void doBuild() {
        MySqlNode mysql = addChild(EntitySpec.create(MySqlNode.class)
                .configure("creationScriptUrl", DB_SETUP_SQL_URL));
        
        ControlledDynamicWebAppCluster web = addChild(EntitySpec.create(ControlledDynamicWebAppCluster.class)
                .configure(WebAppService.HTTP_PORT, PortRanges.fromString("8080+"))
                .configure(JavaWebAppService.ROOT_WAR, WAR_PATH)
                .configure(javaSysProp("brooklyn.example.db.url"), 
                        formatString("jdbc:%s%s?user=%s\\&password=%s", 
                                attributeWhenReady(mysql, MySqlNode.MYSQL_URL), 
                                DB_TABLE, DB_USERNAME, DB_PASSWORD)));

        // Subscribe not notifications about the auto-scaler policy reaching its max cluster size 
        getManagementContext().getSubscriptionManager().subscribe(
                web.getCluster(), 
                MAX_SIZE_REACHED, 
                new SensorEventListener<MaxPoolSizeReachedEvent>() {
                    @Override public void onEvent(SensorEvent<MaxPoolSizeReachedEvent> event) {
                        onMaxPoolSizeReached(event.getValue());
                    }
                });
        
        // simple scaling policy
        web.getCluster().addPolicy(AutoScalerPolicy.builder().
                metric(DynamicWebAppCluster.REQUESTS_PER_SECOND_IN_WINDOW_PER_NODE).
                metricRange(10, 100).
                sizeRange(1, 5).
                maxSizeReachedSensor(MAX_SIZE_REACHED).
//                maxReachedNotificationDelay(1000).
                build());

        // expose some KPI's
        getApp().addEnricher(SensorPropagatingEnricher.newInstanceListeningTo(web,  
                WebAppServiceConstants.ROOT_URL,
                DynamicWebAppCluster.REQUESTS_PER_SECOND_IN_WINDOW));
        getApp().addEnricher(new SensorTransformingEnricher<Integer,Integer>(web, 
                DynamicWebAppCluster.GROUP_SIZE, APPSERVERS_COUNT, Functions.<Integer>identity()));
    }

    private void onMaxPoolSizeReached(MaxPoolSizeReachedEvent event) {
        String msg = "Max-pool-size-reached: "+event;
        System.out.println("*** "+msg);
        
        try {
            new Sms(CLICKATELL_USERNAME, CLICKATELL_PASSWORD, CLICKATELL_APIID).sendSms(SMS_RECEIVER, msg);
        } catch (SmsException e) {
            LOG.warn("Error sending SMS message: "+e.getMessage(), e);
        } catch (IOException e) {
            LOG.warn("Error sending SMS message", e);
        }
    }
    
    public static void main(String[] argv) {
        List<String> args = Lists.newArrayList(argv);
        String port =  CommandLineUtil.getCommandLineOption(args, "--port", "8081+");
        String location = CommandLineUtil.getCommandLineOption(args, "--location", "localhost");

        BrooklynServerDetails server = BrooklynLauncher.newLauncher()
                .webconsolePort(port)
                .launch();

        Location loc = server.getManagementContext().getLocationRegistry().resolve(location);

        StartableApplication app = new WebClusterDatabaseOpenLoopExample()
                .appDisplayName("Brooklyn WebApp Cluster with Database example")
                .manage(server.getManagementContext());
        
        app.start(ImmutableList.of(loc));
        
        Entities.dumpInfo(app);
    }
}
