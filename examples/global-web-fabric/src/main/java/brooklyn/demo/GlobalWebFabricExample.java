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

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.catalog.Catalog;
import brooklyn.catalog.CatalogConfig;
import brooklyn.config.ConfigKey;
import brooklyn.config.StringConfigMap;
import brooklyn.entity.basic.AbstractApplication;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.StartableApplication;
import brooklyn.entity.dns.geoscaling.GeoscalingDnsService;
import brooklyn.entity.group.DynamicRegionsFabric;
import brooklyn.entity.proxy.AbstractController;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.webapp.ElasticJavaWebAppService;
import brooklyn.entity.webapp.JavaWebAppService;
import brooklyn.event.basic.PortAttributeSensorAndConfigKey;
import brooklyn.launcher.BrooklynLauncher;
import brooklyn.location.basic.PortRanges;
import brooklyn.util.BrooklynMavenArtifacts;
import brooklyn.util.CommandLineUtil;
import brooklyn.util.ResourceUtils;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

@Catalog(name="Global Web Fabric",
description="Deploys a WAR to multiple clusters, showing how Brooklyn fabrics work",
iconUrl="classpath://brooklyn/demo/glossy-3d-blue-web-icon.png")
public class GlobalWebFabricExample extends AbstractApplication {

    public static final Logger log = LoggerFactory.getLogger(GlobalWebFabricExample.class);
    
    static final List<String> DEFAULT_LOCATIONS = ImmutableList.of(
            "aws-ec2:eu-west-1",
            "aws-ec2:ap-southeast-1",
            "aws-ec2:us-west-1" );
    
    public static final String DEFAULT_WAR_PATH = ResourceUtils.create(GlobalWebFabricExample.class)
        // take this war, from the classpath, or via maven if not on the classpath
        .firstAvailableUrl(
                "classpath://hello-world-webapp.war",
                BrooklynMavenArtifacts.localUrl("example", "brooklyn-example-hello-world-webapp", "war"))
        .or("classpath://hello-world-webapp.war");

    @CatalogConfig(label="WAR (URL)", priority=2)
    public static final ConfigKey<String> WAR_PATH = ConfigKeys.newConfigKey(
        "app.war", "URL to the application archive which should be deployed", 
        DEFAULT_WAR_PATH);    

    // load-balancer instances must run on some port to work with GeoDNS, port 80 to work without special, so make that default
    // (but included here in case it runs on a different port on all machines, or use a range to work with multiple localhosts)
    @CatalogConfig(label="Proxy server HTTP port")
    public static final PortAttributeSensorAndConfigKey PROXY_HTTP_PORT =
        new PortAttributeSensorAndConfigKey(AbstractController.PROXY_HTTP_PORT, PortRanges.fromInteger(80));
    
    @Override
    public void initApp() {
        StringConfigMap config = getManagementContext().getConfig();
        
        GeoscalingDnsService geoDns = addChild(EntitySpec.create(GeoscalingDnsService.class)
                .displayName("GeoScaling DNS")
                .configure("username", checkNotNull(config.getFirst("brooklyn.geoscaling.username"), "username"))
                .configure("password", checkNotNull(config.getFirst("brooklyn.geoscaling.password"), "password"))
                .configure("primaryDomainName", checkNotNull(config.getFirst("brooklyn.geoscaling.primaryDomain"), "primaryDomain")) 
                .configure("smartSubdomainName", "brooklyn"));
        
        DynamicRegionsFabric webFabric = addChild(EntitySpec.create(DynamicRegionsFabric.class)
                .displayName("Web Fabric")
                .configure(DynamicRegionsFabric.FACTORY, new ElasticJavaWebAppService.Factory())
                
                //specify the WAR file to use
                .configure(JavaWebAppService.ROOT_WAR, Entities.getRequiredUrlConfig(this, WAR_PATH)) );

        //tell GeoDNS what to monitor
        geoDns.setTargetEntityProvider(webFabric);
    }

    public static void main(String[] argv) {
        List<String> args = Lists.newArrayList(argv);
        String port =  CommandLineUtil.getCommandLineOption(args, "--port", "8081+");
        String locations = CommandLineUtil.getCommandLineOption(args, "--locations", Joiner.on(",").join(DEFAULT_LOCATIONS));

        BrooklynLauncher launcher = BrooklynLauncher.newInstance()
                .application(EntitySpec.create(StartableApplication.class, GlobalWebFabricExample.class).displayName("Brooklyn Global Web Fabric Example"))
                .webconsolePort(port)
                .locations(Arrays.asList(locations))
                .start();
         
        Entities.dumpInfo(launcher.getApplications());
    }
}
