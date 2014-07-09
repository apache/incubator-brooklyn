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
package io.brooklyn.camp.dto;

import io.brooklyn.camp.BasicCampPlatform;
import io.brooklyn.camp.CampServer;
import io.brooklyn.camp.commontypes.RepresentationSkew;
import io.brooklyn.camp.rest.util.CampRestGuavas;
import io.brooklyn.camp.spi.AbstractResource;

import java.io.IOException;
import java.util.Arrays;

import org.testng.Assert;
import org.testng.annotations.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Test
public class ResourceDtoTest {

//    private static final Logger log = LoggerFactory.getLogger(ResourceDtoTest.class);
    
    CampServer s;
    AbstractResource rr;
    ResourceDto r;
    
    @SuppressWarnings("unchecked")
    protected void initSimpleDto() {
        s = new CampServer(new BasicCampPlatform(), "http://atest/");
        s.getDtoFactory().getUriFactory().registerIdentityFunction(AbstractResource.class, "basic", CampRestGuavas.IDENTITY_OF_REST_RESOURCE);
        rr = AbstractResource.builder().name("Name").description("a description").
                tags(Arrays.asList("tag1", "tag 2")).representationSkew(RepresentationSkew.NONE).build();
        r = ResourceDto.newInstance(s.getDtoFactory(), rr);
    }
    
    @Test
    public void testSimpleCreation() throws IOException {
        initSimpleDto();
        
        Assert.assertNotNull(r.getCreatedAsString());
        Assert.assertEquals(r.getName(), "Name");
        Assert.assertEquals(r.getDescription(), "a description");
        Assert.assertEquals(r.getTags(), Arrays.asList("tag1", "tag 2"));
        Assert.assertEquals(r.getRepresentationSkew(), RepresentationSkew.NONE);
    }
    
    public void testSimpleSerializationAndDeserialization() throws IOException {
        initSimpleDto();
        
        JsonNode t = BasicDtoTest.tree(r);
        
//        Assert.assertEquals(t.get("uri").asText(), r.getUri());
        ResourceDto r2 = new ObjectMapper().readValue(t.toString(), ResourceDto.class);
        Assert.assertNotNull(r2.getCreated());
        Assert.assertEquals(r, r2);
    }


}
