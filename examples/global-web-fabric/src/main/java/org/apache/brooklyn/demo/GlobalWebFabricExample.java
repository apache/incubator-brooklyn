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

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.brooklyn.api.catalog.Catalog;
import org.apache.brooklyn.api.catalog.CatalogConfig;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.config.StringConfigMap;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.entity.AbstractApplication;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.StartableApplication;
import org.apache.brooklyn.core.location.PortRanges;
import org.apache.brooklyn.core.sensor.PortAttributeSensorAndConfigKey;
import org.apache.brooklyn.entity.dns.geoscaling.GeoscalingDnsService;
import org.apache.brooklyn.entity.group.DynamicRegionsFabric;
import org.apache.brooklyn.entity.proxy.AbstractController;
import org.apache.brooklyn.entity.webapp.ElasticJavaWebAppService;
import org.apache.brooklyn.entity.webapp.JavaWebAppService;
import org.apache.brooklyn.launcher.BrooklynLauncher;
import org.apache.brooklyn.util.CommandLineUtil;
import org.apache.brooklyn.util.core.BrooklynMavenArtifacts;
import org.apache.brooklyn.util.core.ResourceUtils;

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
