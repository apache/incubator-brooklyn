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
import static org.testng.Assert.assertTrue;

import java.util.List;

import org.apache.brooklyn.api.catalog.CatalogItem;
import org.apache.brooklyn.api.objs.SpecParameter;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;

public class CatalogItemBuilderTest {

    private String symbolicName = "test";
    private String version = "1.0.0";
    private String javaType = "1.0.0";
    private String name = "My name";
    private String displayName = "My display name";
    private String description = "My long description";
    private String iconUrl = "http://my.icon.url";
    private boolean deprecated = true;
    private boolean disabled = true;
    private String plan = "name: my.yaml.plan";
    private List<CatalogItem.CatalogBundle> libraries = ImmutableList.<CatalogItem.CatalogBundle>of(new CatalogBundleDto(name, version, null));
    private Object tag = new Object();

    @Test(expectedExceptions = NullPointerException.class)
    public void testCannotBuildWithoutName() {
        new CatalogItemBuilder<CatalogEntityItemDto>(new CatalogEntityItemDto())
                .symbolicName(null)
                .build();
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void testCannotBuildWithoutVersion() {
        new CatalogItemBuilder<CatalogEntityItemDto>(new CatalogEntityItemDto())
                .version(null)
                .build();
    }

    @Test
    public void testNewEntityReturnCatalogEntityItemDto() {
        final CatalogItem catalogItem = CatalogItemBuilder.newEntity(symbolicName, version).build();

        assertTrue(catalogItem != null);
    }

    @Test
    public void testNewLocationReturnCatalogLocationItemDto() {
        final CatalogItem catalogItem = CatalogItemBuilder.newLocation(symbolicName, version).build();

        assertTrue(catalogItem != null);
    }

    @Test
    public void testNewPolicyReturnCatalogPolicyItemDto() {
        final CatalogItem catalogItem = CatalogItemBuilder.newPolicy(symbolicName, version).build();

        assertTrue(catalogItem != null);
    }

    @Test
    public void testNewTemplateReturnCatalogTemplateItemDto() {
        final CatalogItem<?, ?> catalogItem = CatalogItemBuilder.newTemplate(symbolicName, version).build();

        assertTrue(catalogItem != null);
    }

    @Test
    public void testEmptyLibrariesIfNotSpecified() {
        final CatalogItem catalogItem = CatalogItemBuilder.newEntity(symbolicName, version).build();

        assertEquals(catalogItem.getLibraries().size(), 0);
    }

    @Test
    public void testNameReplacedByDisplayName() {
        final CatalogEntityItemDto catalogItem = CatalogItemBuilder.newEntity(symbolicName, version)
                .name(name)
                .displayName(displayName)
                .build();

        assertEquals(catalogItem.getName(), displayName);
    }

    @Test
    public void testBuiltEntity() {
        final CatalogEntityItemDto catalogItem = CatalogItemBuilder.newEntity(symbolicName, version)
                .javaType(javaType)
                .displayName(displayName)
                .description(description)
                .iconUrl(iconUrl)
                .deprecated(deprecated)
                .disabled(disabled)
                .plan(plan)
                .libraries(libraries)
                .tag(tag)
                .build();

        assertEquals(catalogItem.getSymbolicName(), symbolicName);
        assertEquals(catalogItem.getVersion(), version);
        assertEquals(catalogItem.getJavaType(), javaType);
        assertEquals(catalogItem.getDisplayName(), displayName);
        assertEquals(catalogItem.getIconUrl(), iconUrl);
        assertEquals(catalogItem.isDeprecated(), deprecated);
        assertEquals(catalogItem.isDisabled(), disabled);
        assertEquals(catalogItem.getPlanYaml(), plan);
        assertEquals(catalogItem.getLibraries(), libraries);
        assertEquals(catalogItem.tags().getTags().size(), 1);
        assertTrue(catalogItem.tags().getTags().contains(tag));
    }
}
