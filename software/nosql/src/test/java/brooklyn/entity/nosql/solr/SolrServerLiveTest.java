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
package brooklyn.entity.nosql.solr;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.util.Map;

import org.apache.solr.common.SolrDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.trait.Startable;
import brooklyn.test.EntityTestUtils;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.text.Strings;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;

/**
 * Solr live tests.
 *
 * Test the operation of the {@link SolrServer} class using the jclouds {@code rackspace-cloudservers-uk}
 * and {@code aws-ec2} providers, with different OS images. The tests use the {@link SolrJSupport} class
 * to exercise the node, and will need to have {@code brooklyn.jclouds.provider.identity} and {@code .credential}
 * set, usually in the {@code .brooklyn/brooklyn.properties} file.
 */
public class SolrServerLiveTest extends AbstractSolrServerTest {

    private static final Logger log = LoggerFactory.getLogger(SolrServerLiveTest.class);

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
        log.info("Testing Solr on {}{} using {} ({})", new Object[] { provider, Strings.isNonEmpty(region) ? ":" + region : "", description, imageId });

        Map<String, String> properties = MutableMap.of("imageId", imageId);
        testLocation = app.getManagementContext().getLocationRegistry()
                .resolve(provider + (Strings.isNonEmpty(region) ? ":" + region : ""), properties);
        solr = app.createAndManageChild(EntitySpec.create(SolrServer.class)
                .configure(SolrServer.SOLR_CORE_CONFIG, ImmutableMap.of("example", "classpath://solr/example.tgz")));
        app.start(ImmutableList.of(testLocation));

        EntityTestUtils.assertAttributeEqualsEventually(solr, Startable.SERVICE_UP, true);

        SolrJSupport client = new SolrJSupport(solr, "example");

        Iterable<SolrDocument> results = client.getDocuments();
        assertTrue(Iterables.isEmpty(results));

        client.addDocument(MutableMap.<String, Object>of("id", "1", "description", "first"));
        client.addDocument(MutableMap.<String, Object>of("id", "2", "description", "second"));
        client.addDocument(MutableMap.<String, Object>of("id", "3", "description", "third"));
        client.commit();

        results = client.getDocuments();
        assertEquals(Iterables.size(results), 3);
    }
}
