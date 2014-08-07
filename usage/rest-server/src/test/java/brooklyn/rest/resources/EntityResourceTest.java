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
package brooklyn.rest.resources;

import java.util.List;

import javax.annotation.Nullable;
import javax.ws.rs.core.MediaType;

import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.rest.domain.ApplicationSpec;
import brooklyn.rest.domain.EntitySpec;
import brooklyn.rest.testing.BrooklynRestResourceTest;
import brooklyn.rest.testing.mocks.RestMockSimpleEntity;
import brooklyn.util.collections.MutableList;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.GenericType;

@Test(singleThreaded = true)
public class EntityResourceTest extends BrooklynRestResourceTest {

    private final ApplicationSpec simpleSpec = ApplicationSpec.builder()
            .name("simple-app")
            .entities(ImmutableSet.of(new EntitySpec("simple-ent", RestMockSimpleEntity.class.getName())))
            .locations(ImmutableSet.of("localhost"))
            .build();

    private EntityInternal entity;

    private static final String entityEndpoint = "/v1/applications/simple-app/entities/simple-ent";

    @BeforeClass(alwaysRun = true)
    @Override
    public void setUp() throws Exception {
        super.setUp();

        // Deploy application
        ClientResponse deploy = clientDeploy(simpleSpec);
        waitForApplicationToBeRunning(deploy.getLocation());

        // Add tag
        entity = (EntityInternal) Iterables.find(getManagementContext().getEntityManager().getEntities(), new Predicate<Entity>() {
            @Override
            public boolean apply(@Nullable Entity input) {
                return "RestMockSimpleEntity".equals(input.getEntityType().getSimpleName());
            }
        });
    }

    @Test
    public void testTagsSanity() throws Exception {
        entity.getTagSupport().addTag("foo");
        
        ClientResponse response = client().resource(entityEndpoint + "/tags")
                .accept(MediaType.APPLICATION_JSON)
                .get(ClientResponse.class);
        List<Object> tags = response.getEntity(new GenericType<List<Object>>(List.class) {});

        Assert.assertTrue(tags.contains("foo"));
        Assert.assertFalse(tags.contains("bar"));
    }
    
    // TODO any entity or complex object should be cleaned up as part of WebResourceUtils call
    @Test(groups="WIP")
    public void testTagsDoNotSerializeTooMuch() throws Exception {
        entity.getTagSupport().addTag("foo");
        entity.getTagSupport().addTag(entity.getParent());

        ClientResponse response = client().resource(entityEndpoint + "/tags")
                .accept(MediaType.APPLICATION_JSON)
                .get(ClientResponse.class);
        List<Object> tags = response.getEntity(new GenericType<List<Object>>(List.class) {});

        Assert.assertEquals(tags.size(), 2, "tags are: "+tags);
        
        Assert.assertTrue(tags.contains("foo"));
        Assert.assertFalse(tags.contains("bar"));
        
        MutableList<Object> appTags = MutableList.copyOf(tags);
        appTags.remove("foo");
        Object appTag = Iterables.getOnlyElement( appTags );
        Assert.assertTrue((""+appTag).contains(entity.getParent().getId()), "unexpected app tag, does not include ID: "+appTag);
        
        Assert.assertTrue((""+appTag).length() < 1000, "unexpected app tag, includes too much mgmt info (len "+(""+appTag).length()+"): "+appTag);
        
        Assert.assertFalse((""+appTag).contains(entity.getManagementContext().getManagementNodeId()), "unexpected app tag, includes too much mgmt info: "+appTag);
        Assert.assertFalse((""+appTag).contains("managementContext"), "unexpected app tag, includes too much mgmt info: "+appTag);
        Assert.assertFalse((""+appTag).contains("localhost"), "unexpected app tag, includes too much mgmt info: "+appTag);
        Assert.assertFalse((""+appTag).contains("catalog"), "unexpected app tag, includes too much mgmt info: "+appTag);
    }
    
}
