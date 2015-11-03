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

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;

import java.util.Iterator;
import java.util.List;

import org.apache.brooklyn.api.catalog.CatalogItem;
import org.apache.brooklyn.api.entity.Application;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.internal.AbstractBrooklynObjectSpec;
import org.apache.brooklyn.api.objs.BrooklynObject;
import org.apache.brooklyn.api.objs.SpecParameter;
import org.apache.brooklyn.camp.brooklyn.AbstractYamlTest;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.entity.AbstractEntity;
import org.apache.brooklyn.core.location.AbstractLocation;
import org.apache.brooklyn.core.mgmt.EntityManagementUtils;
import org.apache.brooklyn.core.objs.BasicSpecParameter;
import org.apache.brooklyn.core.policy.AbstractPolicy;
import org.apache.brooklyn.entity.stock.BasicApplication;
import org.testng.SkipException;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

public class CatalogParametersTest extends AbstractYamlTest {
    private static final String SYMBOLIC_NAME = "my.catalog.app.id.load";

    private static final ConfigKey<String> SHARED_CONFIG = ConfigKeys.newStringConfigKey("sample.config");
    public static class ConfigEntityForTest extends AbstractEntity {
        public static final ConfigKey<String> SAMPLE_CONFIG = SHARED_CONFIG;
    }
    public static class ConfigPolicyForTest extends AbstractPolicy {
        public static final ConfigKey<String> SAMPLE_CONFIG = SHARED_CONFIG;
    }
    public static class ConfigLocationForTest extends AbstractLocation {
        private static final long serialVersionUID = 1L;
        public static final ConfigKey<String> SAMPLE_CONFIG = SHARED_CONFIG;
    }

    @DataProvider(name="brooklynTypes")
    public Object[][] brooklynTypes() {
        return new Object[][] {
            {ConfigEntityForTest.class},
            {ConfigPolicyForTest.class},
            {ConfigLocationForTest.class}};
    }
    
    @DataProvider(name="catalogTemplates")
    public Object[][] catalogTemplates() {
        return new Object[][] {
            {joinLines(
                    "brooklyn.catalog:",
                    "  id: " + SYMBOLIC_NAME,
                    "  version: " + TEST_VERSION,
                    "  item:",
                    "    type: ${testClass}",
                    "    brooklyn.parameters:",
                    "    - simple")},
            {joinLines(
                    "brooklyn.catalog:",
                    "  id: " + SYMBOLIC_NAME,
                    "  version: " + TEST_VERSION,
                    "  brooklyn.parameters:",
                    "  - simple",
                    "  item:",
                    "    type: ${testClass}")}
        };
    }
    
    @DataProvider(name="typesAndTemplates")
    public Object[][] typesAndTemplates() {
        // cartesian product of brooklynTypes X catalogTemplates
        Object[][] brooklynTypes = brooklynTypes();
        Object[][] catalogTemplates = catalogTemplates();
        Object[][] arr = new Object[brooklynTypes.length * catalogTemplates.length][];
        for (int i = 0; i < catalogTemplates.length; i++) {
            for (int j = 0; j < brooklynTypes.length; j++) {
                Object[] item = new Object[2];
                item[0] = brooklynTypes[j][0];
                item[1] = catalogTemplates[i][0];
                arr[i*brooklynTypes.length + j] = item;
            }
        }
        return arr;
    }

    @Test(dataProvider = "typesAndTemplates")
    public void testParameters(Class<? extends BrooklynObject> testClass, String template) {
        addCatalogItems(template.replace("${testClass}", testClass.getName()));

        ConfigKey<String> SIMPLE_CONFIG = ConfigKeys.newStringConfigKey("simple");
        SpecParameter<String> SIMPLE_PARAM = new BasicSpecParameter<>("simple", true, SIMPLE_CONFIG);
        CatalogItem<?, ?> item = catalog.getCatalogItem(SYMBOLIC_NAME, TEST_VERSION);
        assertEquals(item.getParameters(), ImmutableList.of(SIMPLE_PARAM));
        @SuppressWarnings({"unchecked", "rawtypes"})
        AbstractBrooklynObjectSpec<?,?> spec = catalog.createSpec((CatalogItem)item);
        assertEquals(ImmutableSet.copyOf(spec.getParameters()), ImmutableList.of(SIMPLE_PARAM));
    }

