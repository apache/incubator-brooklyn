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
package org.apache.brooklyn.entity.webapp;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.location.PortRange;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.location.PortRanges;
import org.apache.brooklyn.core.test.BrooklynAppUnitTestSupport;
import org.apache.brooklyn.entity.webapp.jboss.JBoss6Server;
import org.apache.brooklyn.entity.webapp.jboss.JBoss6ServerImpl;
import org.apache.brooklyn.entity.webapp.jboss.JBoss7Server;
import org.apache.brooklyn.entity.webapp.jboss.JBoss7ServerImpl;
import org.apache.brooklyn.entity.webapp.tomcat.TomcatServer;
import org.apache.brooklyn.test.EntityTestUtils;
import org.apache.brooklyn.util.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/**
 * This tests that we can run jboss entity on AWS.
 */
public class WebAppLiveIntegrationTest extends BrooklynAppUnitTestSupport {
    public static final PortRange DEFAULT_HTTP_PORT = PortRanges.fromInteger(8080);
    public static final PortRange DEFAULT_JMX_PORT = PortRanges.fromInteger(32199);

    // Port increment for JBoss 6.
    public static final int PORT_INCREMENT = 400;

    // The parent application entity for these tests
    Location loc;

    /**
     * Provides instances of {@link TomcatServer}, {@link JBoss6Server} and {@link JBoss7Server} to the tests below.
     *
     * TODO combine the data provider here with the integration tests
     *
     * @see WebAppIntegrationTest#basicEntities()
     */
    @DataProvider(name = "basicEntities")
    public Object[][] basicEntities() {
        return new Object[][] {
                { EntitySpec.create(TomcatServer.class)
                    .configure(TomcatServer.HTTP_PORT, DEFAULT_HTTP_PORT)
                    .configure(TomcatServer.JMX_PORT, DEFAULT_JMX_PORT) },
                { EntitySpec.create(JBoss6ServerImpl.class)
                    .configure(JBoss6Server.PORT_INCREMENT, PORT_INCREMENT)
                    .configure(JBoss6Server.JMX_PORT, DEFAULT_JMX_PORT) },
                { EntitySpec.create(JBoss7ServerImpl.class)
                    .configure(JBoss7Server.PORT_INCREMENT, PORT_INCREMENT) } };
    }

    @Override
    @BeforeMethod(alwaysRun = true)
    public void setUp() {
        loc = mgmt.getLocationRegistry().resolve("aws-ec2:us-east-1", ImmutableMap.of(
                "imagel-id", "us-east-1/ami-2342a94a",
                "image-owner", "411009282317"));
    }

    @Test(groups = "Live", dataProvider="basicEntities")
    public void testStartsWebAppInAws(final EntitySpec<JavaWebAppSoftwareProcess> spec) {
        JavaWebAppSoftwareProcess server = app.createAndManageChild(spec);
        server.start(ImmutableList.of(loc));
        EntityTestUtils.assertAttributeEqualsEventually(ImmutableMap.of("timeout", Duration.seconds(75)),
                server, Attributes.SERVICE_UP, Boolean.TRUE);
    }
}
