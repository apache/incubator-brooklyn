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
package brooklyn.entity.nosql.elasticsearch;

import static org.testng.Assert.assertEquals;

import java.net.URI;
import java.net.URISyntaxException;

import org.apache.http.client.methods.HttpGet;
import org.bouncycastle.util.Strings;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.group.DynamicCluster;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.trait.Startable;
import brooklyn.event.feed.http.HttpValueFunctions;
import brooklyn.location.Location;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.test.EntityTestUtils;
import brooklyn.test.entity.TestApplication;
import brooklyn.util.http.HttpTool;
import brooklyn.util.http.HttpToolResponse;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class ElasticSearchClusterIntegrationTest {

    protected TestApplication app;
    protected Location testLocation;
    protected ElasticSearchCluster elasticSearchCluster;

    @BeforeMethod(alwaysRun = true)
    public void setup() throws Exception {
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        testLocation = new LocalhostMachineProvisioningLocation();
    }

    @AfterMethod(alwaysRun = true)
    public void shutdown() {
        Entities.destroyAll(app.getManagementContext());
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
        EntityTestUtils.assertAttributeEqualsEventually(anyNode, ElasticSearchNode.DOCUMENT_COUNT, 1);
        
        int totalDocumentCount = 0;
        for (Entity entity : elasticSearchCluster.getMembers()) {
            ElasticSearchNode node = (ElasticSearchNode)entity;
            String getBaseUri = "http://" + node.getAttribute(Attributes.HOSTNAME) + ":" + node.getAttribute(Attributes.HTTP_PORT);
            HttpToolResponse getResponse = HttpTool.execAndConsume(
                    HttpTool.httpClientBuilder().build(),
                    new HttpGet(getBaseUri + "/mydocuments/docs/1/_source"));
            assertEquals(getResponse.getResponseCode(), 200);
            assertEquals(HttpValueFunctions.jsonContents("foo", String.class).apply(getResponse), "bar");
            
            totalDocumentCount += node.getAttribute(ElasticSearchNode.DOCUMENT_COUNT);
        }
        assertEquals(totalDocumentCount, 1);
    }
}