    @Test(dataProvider = "brooklynTypes")
    public void testDefaultParameters(Class<? extends BrooklynObject> testClass) {
        addCatalogItems(
            "brooklyn.catalog:",
            "  id: " + SYMBOLIC_NAME,
            "  version: " + TEST_VERSION,
            "  item:",
            "    type: "+ testClass.getName());

        CatalogItem<?, ?> item = catalog.getCatalogItem(SYMBOLIC_NAME, TEST_VERSION);
        assertEquals(ImmutableSet.copyOf(item.getParameters()), ImmutableSet.copyOf(BasicSpecParameter.fromClass(mgmt(), testClass)));
        @SuppressWarnings({"unchecked", "rawtypes"})
        AbstractBrooklynObjectSpec<?,?> spec = (AbstractBrooklynObjectSpec<?,?>) catalog.createSpec((CatalogItem)item);
        assertEquals(ImmutableSet.copyOf(spec.getParameters()), ImmutableSet.copyOf(BasicSpecParameter.fromClass(mgmt(),testClass)));
    }


    @Test
    public void testRootParametersUnwrapped() {
        addCatalogItems(
                "brooklyn.catalog:",
                "  id: " + SYMBOLIC_NAME,
                "  version: " + TEST_VERSION,
                "  item:",
                "    services:",
                "    - type: " + ConfigEntityForTest.class.getName(),
                "    brooklyn.parameters:",
                "    - simple");

        CatalogItem<?, ?> item = catalog.getCatalogItem(SYMBOLIC_NAME, TEST_VERSION);
        List<SpecParameter<?>> inputs = item.getParameters();
        assertEquals(inputs.size(), 1);
        SpecParameter<?> firstInput = inputs.get(0);
        assertEquals(firstInput.getLabel(), "simple");
    }

    @Test
    public void testExplicitParametersInMetaOverride() {
        addCatalogItems(
                "brooklyn.catalog:",
                "  id: " + SYMBOLIC_NAME,
                "  version: " + TEST_VERSION,
                "  brooklyn.parameters:",
                "    - metaSimple",
                "  item:",
                "    type: " + ConfigEntityForTest.class.getName(),
                "    brooklyn.parameters:",
                "    - simple");

        CatalogItem<?, ?> item = catalog.getCatalogItem(SYMBOLIC_NAME, TEST_VERSION);
        List<SpecParameter<?>> inputs = item.getParameters();
        assertEquals(inputs.size(), 1);
        SpecParameter<?> firstInput = inputs.get(0);
        assertEquals(firstInput.getLabel(), "metaSimple");
    }

    @Test(dataProvider="brooklynTypes")
    public void testDepentantCatalogsInheritParameters(Class<? extends BrooklynObject> type) {
        if (type == ConfigLocationForTest.class) {
            //TODO
            throw new SkipException("Locations don't inherit parameters, should migrate to the type registry first");
        }
        addCatalogItems(
                "brooklyn.catalog:",
                "  version: " + TEST_VERSION,
                "  items:",
                "  - id: paramItem",
                "    item:",
                "      type: " + type.getName(),
                "      brooklyn.parameters:",
                "      - simple",
                "  - id: " + SYMBOLIC_NAME,
                "    item:",
                "      type: paramItem:" + TEST_VERSION);

        CatalogItem<?, ?> item = catalog.getCatalogItem(SYMBOLIC_NAME, TEST_VERSION);
        List<SpecParameter<?>> inputs = item.getParameters();
        assertEquals(inputs.size(), 1);
        SpecParameter<?> firstInput = inputs.get(0);
        assertEquals(firstInput.getLabel(), "simple");
    }

