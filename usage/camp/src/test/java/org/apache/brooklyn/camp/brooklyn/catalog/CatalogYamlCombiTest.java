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
package org.apache.brooklyn.camp.brooklyn.catalog;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.policy.Policy;
import org.apache.brooklyn.api.typereg.RegisteredType;
import org.apache.brooklyn.camp.brooklyn.AbstractYamlTest;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.entity.stock.BasicEntity;
import org.apache.brooklyn.entity.stock.BasicStartable;
import org.apache.brooklyn.policy.ha.ServiceRestarter;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.collect.Iterables;


public class CatalogYamlCombiTest extends AbstractYamlTest {

    private static final Logger log = LoggerFactory.getLogger(CatalogYamlCombiTest.class);
    
    @Test
    public void testBRefEntityA() throws Exception {
        addCatalogItems(
            "brooklyn.catalog:",
            "  version: "+TEST_VERSION,
            "  items:",
            
            "  - itemType: entity",
            "    item:",
            // TODO inclusion of the above information gives a better error message when no transformers
//            "  - item:",
            
            "      id: A",
            "      type: "+BasicEntity.class.getName(),
            "      brooklyn.config: { a: 1, b: 0 }",
            "  - item:",
            "      id: B",
            "      type: A",
            "      brooklyn.config: { b: 1 }");

        RegisteredType item = mgmt().getTypeRegistry().get("B", TEST_VERSION, null, null);
        Assert.assertNotNull(item);

        Entity a = launchEntity("A");
        Assert.assertTrue(BasicEntity.class.isInstance(a), "Wrong type: "+a);
        Assert.assertEquals(a.config().get(ConfigKeys.newIntegerConfigKey("a")), (Integer)1);
        Assert.assertEquals(a.config().get(ConfigKeys.newIntegerConfigKey("b")), (Integer)0);

        Entity b = launchEntity("B");
        Assert.assertTrue(BasicEntity.class.isInstance(b), "Wrong type: "+b);
        Assert.assertEquals(b.config().get(ConfigKeys.newIntegerConfigKey("a")), (Integer)1);
        Assert.assertEquals(b.config().get(ConfigKeys.newIntegerConfigKey("b")), (Integer)1);

        deleteCatalogEntity("A");
        
        // now loading B makes an error
        try {
            launchEntity("B");
            Assert.fail("B should not be launchable");
        } catch (Exception e) {
            Exceptions.propagateIfFatal(e);
            log.info("Got expected error: "+e);
        }
        
        deleteCatalogEntity("B");
    }

    @Test
    public void testBRefPolicyALocationZ() throws Exception {
        addCatalogItems(
            "brooklyn.catalog:",
            "  version: "+TEST_VERSION,
            "  id: Z",
            "  items:",
            "  - item: ",
            "      type: localhost",
            "      brooklyn.config: { z: 9 }");
        addCatalogItems(
            "brooklyn.catalog:",
            "  version: "+TEST_VERSION,
            "  items:",
            "  - item_type: policy", 
            "    item:",
            "      id: A",
            "      type: "+ServiceRestarter.class.getName(),
            "      brooklyn.config: { a: 99 }",
            "  - item:",
            "      id: B",
            "      type: "+BasicStartable.class.getName(),
            "      location: Z",
            "      brooklyn.policies:",
            "      - type: A");

        RegisteredType item = mgmt().getTypeRegistry().get("A", TEST_VERSION, null, null);
        Assert.assertNotNull(item);

        Entity b = launchEntity("B", false);
        Assert.assertTrue(BasicStartable.class.isInstance(b), "Wrong type: "+b);
        Entities.dumpInfo(b);
        
        Assert.assertEquals(Iterables.getOnlyElement(b.getLocations()).getConfig(ConfigKeys.newIntegerConfigKey("z")), (Integer)9);
        
        Policy p = Iterables.getOnlyElement(b.getPolicies());
        Assert.assertTrue(ServiceRestarter.class.isInstance(p), "Wrong type: "+p);
        Assert.assertEquals(p.getConfig(ConfigKeys.newIntegerConfigKey("a")), (Integer)99);
        
        deleteCatalogEntity("A");
        deleteCatalogEntity("B");
        deleteCatalogEntity("Z");
    }

    private Entity launchEntity(String symbolicName) throws Exception {
        return launchEntity(symbolicName, true);
    }
    
    private Entity launchEntity(String symbolicName, boolean includeLocation) throws Exception {
        String yaml = "name: simple-app-yaml\n" +
                      (includeLocation ? "location: localhost\n" : "") +
                      "services: \n" +
                      "  - type: "+ver(symbolicName);
        Entity app = createAndStartApplication(yaml);
        return Iterables.getOnlyElement(app.getChildren());
    }


}
