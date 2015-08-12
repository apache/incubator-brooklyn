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
import static org.testng.Assert.assertNull;

import org.apache.brooklyn.entity.webapp.tomcat.TomcatServer;
import org.apache.brooklyn.test.EntityTestUtils;
import org.testng.annotations.Test;

import brooklyn.entity.BrooklynAppLiveTestSupport;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.test.Asserts;

import com.google.common.collect.ImmutableList;

public class TomcatServerDisableRetrieveUsageMetricsIntegrationTest extends BrooklynAppLiveTestSupport {
    
    // Note we test the default and the disabled with two entities, in the same method.
    // This piggie-backs off the necessary length of time required for the default entity
    // to have its metrics set; we then assert that the other entity does not have its set.
    @Test(groups="Integration")
    public void testDisableRetrievalOfUsageMetrics() throws Exception {
        LocalhostMachineProvisioningLocation loc = app.newLocalhostProvisioningLocation();
        final TomcatServer tc1 = app.createAndManageChild(EntitySpec.create(TomcatServer.class)
                .configure(SoftwareProcess.RETRIEVE_USAGE_METRICS, false));
        final TomcatServer tc2 = app.createAndManageChild(EntitySpec.create(TomcatServer.class));
        
        tc1.start(ImmutableList.of(loc));
        tc2.start(ImmutableList.of(loc));

        // tc2 uses defaults, so will include usage metrics
        Asserts.succeedsEventually(new Runnable() {
            public void run() {
                assertNotNull(tc2.getAttribute(TomcatServer.CONNECTOR_STATUS));
                assertNotNull(tc2.getAttribute(TomcatServer.ERROR_COUNT));
                assertNotNull(tc2.getAttribute(TomcatServer.REQUEST_COUNT));
                assertNotNull(tc2.getAttribute(TomcatServer.TOTAL_PROCESSING_TIME));
            }});

        // tc1 should have status info, but not usage metrics
        EntityTestUtils.assertAttributeEventuallyNonNull(tc1, TomcatServer.CONNECTOR_STATUS);
        EntityTestUtils.assertAttributeEqualsContinually(tc1, TomcatServer.ERROR_COUNT, null);
        assertNull(tc1.getAttribute(TomcatServer.REQUEST_COUNT));
        assertNull(tc1.getAttribute(TomcatServer.TOTAL_PROCESSING_TIME));
    }
}
