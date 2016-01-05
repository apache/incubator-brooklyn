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
package org.apache.brooklyn.core.catalog.internal;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.apache.brooklyn.api.catalog.CatalogItem.CatalogBundle;
import org.apache.brooklyn.core.catalog.internal.CatalogDto;
import org.apache.brooklyn.core.catalog.internal.CatalogItemDtoAbstract;
import org.apache.brooklyn.core.catalog.internal.CatalogXmlSerializer;
import org.apache.brooklyn.util.core.ResourceUtils;

import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;

public class CatalogLoadTest {

    CatalogXmlSerializer serializer;

    @BeforeMethod
    public void setUp() {
        serializer = new CatalogXmlSerializer();
    }

    private String loadFile(String file) {
        return ResourceUtils.create(this).getResourceAsString(file);
    }

    // CAMP YAML parsing not available in core, so YAML catalog tests are in camp, e.g. CatalogYamlEntityTest 
    
    @Test
    public void testLoadXmlCatalog() {
        CatalogDto catalog = (CatalogDto) serializer.fromString(
                loadFile("classpath://brooklyn/catalog/internal/osgi-catalog.xml"));
        assertNotNull(catalog);
        assertEquals(catalog.name, "OSGi catalogue");
        assertEquals(Iterables.size(catalog.getUniqueEntries()), 1, "Catalog entries = " + Joiner.on(", ").join(catalog.getUniqueEntries()));

        CatalogItemDtoAbstract<?,?> template = Iterables.getOnlyElement(catalog.getUniqueEntries());
        assertEquals(template.getDisplayName(), "Entity name");
        assertEquals(template.getVersion(), "9.1.3");
        assertEquals(template.getJavaType(), "com.example.ExampleApp");
        assertEquals(template.getLibraries().size(), 2,
                "Template bundles=" + Joiner.on(", ").join(template.getLibraries()));
        
        boolean foundBundle1 = false, foundBundle2 = false;
        for (CatalogBundle bundle : template.getLibraries()) {
            if (bundle.getUrl().equals("file://path/to/bundle.jar")) {
                foundBundle1 = true;
            }
            if (bundle.getUrl().equals("http://www.url.com/for/bundle.jar")) {
                foundBundle2 = true;
            }
        }
        assertTrue(foundBundle1);
        assertTrue(foundBundle2);
    }

}
