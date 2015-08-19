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
package org.apache.brooklyn.entity.nosql.cassandra;

import static org.testng.Assert.assertNotNull;

import java.math.BigInteger;
import java.util.Set;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.core.entity.trait.Startable;
import org.apache.brooklyn.core.mgmt.rebind.RebindOptions;
import org.apache.brooklyn.core.mgmt.rebind.RebindTestFixtureWithApp;
import org.apache.brooklyn.entity.proxy.nginx.NginxController;
import org.apache.brooklyn.test.EntityTestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.apache.brooklyn.location.localhost.LocalhostMachineProvisioningLocation;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

/**
 * Test the operation of the {@link NginxController} class.
 */
public class CassandraDatacenterRebindIntegrationTest extends RebindTestFixtureWithApp {
    private static final Logger LOG = LoggerFactory.getLogger(CassandraDatacenterRebindIntegrationTest.class);

    private LocalhostMachineProvisioningLocation localhostProvisioningLocation;
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        CassandraNodeIntegrationTest.assertCassandraPortsAvailableEventually();
        super.setUp();
        localhostProvisioningLocation = origApp.newLocalhostProvisioningLocation();
    }

    @AfterMethod(alwaysRun=true)
    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        CassandraNodeIntegrationTest.assertCassandraPortsAvailableEventually();
    }
    
    /**
     * Test that Brooklyn can rebind to a single node datacenter.
     */
    @Test(groups = "Integration")
    public void testRebindDatacenterOfSizeOne() throws Exception {
        CassandraDatacenter origDatacenter = origApp.createAndManageChild(EntitySpec.create(CassandraDatacenter.class)
                .configure("initialSize", 1));

        origApp.start(ImmutableList.of(localhostProvisioningLocation));
        CassandraNode origNode = (CassandraNode) Iterables.get(origDatacenter.getMembers(), 0);

        EntityTestUtils.assertAttributeEqualsEventually(origDatacenter, CassandraDatacenter.GROUP_SIZE, 1);
        CassandraDatacenterLiveTest.assertNodesConsistent(ImmutableList.of(origNode));
        CassandraDatacenterLiveTest.assertSingleTokenConsistent(ImmutableList.of(origNode));
        CassandraDatacenterLiveTest.checkConnectionRepeatedly(2, 5, ImmutableList.of(origNode));
        BigInteger origToken = origNode.getAttribute(CassandraNode.TOKEN);
        Set<BigInteger> origTokens = origNode.getAttribute(CassandraNode.TOKENS);
        assertNotNull(origToken);
        
        newApp = rebind(RebindOptions.create().terminateOrigManagementContext(true));
        final CassandraDatacenter newDatacenter = (CassandraDatacenter) Iterables.find(newApp.getChildren(), Predicates.instanceOf(CassandraDatacenter.class));
        final CassandraNode newNode = (CassandraNode) Iterables.find(newDatacenter.getMembers(), Predicates.instanceOf(CassandraNode.class));
        
        EntityTestUtils.assertAttributeEqualsEventually(newDatacenter, CassandraDatacenter.GROUP_SIZE, 1);
        EntityTestUtils.assertAttributeEqualsEventually(newNode, Startable.SERVICE_UP, true);
        EntityTestUtils.assertAttributeEqualsEventually(newNode, CassandraNode.TOKEN, origToken);
        EntityTestUtils.assertAttributeEqualsEventually(newNode, CassandraNode.TOKENS, origTokens);
        CassandraDatacenterLiveTest.assertNodesConsistent(ImmutableList.of(newNode));
        CassandraDatacenterLiveTest.assertSingleTokenConsistent(ImmutableList.of(newNode));
        CassandraDatacenterLiveTest.checkConnectionRepeatedly(2, 5, ImmutableList.of(newNode));
    }
}
