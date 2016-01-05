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
package org.apache.brooklyn.entity.proxy.nginx;

import static org.testng.Assert.assertNotNull;

import java.net.URL;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.location.MachineLocation;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.factory.ApplicationBuilder;
import org.apache.brooklyn.core.location.Machines;
import org.apache.brooklyn.core.test.entity.TestApplication;
import org.apache.brooklyn.entity.group.DynamicCluster;
import org.apache.brooklyn.entity.proxy.nginx.NginxController;
import org.apache.brooklyn.entity.webapp.JavaWebAppService;
import org.apache.brooklyn.entity.webapp.WebAppService;
import org.apache.brooklyn.entity.webapp.jboss.JBoss7Server;
import org.apache.brooklyn.test.Asserts;
import org.apache.brooklyn.test.HttpTestUtils;
import org.apache.brooklyn.test.support.TestResourceUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/**
 * Test Nginx proxying a cluster of JBoss7Server entities on AWS for ENGR-1689.
 *
 * This test is a proof-of-concept for the Brooklyn demo application, with each
 * service running on a separate Amazon EC2 instance.
 */
public class NginxWebClusterEc2LiveTest {
    private static final Logger LOG = LoggerFactory.getLogger(NginxWebClusterEc2LiveTest.class);
    
    private TestApplication app;
    private NginxController nginx;
    private DynamicCluster cluster;
    private Location loc;

    @BeforeMethod(alwaysRun = true)
    public void setUp() {
        ManagementContext managementContext = Entities.newManagementContext(
                ImmutableMap.of("brooklyn.location.jclouds.aws-ec2.image-id", "us-east-1/ami-2342a94a"));
        
        loc = managementContext.getLocationRegistry().resolve("aws-ec2:us-east-1");
        app = ApplicationBuilder.newManagedApp(TestApplication.class, managementContext);
    }

    @AfterMethod(alwaysRun = true)
    public void shutdown() {
        if (app != null) Entities.destroyAll(app.getManagementContext());
    }

    @Test(groups = "Live")
    public void testProvisionAwsCluster() {
        String warName = "/hello-world.war";
        TestResourceUnavailableException.throwIfResourceUnavailable(getClass(), warName);
        URL war = getClass().getResource(warName);
        assertNotNull(war, "Unable to locate resource "+warName);
        
        cluster = app.createAndManageChild(EntitySpec.create(DynamicCluster.class)
                .configure(DynamicCluster.MEMBER_SPEC, EntitySpec.create(JBoss7Server.class))
                .configure("initialSize", 2)
                .configure("httpPort", 8080)
                .configure(JavaWebAppService.ROOT_WAR, war.getPath()));
        
        nginx = app.createAndManageChild(EntitySpec.create(NginxController.class)
                .configure("cluster", cluster)
                .configure("domain", "localhost")
                .configure("port", 8000)
                .configure("portNumberSensor", WebAppService.HTTP_PORT));

        app.start(ImmutableList.of(loc));
        
        Asserts.succeedsEventually(new Runnable() {
            public void run() {
                // Nginx URL is available
                MachineLocation machine = Machines.findUniqueMachineLocation(nginx.getLocations()).get();
                String url = "http://" + machine.getAddress().getHostName() + ":" + nginx.getAttribute(NginxController.PROXY_HTTP_PORT) + "/swf-booking-mvc";
                HttpTestUtils.assertHttpStatusCodeEquals(url, 200);
    
                // Web-app URL is available
                for (Entity member : cluster.getMembers()) {
                    HttpTestUtils.assertHttpStatusCodeEquals(member.getAttribute(JavaWebAppService.ROOT_URL) + "swf-booking-mvc", 200);
                }
            }});

        nginx.stop();
    }
}
