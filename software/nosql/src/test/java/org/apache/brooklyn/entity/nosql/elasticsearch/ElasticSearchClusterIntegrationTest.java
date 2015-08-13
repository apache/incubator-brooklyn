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
package org.apache.brooklyn.entity.nosql.elasticsearch;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.net.URI;
import java.net.URISyntaxException;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.proxying.EntitySpec;
import org.apache.brooklyn.entity.nosql.elasticsearch.ElasticSearchCluster;
import org.apache.brooklyn.entity.nosql.elasticsearch.ElasticSearchNode;
import org.apache.brooklyn.test.EntityTestUtils;
import org.apache.http.client.methods.HttpGet;
import org.bouncycastle.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.BrooklynAppLiveTestSupport;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.trait.Startable;
import brooklyn.event.feed.http.HttpValueFunctions;
import brooklyn.location.Location;
import brooklyn.test.Asserts;
import brooklyn.util.http.HttpTool;
import brooklyn.util.http.HttpToolResponse;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class ElasticSearchClusterIntegrationTest extends BrooklynAppLiveTestSupport {

    // FIXME Exception in thread "main" java.lang.UnsupportedClassVersionError: org/elasticsearch/bootstrap/Elasticsearch : Unsupported major.minor version 51.0

    private static final Logger LOG = LoggerFactory.getLogger(ElasticSearchClusterIntegrationTest.class);

    protected Location testLocation;
    protected ElasticSearchCluster elasticSearchCluster;

    @BeforeMethod(alwaysRun = true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        testLocation = app.newLocalhostProvisioningLocation();
    }

    @Test(groups = {"Integration"})
    public void testStartupAndShutdown() {
        elasticSearchCluster = app.createAndManageChild(EntitySpec.create(ElasticSearchCluster.class)
                .configure(DynamicCluster.INITIAL_SIZE, 3));
        app.start(ImmutableList.of(testLocation));
        
        EntityTestUtils.assertAttributeEqualsEventually(elasticSearchCluster, Startable.SERVICE_UP, true);
        
        elasticSearchCluster.stop();
        
        EntityTestUtils.assertAttributeEqualsEventually(elasticSearchCluster, Startable.SERVICE_UP, false);
    }
    
    @Test(groups = {"Integration"})
    public void testPutAndGet() throws URISyntaxException {
        elasticSearchCluster = app.createAndManageChild(EntitySpec.create(ElasticSearchCluster.class)
                .configure(DynamicCluster.INITIAL_SIZE, 3));
        app.start(ImmutableList.of(testLocation));
        
        EntityTestUtils.assertAttributeEqualsEventually(elasticSearchCluster, Startable.SERVICE_UP, true);
        assertEquals(elasticSearchCluster.getMembers().size(), 3);
        assertEquals(clusterDocumentCount(), 0);
        
        ElasticSearchNode anyNode = (ElasticSearchNode)elasticSearchCluster.getMembers().iterator().next();
        
        String document = "{\"foo\" : \"bar\",\"baz\" : \"quux\"}";
        
        String putBaseUri = "http://" + anyNode.getAttribute(Attributes.HOSTNAME) + ":" + anyNode.getAttribute(Attributes.HTTP_PORT);
        
        HttpToolResponse putResponse = HttpTool.httpPut(
                HttpTool.httpClientBuilder()
                    .port(anyNode.getAttribute(Attributes.HTTP_PORT))
                    .build(), 
                new URI(putBaseUri + "/mydocuments/docs/1"), 
                ImmutableMap.<String, String>of(), 
                Strings.toByteArray(document)); 
        assertEquals(putResponse.getResponseCode(), 201);
        
        for (Entity entity : elasticSearchCluster.getMembers()) {
            ElasticSearchNode node = (ElasticSearchNode)entity;
            String getBaseUri = "http://" + node.getAttribute(Attributes.HOSTNAME) + ":" + node.getAttribute(Attributes.HTTP_PORT);
            HttpToolResponse getResponse = HttpTool.execAndConsume(
                    HttpTool.httpClientBuilder().build(),
                    new HttpGet(getBaseUri + "/mydocuments/docs/1/_source"));
            assertEquals(getResponse.getResponseCode(), 200);
            assertEquals(HttpValueFunctions.jsonContents("foo", String.class).apply(getResponse), "bar");
        }
        Asserts.succeedsEventually(new Runnable() {
            public void run() {
                int count = clusterDocumentCount();
                assertTrue(count >= 1, "count="+count);
                LOG.debug("Document count is {}", count);
            }});
    }
    
    private int clusterDocumentCount() {
        int result = 0;
        for (Entity entity : elasticSearchCluster.getMembers()) {
            result += entity.getAttribute(ElasticSearchNode.DOCUMENT_COUNT);
        }
        return result;
    }
}
