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

import com.google.common.collect.ImmutableList;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.entity.lifecycle.Lifecycle;
import org.apache.brooklyn.core.entity.trait.Startable;
import org.apache.brooklyn.entity.AbstractCloudFoundryPaasLocationLiveTest;
import org.apache.brooklyn.entity.software.base.SoftwareProcess;
import org.apache.brooklyn.entity.webapp.WebAppService;
import org.apache.brooklyn.entity.webapp.WebAppServiceConstants;
import org.apache.brooklyn.test.Asserts;
import org.apache.brooklyn.util.exceptions.PropagatedRuntimeException;
import org.testng.annotations.Test;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

@Test(groups = {"Live"})
public class TomcatCloudFoundryLiveTest extends AbstractCloudFoundryPaasLocationLiveTest {

    private final String APPLICATION_ARTIFACT_NAME = "hello-world.war";

    private final String APPLICATION_ARTIFACT_URL =
            getClasspathUrlForResource(APPLICATION_ARTIFACT_NAME);

    public void deployApplicationTest() throws Exception {
        final TomcatServer server = app.
                createAndManageChild(EntitySpec.create(TomcatServer.class)
                        .configure("wars.root", APPLICATION_ARTIFACT_URL)
                        .location(cloudFoundryPaasLocation));

        app.start(ImmutableList.of(cloudFoundryPaasLocation));

        Asserts.succeedsEventually(new Runnable() {
            public void run() {
                assertTrue(server.getAttribute(Startable.SERVICE_UP));
                assertTrue(server.getAttribute(SoftwareProcess
                        .SERVICE_PROCESS_IS_RUNNING));

                assertNotNull(server.getAttribute(Attributes.MAIN_URI));
                assertNotNull(server.getAttribute(WebAppService.ROOT_URL));
                assertEquals(server.getAttribute(WebAppServiceConstants.ENABLED_PROTOCOLS).size(), 2);
                assertTrue(server.isHttpEnabled());
                assertTrue(server.isHttpsEnabled());
                assertEquals(server.getHttpPort(), new Integer(8080));
                assertEquals(server.getHttpsPort(), new Integer(443));

            }
        });
    }

    public void wrongApplicationOnFireStatusTest() throws Exception {
        final TomcatServer server = app.
                createAndManageChild(EntitySpec.create(TomcatServer.class)
                        .configure("wars.root", APPLICATION_ARTIFACT_URL + "wrong")
                        .location(cloudFoundryPaasLocation));

        Asserts.succeedsEventually(new Runnable() {
            public void run() {
                try {
                    app.start(ImmutableList.of(cloudFoundryPaasLocation));
                } catch (PropagatedRuntimeException e) {
                    assertEquals(server.getAttribute(TomcatServer.SERVICE_STATE_ACTUAL),
                            Lifecycle.ON_FIRE);
                }
            }
        });
    }

    public void stopApplicationTest() throws Exception {
        final TomcatServer server = app.
                createAndManageChild(EntitySpec.create(TomcatServer.class)
                        .configure("wars.root", APPLICATION_ARTIFACT_URL)
                        .location(cloudFoundryPaasLocation));

        app.start(ImmutableList.of(cloudFoundryPaasLocation));

        Asserts.succeedsEventually(new Runnable() {
            public void run() {
                assertTrue(server.getAttribute(Startable.SERVICE_UP));

                app.stop();
                assertEquals(server.getAttribute(TomcatServer
                        .SERVICE_STATE_ACTUAL), Lifecycle.STOPPED);
                assertFalse(server.getAttribute(Startable.SERVICE_UP));
                assertFalse(server.getAttribute(TomcatServer
                        .SERVICE_PROCESS_IS_RUNNING));
            }
        });
    }


}
