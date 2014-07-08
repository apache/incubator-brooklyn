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
package brooklyn.demo.todo

import java.util.List
import java.util.Map

import org.apache.http.util.EntityUtils;
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.config.BrooklynProperties;
import brooklyn.entity.Entity
import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.basic.DynamicGroup
import brooklyn.entity.basic.Entities;
import brooklyn.entity.dns.geoscaling.GeoscalingDnsService
import brooklyn.entity.group.Cluster
import brooklyn.entity.group.DynamicFabric
import brooklyn.entity.proxy.nginx.NginxController
import brooklyn.entity.webapp.ControlledDynamicWebAppCluster
import brooklyn.entity.webapp.DynamicWebAppCluster
import brooklyn.entity.webapp.JavaWebAppService
import brooklyn.entity.webapp.jboss.JBoss6Server
import brooklyn.entity.webapp.jboss.JBoss7Server
import brooklyn.entity.webapp.tomcat.TomcatServer
import brooklyn.launcher.BrooklynLauncher
import brooklyn.location.Location
import brooklyn.location.basic.CommandLineLocations;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.location.basic.jclouds.JcloudsLocationFactory;
import brooklyn.policy.ResizerPolicy

/**
 * Run with:
 *   java -Xmx512m -Xms128m -XX:MaxPermSize=256m -cp target/brooklyn-example-0.2.0-SNAPSHOT-with-dependencies.jar brooklyn.demo.WebAppFabricExample 
 **/
public class WebAppFabricExample extends AbstractApplication {
    public static final Logger LOG = LoggerFactory.getLogger(WebAppFabricExample)
    static BrooklynProperties config = BrooklynProperties.Factory.newDefault()

    public static final List<String> DEFAULT_LOCATIONS = [ CommandLineLocations.LOCALHOST ]

    public static final String WAR_PATH = "classpath://hello-world.war"
    
    public WebAppFabricExample(Map props=[:]) {
        super(props)
    }
    
    private DynamicFabric webFabric = new DynamicFabric(this,
                displayName: 'WebApp fabric',
                war: WAR_PATH,
                newEntity: this.&newWebCluster);

    private DynamicGroup nginxEntities = new DynamicGroup(this, name: 'Web Fronts (nginx\'s)', { it in NginxController })
    private GeoscalingDnsService geoDns = new GeoscalingDnsService(this,
            displayName: 'Geo-DNS',
            username: config.getFirst("brooklyn.geoscaling.username", defaultIfNone:'cloudsoft'), 
            password: config.getFirst("brooklyn.geoscaling.password", failIfNone:true), 
            primaryDomainName: 'geopaas.org', smartSubdomainName: 'brooklyn').
        setTargetEntityProvider(nginxEntities)
        
    protected ControlledDynamicWebAppCluster newWebCluster(Map flags, Entity owner) {
        NginxController nginxController = new NginxController(
            domain:'brooklyn.geopaas.org',
            port:8000 )

        ControlledDynamicWebAppCluster webCluster = new ControlledDynamicWebAppCluster(flags, owner).configure(
            name:"WebApp cluster",
            controller:nginxController,
            initialSize: 1, 
            webServerFactory: this.&newWebServer )

        ResizerPolicy policy = new ResizerPolicy(DynamicWebAppCluster.AVERAGE_REQUESTS_PER_SECOND).
            setSizeRange(1, 5).
            setMetricRange(10, 100)
        webCluster.cluster.addPolicy(policy)

        return webCluster
    }

    protected JavaWebAppService newWebServer(Map flags, Entity cluster) {
        return new JBoss7ServerImpl(flags, cluster).configure(httpPort: 8080, war: WAR_PATH)
    }

    public static void main(String[] argv) {
        List<Location> locations = [
                CommandLineLocations.newLocalhostLocation(),
                CommandLineLocations.newJcloudsLocation("aws-ec2", "us-west"),
                CommandLineLocations.newJcloudsLocation("aws-ec2", "ap-southeast-1")
            ]

        WebAppFabricExample app = new WebAppFabricExample(name:'Brooklyn WebApp Fabric example')
            
        BrooklynLauncher.manage(app)
        app.start(locations)
        Entities.dumpInfo(app)
    }
    
}
