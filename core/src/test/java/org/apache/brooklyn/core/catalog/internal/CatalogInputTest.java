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
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.util.List;

import org.apache.brooklyn.api.catalog.BrooklynCatalog;
import org.apache.brooklyn.api.catalog.CatalogItem;
import org.apache.brooklyn.api.catalog.CatalogItem.CatalogInput;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.core.mgmt.osgi.OsgiTestResources;
import org.apache.brooklyn.core.test.entity.LocalManagementContextForTests;
import org.apache.brooklyn.entity.stock.BasicEntity;
import org.apache.brooklyn.test.support.TestResourceUnavailableException;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import com.google.common.reflect.TypeToken;

public class CatalogInputTest {
    private ManagementContext mgmt;
    private BrooklynCatalog catalog;
    private String spec;

    @BeforeMethod(alwaysRun=true)
    public void setUp() {
        mgmt = LocalManagementContextForTests.newInstanceWithOsgi();
        catalog = mgmt.getCatalog();
        spec = TestToSpecTransformer.registerSpec(EntitySpec.create(BasicEntity.class));
    }

    @Test
    public void testYamlInputsParsed() {
        CatalogItem<?, ?> item = add(
                "brooklyn.catalog:",
                "  id: test.inputs",
                "  version: 0.0.1",
                "  inputs:",
                "  - simple",
                "  - name: explicit_name",
                "  - name: third_input",
                "    type: integer",
                "  item: " + spec);
        List<CatalogInput<?>> inputs = item.getInputs();
        assertEquals(inputs.size(), 3);
        CatalogInput<?> firstInput = inputs.get(0);
        assertEquals(firstInput.getLabel(), "simple");
        assertEquals(firstInput.isPinned(), true);
        assertEquals(firstInput.getType().getName(), "simple");
        assertEquals(firstInput.getType().getTypeToken(), TypeToken.of(String.class));
        
        CatalogInput<?> secondInput = inputs.get(1);
        assertEquals(secondInput.getLabel(), "explicit_name");
        assertEquals(secondInput.isPinned(), true);
        assertEquals(secondInput.getType().getName(), "explicit_name");
        assertEquals(secondInput.getType().getTypeToken(), TypeToken.of(String.class));
        
        CatalogInput<?> thirdInput = inputs.get(2);
        assertEquals(thirdInput.getLabel(), "third_input");
        assertEquals(thirdInput.isPinned(), true);
        assertEquals(thirdInput.getType().getName(), "third_input");
        assertEquals(thirdInput.getType().getTypeToken(), TypeToken.of(Integer.class));
    }

    @Test
    public void testOsgiType() {
        TestResourceUnavailableException.throwIfResourceUnavailable(getClass(), OsgiTestResources.BROOKLYN_TEST_OSGI_ENTITIES_PATH);

        CatalogItem<?, ?> item = add(
                "brooklyn.catalog:",
                "  id: test.inputs",
                "  version: 0.0.1",
                "  libraries:",
                "  - classpath://" + OsgiTestResources.BROOKLYN_TEST_OSGI_ENTITIES_PATH,
                "  inputs:",
                "  - name: simple",
                "    type: " + OsgiTestResources.BROOKLYN_TEST_OSGI_ENTITIES_SIMPLE_ENTITY,
                "  item: " + spec);
        List<CatalogInput<?>> inputs = item.getInputs();
        assertEquals(inputs.size(), 1);
        CatalogInput<?> firstInput = inputs.get(0);
        assertEquals(firstInput.getLabel(), "simple");
        assertTrue(firstInput.isPinned());
        assertEquals(firstInput.getType().getName(), "simple");
        assertEquals(firstInput.getType().getTypeToken().getRawType().getName(), OsgiTestResources.BROOKLYN_TEST_OSGI_ENTITIES_SIMPLE_ENTITY);
    }

    @Test
    public void testOsgiClassScanned() {
        TestResourceUnavailableException.throwIfResourceUnavailable(getClass(), OsgiTestResources.BROOKLYN_TEST_MORE_ENTITIES_V2_PATH);

        addMulti("brooklyn.catalog:",
            "    items:",
            "    - scanJavaAnnotations: true",
            "      version: 2.0.test_java",
            "      libraries:",
            "      - classpath://" + OsgiTestResources.BROOKLYN_TEST_MORE_ENTITIES_V2_PATH);

        CatalogItem<?, ?> item = CatalogUtils.getCatalogItemOptionalVersion(mgmt, OsgiTestResources.BROOKLYN_TEST_MORE_ENTITIES_MORE_ENTITY);
        assertEquals(item.getVersion(), "2.0.test_java");
        assertEquals(item.getLibraries().size(), 1);
        CatalogInput<?> input = item.getInputs().get(0);
        assertEquals(input.getLabel(), "more_config");
        assertFalse(input.isPinned());
        assertEquals(input.getType().getName(), "more_config");
    }

    private CatalogItem<?,?> add(String... def) {
        return Iterables.getOnlyElement(addMulti(def));
    }

    private Iterable<? extends CatalogItem<?, ?>> addMulti(String... def) {
        return catalog.addItems(Joiner.on('\n').join(def));
    }

}
