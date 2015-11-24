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
package org.apache.brooklyn.entity.webapp.tomcat;

import static org.testng.Assert.assertNotNull;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.entity.EntityAsserts;
import org.apache.brooklyn.core.entity.lifecycle.Lifecycle;
import org.apache.brooklyn.core.location.cloud.CloudLocationConfig;
import org.apache.brooklyn.entity.AbstractEc2LiveTest;
import org.apache.brooklyn.test.Asserts;
import org.apache.brooklyn.test.HttpTestUtils;
import org.apache.brooklyn.test.support.TestResourceUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/**
 * A simple test of installing+running on AWS-EC2, using various OS distros and versions. 
 */
public class TomcatServerEc2LiveTest extends AbstractEc2LiveTest {

    private static final Logger LOG = LoggerFactory.getLogger(TomcatServerEc2LiveTest.class);

    public String getTestWar() {
        TestResourceUnavailableException.throwIfResourceUnavailable(getClass(), "/hello-world.war");
        return "classpath://hello-world.war";
    }

    @Override
    protected void doTest(Location loc) throws Exception {
        final TomcatServer server = app.createAndManageChild(EntitySpec.create(TomcatServer.class)
                .configure(TomcatServer.OPEN_IPTABLES, true)
                .configure("war", getTestWar()));
        
        app.start(ImmutableList.of(loc));
        
        String url = server.getAttribute(TomcatServer.ROOT_URL);
        
        HttpTestUtils.assertHttpStatusCodeEventuallyEquals(url, 200);
        HttpTestUtils.assertContentContainsText(url, "Hello");
        
        Asserts.succeedsEventually(new Runnable() {
            @Override public void run() {
                assertNotNull(server.getAttribute(TomcatServer.REQUEST_COUNT));
                assertNotNull(server.getAttribute(TomcatServer.ERROR_COUNT));
                assertNotNull(server.getAttribute(TomcatServer.TOTAL_PROCESSING_TIME));
            }});
    }
    
    @Test(groups = {"Live"})
    public void testWithOnlyPort22() throws Exception {
        // CentOS-6.3-x86_64-GA-EBS-02-85586466-5b6c-4495-b580-14f72b4bcf51-ami-bb9af1d2.1
        jcloudsLocation = mgmt.getLocationRegistry().resolve(LOCATION_SPEC, ImmutableMap.of(
                "tags", ImmutableList.of(getClass().getName()),
                "imageId", "us-east-1/ami-a96b01c0", 
                "hardwareId", SMALL_HARDWARE_ID));

        final TomcatServer server = app.createAndManageChild(EntitySpec.create(TomcatServer.class)
                .configure(TomcatServer.PROVISIONING_PROPERTIES.subKey(CloudLocationConfig.INBOUND_PORTS.getName()), ImmutableList.of(22))
                .configure(TomcatServer.USE_JMX, false)
                .configure(TomcatServer.OPEN_IPTABLES, true)
//                .configure(TomcatServer.PRE_INSTALL_COMMAND, BashCommands.sudo("yum upgrade -y ca-certificates --disablerepo=epel"))
                .configure("war", getTestWar()));
        
        app.start(ImmutableList.of(jcloudsLocation));
        
        EntityAsserts.assertAttributeEqualsEventually(server, Attributes.SERVICE_UP, true);
        EntityAsserts.assertAttributeEqualsEventually(server, Attributes.SERVICE_STATE_ACTUAL, Lifecycle.RUNNING);
        
        String url = server.getAttribute(TomcatServer.ROOT_URL);
        assertNotNull(url);
        
        assertViaSshLocalUrlListeningEventually(server, url);
    }
    
    @Test(enabled=false)
    public void testDummy() {} // Convince testng IDE integration that this really does have test methods  
}
