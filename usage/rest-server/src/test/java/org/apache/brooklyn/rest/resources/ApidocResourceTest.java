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
package org.apache.brooklyn.rest.resources;

import static org.testng.Assert.assertEquals;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

import org.apache.brooklyn.rest.BrooklynRestApi;
import org.apache.brooklyn.rest.testing.BrooklynRestResourceTest;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

/**
 * @author Adam Lowe
 */
@Test(singleThreaded = true)
public class ApidocResourceTest extends BrooklynRestResourceTest {

    private static final Logger log = LoggerFactory.getLogger(ApidocResourceTest.class);

    @Override
    protected void addBrooklynResources() {
        for (Object o : BrooklynRestApi.getApidocResources()) {
            addResource(o);
        }
        super.addBrooklynResources();
    }
    
    @Test
    public void testRootSerializesSensibly() throws Exception {
        String data = client().resource("/v1/apidoc/swagger.json").get(String.class);
        log.info("apidoc gives: "+data);
        // make sure no scala gets in
        Assert.assertFalse(data.contains("$"));
        Assert.assertFalse(data.contains("scala"));
    }
    
//    @Test
//    public void testCountRestResources() throws Exception {
//        ApidocRoot response = client().resource("/v1/apidoc/").get(ApidocRoot.class);
//        assertEquals(response.getApis().size(), 1 + Iterables.size(BrooklynRestApi.getBrooklynRestResources()));
//    }
//
//    @Test
//    public void testEndpointSerializesSensibly() throws Exception {
//        String data = client().resource("/v1/apidoc/org.apache.brooklyn.rest.resources.ApidocResource").get(String.class);
//        log.info("apidoc endpoint resource gives: "+data);
//        // make sure no scala gets in
//        Assert.assertFalse(data.contains("$"));
//        Assert.assertFalse(data.contains("scala"));
//    }
//
//    @Test
//    public void testApiDocDetails() throws Exception {
//        ApidocRoot response = client().resource("/v1/apidoc/org.apache.brooklyn.rest.resources.ApidocResource").get(ApidocRoot.class);
//        assertEquals(countOperations(response), 2);
//    }
//
//    @Test
//    public void testEffectorDetails() throws Exception {
//        ApidocRoot response = client().resource("/v1/apidoc/org.apache.brooklyn.rest.resources.EffectorResource").get(ApidocRoot.class);
//        assertEquals(countOperations(response), 2);
//    }
//
//    @Test
//    public void testEntityDetails() throws Exception {
//        ApidocRoot response = client().resource("/v1/apidoc/org.apache.brooklyn.rest.resources.EntityResource").get(ApidocRoot.class);
//        assertEquals(countOperations(response), 14);
//    }
//
//    @Test
//    public void testCatalogDetails() throws Exception {
//        ApidocRoot response = client().resource("/v1/apidoc/org.apache.brooklyn.rest.resources.CatalogResource").get(ApidocRoot.class);
//        assertEquals(countOperations(response), 22, "ops="+getOperations(response));
//    }

    @SuppressWarnings("rawtypes")
    @Test
    public void testAllAreLoadable() throws Exception {
        // sometimes -- e.g. if an annotation refers to a class name with the wrong case -- the call returns a 500 and breaks apidoc; ensure we don't trigger that.  
        Map response = client().resource("/v1/apidoc/swagger.json").get(Map.class);
        // "Documenation" object does not include the links :( so traverse via map
        log.debug("root doc response is: "+response);
        List apis = (List)response.get("apis");
        for (Object api: apis) {
            String link = (String) ((Map)api).get("link");
            try {
                Map r2 = client().resource(link).get(Map.class);
                log.debug("doc for "+link+" is: "+r2);
            } catch (Exception e) {
                log.error("Error in swagger/apidoc annotations, unparseable, at "+link+": "+e, e);
                Assert.fail("Error in swagger/apidoc annotations, unparseable, at "+link+": "+e, e);
            }
        }
    }

//    /* Note in some cases we might have more than one Resource method per 'endpoint'
//     */
//    private int countOperations(ApidocRoot doc) throws Exception {
//        return getOperations(doc).size();
//    }
//
//    private List<DocumentationOperation> getOperations(ApidocRoot doc) throws Exception {
//        List<DocumentationOperation> result = Lists.newArrayList();
//        for (DocumentationEndPoint endpoint : doc.getApis()) {
//            result.addAll(endpoint.getOperations());
//        }
//        return result;
//    }
}

