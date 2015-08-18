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
package org.apache.brooklyn.entity.webapp.jboss;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.core.test.BrooklynAppLiveTestSupport;
import org.apache.brooklyn.entity.java.UsesJmx;
import org.apache.brooklyn.test.Asserts;
import org.apache.brooklyn.test.HttpTestUtils;
import org.apache.brooklyn.test.TestResourceUnavailableException;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.apache.brooklyn.location.basic.LocalhostMachineProvisioningLocation;

import com.google.common.collect.ImmutableList;

/**
 * TODO re-write this like WebAppIntegrationTest, rather than being jboss6 specific.
 */
public class JBoss6ServerNonInheritingIntegrationTest extends BrooklynAppLiveTestSupport {
    
    // FIXME Fails deploying hello-world.war
    //     07:27:30,958 ERROR [AbstractKernelController] Error installing to Parse: name=vfs:///tmp/brooklyn-aled/apps/FJMcnSjO/entities/JBoss6Server_UZ2gA9HR/server/standard/deploy/ROOT.war state=PreParse mode=Manual requiredState=Parse: org.jboss.deployers.spi.DeploymentException: Error creating managed object for vfs:///tmp/brooklyn-aled/apps/FJMcnSjO/entities/JBoss6Server_UZ2gA9HR/server/standard/deploy/ROOT.war
    //     ...
    //     Caused by: org.xml.sax.SAXException: cvc-complex-type.2.4.d: Invalid content was found starting with element 'url-pattern'. No child element is expected at this point. @ vfs:///tmp/brooklyn-aled/apps/FJMcnSjO/entities/JBoss6Server_UZ2gA9HR/server/standard/deploy/ROOT.war/WEB-INF/web.xml[21,22]
    //         at org.jboss.xb.binding.parser.sax.SaxJBossXBParser.error(SaxJBossXBParser.java:416) [jbossxb.jar:2.0.3.GA]
    
    // Port increment for JBoss 6.
    public static final int PORT_INCREMENT = 400;

    private LocalhostMachineProvisioningLocation localhostProvisioningLocation;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        super.setUp();

        localhostProvisioningLocation = app.newLocalhostProvisioningLocation();
    }

    public String getTestWarWithNoMapping() {
        TestResourceUnavailableException.throwIfResourceUnavailable(getClass(), "/hello-world-no-mapping.war");
        return "classpath://hello-world-no-mapping.war";
    }

    @Test(groups = "Integration")
    public void testJmxmp() throws Exception {
        runTest(UsesJmx.JmxAgentModes.JMXMP);
    }

    @Test(groups = "Integration")
    public void testJmxRmi() throws Exception {
        runTest(UsesJmx.JmxAgentModes.JMX_RMI_CUSTOM_AGENT);
    }
    
    @Test(groups = "Integration")
    public void testJmxAutodetect() throws Exception {
        runTest(UsesJmx.JmxAgentModes.AUTODETECT);
    }
    
    protected void runTest(UsesJmx.JmxAgentModes jmxAgentMode) throws Exception {
        final JBoss6Server server = app.createAndManageChild(EntitySpec.create(JBoss6Server.class)
                .configure(JBoss6Server.PORT_INCREMENT, PORT_INCREMENT)
                .configure(UsesJmx.JMX_AGENT_MODE, jmxAgentMode)
                .configure("war", getTestWarWithNoMapping()));

        app.start(ImmutableList.of(localhostProvisioningLocation));
        
        String httpUrl = server.getAttribute(JBoss6Server.ROOT_URL);
        
        HttpTestUtils.assertHttpStatusCodeEventuallyEquals(httpUrl, 200);
        HttpTestUtils.assertContentContainsText(httpUrl, "Hello");

        Asserts.succeedsEventually(new Runnable() {
            @Override public void run() {
                // TODO Could test other attributes as well; see jboss7 test
                assertNotNull(server.getAttribute(JBoss6Server.REQUEST_COUNT));
                assertNotNull(server.getAttribute(JBoss6Server.ERROR_COUNT));
                assertNotNull(server.getAttribute(JBoss6Server.TOTAL_PROCESSING_TIME));
            }});
    }
}