    @Test(dataProvider="brooklynTypes")
    public void testDepentantCatalogsOverrideParameters(Class<? extends BrooklynObject> type) {
        addCatalogItems(
                "brooklyn.catalog:",
                "  version: " + TEST_VERSION,
                "  items:",
                "  - id: paramItem",
                "    item:",
                "      type: " + type.getName(),
                "      brooklyn.parameters:",
                "      - simple",
                "  - id: " + SYMBOLIC_NAME,
                "    item:",
                "      type: paramItem:" + TEST_VERSION,
                "      brooklyn.parameters:",
                "      - override");

        CatalogItem<?, ?> item = catalog.getCatalogItem(SYMBOLIC_NAME, TEST_VERSION);
        List<SpecParameter<?>> inputs = item.getParameters();
        assertEquals(inputs.size(), 1);
        SpecParameter<?> firstInput = inputs.get(0);
        assertEquals(firstInput.getLabel(), "override");
    }

    @Test
    public void testChildEntitiyHasParameters() {
        addCatalogItems(
                "brooklyn.catalog:",
                "  id: " + SYMBOLIC_NAME,
                "  version: " + TEST_VERSION,
                "  items:",
                "  - item:",
                "      type: " + ConfigEntityForTest.class.getName(),
                "      brooklyn.children:",
                "      - type: " + ConfigEntityForTest.class.getName(),
                "        brooklyn.parameters:",
                "        - simple");

        CatalogItem<?, ?> item = catalog.getCatalogItem(SYMBOLIC_NAME, TEST_VERSION);
        @SuppressWarnings({ "rawtypes", "unchecked"})
        EntitySpec<?> parentSpec = (EntitySpec<?>) catalog.createSpec((CatalogItem)item);
        EntitySpec<?> spec = parentSpec.getChildren().get(0);
        SpecParameter<?> firstInput = spec.getParameters().get(0);
        assertEquals(firstInput.getLabel(), "simple");
    }

    @Test
    public void testAppSpecInheritsCatalogParameters() {
        addCatalogItems(
                "brooklyn.catalog:",
                "  version: " + TEST_VERSION,
                "  items:",
                "  - id: " + SYMBOLIC_NAME,
                "    item:",
                "      type: " + BasicApplication.class.getName(),
                "      brooklyn.parameters:",
                "      - simple");

        EntitySpec<? extends Application> spec = EntityManagementUtils.createEntitySpecForApplication(mgmt(), joinLines(
                "services:",
                "- type: " + ver(SYMBOLIC_NAME)));
        List<SpecParameter<?>> params = spec.getParameters();
        assertEquals(params.size(), 1);
        SpecParameter<?> firstInput = params.get(0);
        assertEquals(firstInput.getLabel(), "simple");
    }

    @Test
    public void testParametersCoercedOnSetAndReferences() throws Exception {
        Integer testValue = Integer.valueOf(55);
        addCatalogItems(
                "brooklyn.catalog:",
                "  id: " + SYMBOLIC_NAME,
                "  version: " + TEST_VERSION,
                "  brooklyn.parameters:",
                "  - name: num",
                "    type: integer",
                "  item:",
                "    type: " + BasicApplication.class.getName(),
                "    brooklyn.children:",
                "    - type: " + ConfigEntityForTest.class.getName(),
                "      brooklyn.config:",
                "        refConfig: $brooklyn:scopeRoot().config(\"num\")",
                "    - type: " + ConfigEntityForTest.class.getName(),
                "      brooklyn.config:",
                "        refConfig: $brooklyn:config(\"num\")"); //inherited config

        Entity app = createAndStartApplication(
                "services:",
                "- type: " + BasicApplication.class.getName(),
                "  brooklyn.children:",
                "  - type: " + ver(SYMBOLIC_NAME),
                "    brooklyn.config:",
                "      num: \"" + testValue + "\"");

        Entity scopeRoot = Iterables.getOnlyElement(app.getChildren());

        ConfigKey<Object> numKey = ConfigKeys.newConfigKey(Object.class, "num");
        assertEquals(scopeRoot.config().get(numKey), testValue);

        ConfigKey<Object> refConfigKey = ConfigKeys.newConfigKey(Object.class, "refConfig");

        Iterator<Entity> childIter = scopeRoot.getChildren().iterator();
        Entity c1 = childIter.next();
        assertEquals(c1.config().get(refConfigKey), testValue);
        Entity c2 = childIter.next();
        assertEquals(c2.config().get(refConfigKey), testValue);
        assertFalse(childIter.hasNext());
    }

}
