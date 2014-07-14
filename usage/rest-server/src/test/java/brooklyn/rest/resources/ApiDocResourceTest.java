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

import static org.testng.Assert.assertEquals;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

import brooklyn.rest.BrooklynRestApi;
import brooklyn.rest.testing.BrooklynRestResourceTest;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.wordnik.swagger.core.Documentation;
import com.wordnik.swagger.core.DocumentationEndPoint;
import com.wordnik.swagger.core.DocumentationOperation;

/**
 * @author Adam Lowe
 */
@Test(singleThreaded = true)
public class ApiDocResourceTest extends BrooklynRestResourceTest {

    private static final Logger log = LoggerFactory.getLogger(ApiDocResourceTest.class);

    @Override
    protected void setUpResources() throws Exception {
        super.setUpResources();
        for (Object o : BrooklynRestApi.getApidocResources()) {
            addResource(o);
        }
    }

    @Test
    public void testCountRestResources() throws Exception {
        Documentation response = client().resource("/v1/apidoc/").get(Documentation.class);
        assertEquals(response.getApis().size(), 1 + Iterables.size(BrooklynRestApi.getBrooklynRestResources()));
    }

    @Test
    public void testApiDocDetails() throws Exception {
        Documentation response = client().resource("/v1/apidoc/brooklyn.rest.resources.ApidocResource").get(Documentation.class);
        assertEquals(countOperations(response), 2);
    }

    @Test
    public void testEffectorDetails() throws Exception {
        Documentation response = client().resource("/v1/apidoc/brooklyn.rest.resources.EffectorResource").get(Documentation.class);
        assertEquals(countOperations(response), 2);
    }

    @Test
    public void testEntityDetails() throws Exception {
        Documentation response = client().resource("/v1/apidoc/brooklyn.rest.resources.EntityResource").get(Documentation.class);
        assertEquals(countOperations(response), 11);
    }

    @Test
    public void testCatalogDetails() throws Exception {
        Documentation response = client().resource("/v1/apidoc/brooklyn.rest.resources.CatalogResource").get(Documentation.class);
        assertEquals(countOperations(response), 11, "ops="+getOperations(response));
    }

    @SuppressWarnings("rawtypes")
    @Test
    public void testAllAreLoadable() throws Exception {
        // sometimes -- e.g. if an annotation refers to a class name with the wrong case -- the call returns a 500 and breaks apidoc; ensure we don't trigger that.  
        Map response = client().resource("/v1/apidoc/").get(Map.class);
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

    /* Note in some cases we might have more than one Resource method per 'endpoint'
     */
    private int countOperations(Documentation doc) throws Exception {
        return getOperations(doc).size();
    }
    
    private List<DocumentationOperation> getOperations(Documentation doc) throws Exception {
        List<DocumentationOperation> result = Lists.newArrayList();
        for (DocumentationEndPoint endpoint : doc.getApis()) {
            result.addAll(endpoint.getOperations());
        }
        return result;
    }
}

