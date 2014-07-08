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
package brooklyn.catalog.internal;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;

import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;

import brooklyn.util.ResourceUtils;

public class CatalogLoadTest {

    CatalogXmlSerializer serializer;

    @BeforeMethod
    public void setUp() {
        serializer = new CatalogXmlSerializer();
    }

    private String loadFile(String file) {
        return ResourceUtils.create(this).getResourceAsString(file);
    }

    @Test
    public void testLoadCatalog() {
        CatalogDto catalog = (CatalogDto) serializer.fromString(
                loadFile("classpath://brooklyn/catalog/internal/osgi-catalog.xml"));
        assertNotNull(catalog);
        assertEquals(catalog.name, "OSGi catalogue");
        assertEquals(catalog.entries.size(), 1, "Catalog entries = " + Joiner.on(", ").join(catalog.entries));

        CatalogItemDtoAbstract<?,?> template = Iterables.getOnlyElement(catalog.entries);
        assertEquals(template.getName(), "Entity name");
        assertEquals(template.getVersion(), "9.1.3");
        assertEquals(template.getJavaType(), "com.example.ExampleApp");
        assertEquals(template.getLibraries().getBundles().size(), 2,
                "Template bundles=" + Joiner.on(", ").join(template.getLibraries().getBundles()));
        assertEquals(Sets.newHashSet(template.getLibraries().getBundles()),
                Sets.newHashSet("file://path/to/bundle.jar", "http://www.url.com/for/bundle.jar"));
    }

}
