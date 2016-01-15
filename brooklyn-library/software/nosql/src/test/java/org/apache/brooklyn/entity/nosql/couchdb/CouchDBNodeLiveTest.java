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
package org.apache.brooklyn.entity.nosql.couchdb;

import java.util.Map;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.core.entity.trait.Startable;
import org.apache.brooklyn.entity.nosql.couchdb.CouchDBNode;
import org.apache.brooklyn.test.EntityTestUtils;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.text.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

/**
 * CouchDB live tests.
 *
 * Test the operation of the {@link CouchDBNode} class using the jclouds {@code rackspace-cloudservers-uk}
 * and {@code aws-ec2} providers, with different OS images. The tests use the {@link JcouchdbSupport#jcouchdbTest(CouchDBNode)} method
 * to exercise the node, and will need to have {@code brooklyn.jclouds.provider.identity} and {@code .credential}
 * set, usually in the {@code .brooklyn/brooklyn.properties} file.
 */
public class CouchDBNodeLiveTest extends AbstractCouchDBNodeTest {

    private static final Logger log = LoggerFactory.getLogger(CouchDBNodeLiveTest.class);

    @DataProvider(name = "virtualMachineData")
    public Object[][] provideVirtualMachineData() {
        return new Object[][] { // ImageId, Provider, Region, Description (for logging)
            new Object[] { "eu-west-1/ami-0307d674", "aws-ec2", "eu-west-1", "Ubuntu Server 14.04 LTS (HVM), SSD Volume Type" },
            new Object[] { "LON/f9b690bf-88eb-43c2-99cf-391f2558732e", "rackspace-cloudservers-uk", "", "Ubuntu 12.04 LTS (Precise Pangolin)" }, 
            new Object[] { "LON/a84b1592-6817-42da-a57c-3c13f3cfc1da", "rackspace-cloudservers-uk", "", "CentOS 6.5 (PVHVM)" }, 
        };
    }

    @Test(groups = "Live", dataProvider = "virtualMachineData")
    protected void testOperatingSystemProvider(String imageId, String provider, String region, String description) throws Exception {
        log.info("Testing CouchDB on {}{} using {} ({})", new Object[] { provider, Strings.isNonEmpty(region) ? ":" + region : "", description, imageId });

        Map<String, String> properties = MutableMap.of("imageId", imageId);
        testLocation = app.getManagementContext().getLocationRegistry()
                .resolve(provider + (Strings.isNonEmpty(region) ? ":" + region : ""), properties);

        couchdb = app.createAndManageChild(EntitySpec.create(CouchDBNode.class)
                .configure("httpPort", "12345+")
                .configure("clusterName", "TestCluster"));
        app.start(ImmutableList.of(testLocation));
        EntityTestUtils.assertAttributeEqualsEventually(couchdb, Startable.SERVICE_UP, true);

        JcouchdbSupport jcouchdb = new JcouchdbSupport(couchdb);
        jcouchdb.jcouchdbTest();
    }
}
