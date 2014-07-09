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

import java.io.IOException;

import org.testng.Assert;
import org.testng.annotations.Test;

import brooklyn.util.collections.MutableMap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Test
public class LinkDtoTest {

//    private static final Logger log = LoggerFactory.getLogger(LinkDtoTest.class);
    
    @Test
    public void testSimple() throws IOException {
        LinkDto l = LinkDto.newInstance("http://foo", "Foo");
        
        JsonNode t = BasicDtoTest.tree(l);
        Assert.assertEquals(t.size(), 2);
        Assert.assertEquals(t.get("href").asText(), l.getHref());
        Assert.assertEquals(t.get("targetName").asText(), l.getTargetName());
        Assert.assertTrue(l.getCustomAttributes()==null || l.getCustomAttributes().isEmpty());
        
        Assert.assertEquals(l, new ObjectMapper().readValue(t.toString(), LinkDto.class));
    }

    @Test
    public void testCustomAttrs() throws IOException {
        LinkDto l = LinkDto.newInstance("http://foo", "Foo", MutableMap.of("bar", "bee"));
        
        JsonNode t = BasicDtoTest.tree(l);
        Assert.assertEquals(t.size(), 3);
        Assert.assertEquals(t.get("href").asText(), l.getHref());
        Assert.assertEquals(t.get("targetName").asText(), l.getTargetName());
        Assert.assertEquals(t.get("bar").asText(), l.getCustomAttributes().get("bar"));
        
        Assert.assertEquals(l, new ObjectMapper().readValue(t.toString(), LinkDto.class));
    }

}
