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
package org.apache.brooklyn.entity.nosql.riak;

import static org.testng.Assert.assertNotNull;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.entity.EntityAsserts;
import org.apache.brooklyn.core.entity.lifecycle.Lifecycle;
import org.apache.brooklyn.core.location.cloud.CloudLocationConfig;
import org.apache.brooklyn.entity.AbstractEc2LiveTest;
import org.apache.brooklyn.test.EntityTestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class RiakNodeEc2LiveTest extends AbstractEc2LiveTest {

    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(RiakNodeEc2LiveTest.class);

    @Override
    protected void doTest(Location loc) throws Exception {
        RiakNode entity = app.createAndManageChild(EntitySpec.create(RiakNode.class));
        app.start(ImmutableList.of(loc));

        EntityTestUtils.assertAttributeEqualsEventually(entity, RiakNode.SERVICE_UP, true);

    }

    @Test(groups = {"Live"})
    public void testWithOnlyPort22() throws Exception {
        // CentOS-6.3-x86_64-GA-EBS-02-85586466-5b6c-4495-b580-14f72b4bcf51-ami-bb9af1d2.1
        jcloudsLocation = mgmt.getLocationRegistry().resolve(LOCATION_SPEC, ImmutableMap.of(
                "tags", ImmutableList.of(getClass().getName()),
                "imageId", "us-east-1/ami-a96b01c0", 
                "hardwareId", SMALL_HARDWARE_ID));

        RiakNode server = app.createAndManageChild(EntitySpec.create(RiakNode.class)
                .configure(RiakNode.PROVISIONING_PROPERTIES.subKey(CloudLocationConfig.INBOUND_PORTS.getName()), ImmutableList.of(22))
                .configure(RiakNode.USE_HTTP_MONITORING, false));
        
        app.start(ImmutableList.of(jcloudsLocation));
        
        EntityAsserts.assertAttributeEqualsEventually(server, Attributes.SERVICE_UP, true);
        EntityAsserts.assertAttributeEqualsEventually(server, Attributes.SERVICE_STATE_ACTUAL, Lifecycle.RUNNING);
        
        Integer port = server.getAttribute(RiakNode.RIAK_PB_PORT);
        assertNotNull(port);
        
        assertViaSshLocalPortListeningEventually(server, port);
    }
}
