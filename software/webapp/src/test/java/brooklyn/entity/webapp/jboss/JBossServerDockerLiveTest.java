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
package brooklyn.entity.webapp.jboss;

import static org.testng.Assert.assertNotNull;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.software.AbstractDockerLiveTest;
import brooklyn.entity.webapp.JavaWebAppSoftwareProcess;
import brooklyn.entity.webapp.WebAppServiceConstants;
import brooklyn.entity.webapp.WebAppServiceMetrics;
import brooklyn.location.Location;
import brooklyn.test.Asserts;
import brooklyn.test.HttpTestUtils;
import brooklyn.test.TestResourceUnavailableException;

import com.google.common.collect.ImmutableList;

/**
 * A simple test of installing+running JBoss type servers on Docker, using various OS distros and versions.
 */
public abstract class JBossServerDockerLiveTest extends AbstractDockerLiveTest {

   public String getTestWar() {
      TestResourceUnavailableException.throwIfResourceUnavailable(getClass(), "/hello-world.war");
      return "classpath://hello-world.war";
   }

   @Override
   protected void doTest(Location loc) throws Exception {
       final JavaWebAppSoftwareProcess server = app.createAndManageChild(EntitySpec.create(getServerType())
               .configure("war", getTestWar()));
       
       app.start(ImmutableList.of(loc));
       
       String url = server.getAttribute(WebAppServiceConstants.ROOT_URL);
       
       HttpTestUtils.assertHttpStatusCodeEventuallyEquals(url, 200);
       HttpTestUtils.assertContentContainsText(url, "Hello");
       
       Asserts.succeedsEventually(new Runnable() {
           @Override public void run() {
               assertNotNull(server.getAttribute(WebAppServiceMetrics.REQUEST_COUNT));
               assertNotNull(server.getAttribute(WebAppServiceMetrics.ERROR_COUNT));
               assertNotNull(server.getAttribute(WebAppServiceMetrics.TOTAL_PROCESSING_TIME));
               assertNotNull(server.getAttribute(WebAppServiceMetrics.MAX_PROCESSING_TIME));
               assertNotNull(server.getAttribute(WebAppServiceMetrics.BYTES_RECEIVED));
               assertNotNull(server.getAttribute(WebAppServiceMetrics.BYTES_SENT));
           }});
   }

   protected abstract Class<? extends JavaWebAppSoftwareProcess> getServerType();
}
