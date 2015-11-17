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
import org.apache.brooklyn.core.entity.AbstractApplication;
import org.apache.brooklyn.core.entity.AbstractEntity;
import org.apache.brooklyn.core.location.AbstractLocation;
import org.apache.brooklyn.core.mgmt.EntityManagementUtils;
import org.apache.brooklyn.core.mgmt.internal.LocalManagementContext;
import org.apache.brooklyn.core.objs.BasicSpecParameter;
import org.apache.brooklyn.core.policy.AbstractPolicy;
import org.apache.brooklyn.core.test.entity.LocalManagementContextForTests;
import org.apache.brooklyn.entity.stock.BasicApplication;
import org.testng.SkipException;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

public class SpecParameterUnwrappingTest extends AbstractYamlTest {
    private static final String SYMBOLIC_NAME = "my.catalog.app.id.load";

    private static final ConfigKey<String> SHARED_CONFIG = ConfigKeys.newStringConfigKey("sample.config");
    public static class ConfigAppForTest extends AbstractApplication {
        public static final ConfigKey<String> SAMPLE_CONFIG = SHARED_CONFIG;
    }
    public static class ConfigEntityForTest extends AbstractEntity {
        public static final ConfigKey<String> SAMPLE_CONFIG = SHARED_CONFIG;
    }
    public static class ConfigPolicyForTest extends AbstractPolicy {
        public static final ConfigKey<String> SAMPLE_CONFIG = SHARED_CONFIG;
    }
    public static class ConfigLocationForTest extends AbstractLocation {
        public static final ConfigKey<String> SAMPLE_CONFIG = SHARED_CONFIG;
    }

    @Override
    protected LocalManagementContext newTestManagementContext() {
        // Don't need OSGi
        return LocalManagementContextForTests.newInstance();
    }

    @DataProvider(name="brooklynTypes")
    public Object[][] brooklynTypes() {
        return new Object[][] {
            {ConfigEntityForTest.class},
            {ConfigPolicyForTest.class},
            {ConfigLocationForTest.class}};
    }

    @Test(dataProvider = "brooklynTypes")
    public void testParameters(Class<? extends BrooklynObject> testClass) {
        addCatalogItems("brooklyn.catalog:",
                        "  id: " + SYMBOLIC_NAME,
                        "  version: " + TEST_VERSION,
                        "  item:",
                        "    type: " + testClass.getName(),
                        "    brooklyn.parameters:",
                        "    - simple");

        ConfigKey<String> SIMPLE_CONFIG = ConfigKeys.newStringConfigKey("simple");
        SpecParameter<String> SIMPLE_PARAM = new BasicSpecParameter<>("simple", true, SIMPLE_CONFIG);
        CatalogItem<?, ?> item = catalog.getCatalogItem(SYMBOLIC_NAME, TEST_VERSION);
        AbstractBrooklynObjectSpec<?,?> spec = createSpec(item);
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
        AbstractBrooklynObjectSpec<?, ?> spec = createSpec(item);
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
        AbstractBrooklynObjectSpec<?,?> spec = createSpec(item);
        List<SpecParameter<?>> inputs = spec.getParameters();
        assertEquals(inputs.size(), 1);
        SpecParameter<?> firstInput = inputs.get(0);
        assertEquals(firstInput.getLabel(), "simple");
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
        AbstractBrooklynObjectSpec<?,?> spec = createSpec(item);
        List<SpecParameter<?>> inputs = spec.getParameters();
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
                // Don't set explicit version, not supported by locations
                "      type: paramItem",
                "      brooklyn.parameters:",
                "      - override");

        CatalogItem<?, ?> item = catalog.getCatalogItem(SYMBOLIC_NAME, TEST_VERSION);
        AbstractBrooklynObjectSpec<?,?> spec = createSpec(item);
        List<SpecParameter<?>> inputs = spec.getParameters();
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

        EntitySpec<? extends Application> spec = createAppSpec(
                "services:",
                "- type: " + ver(SYMBOLIC_NAME));
        List<SpecParameter<?>> params = spec.getParameters();
        assertEquals(params.size(), 1);
        SpecParameter<?> firstInput = params.get(0);
        assertEquals(firstInput.getLabel(), "simple");
    }


    @Test
    public void testAppSpecInheritsCatalogRootParameters() {
        addCatalogItems(
                "brooklyn.catalog:",
                "  version: " + TEST_VERSION,
                "  items:",
                "  - id: " + SYMBOLIC_NAME,
                "    item:",
                "      type: " + BasicApplication.class.getName(),
                "      brooklyn.parameters:",
                "      - simple");

        EntitySpec<? extends Application> spec = createAppSpec(
                "services:",
                "- type: " + ver(SYMBOLIC_NAME));
        List<SpecParameter<?>> params = spec.getParameters();
        assertEquals(params.size(), 1);
        SpecParameter<?> firstInput = params.get(0);
        assertEquals(firstInput.getLabel(), "simple");
    }

    @Test
    public void testAppSpecInheritsCatalogRootParametersWithServices() {
        addCatalogItems(
                "brooklyn.catalog:",
                "  version: " + TEST_VERSION,
                "  items:",
                "  - id: " + SYMBOLIC_NAME,
                "    item:",
                "      brooklyn.parameters:",
                "      - simple",
                "      services:",
                "      - type: " + BasicApplication.class.getName());

        EntitySpec<? extends Application> spec = createAppSpec(
                "services:",
                "- type: " + ver(SYMBOLIC_NAME));
        List<SpecParameter<?>> params = spec.getParameters();
        assertEquals(params.size(), 1);
        SpecParameter<?> firstInput = params.get(0);
        assertEquals(firstInput.getLabel(), "simple");
    }

    @Test
    public void testUnresolvedCatalogItemParameters() {
        // Insert template which is not instantiatable during catalog addition due to
        // missing dependencies, but the spec can be created (the
        // dependencies are already parsed).
        addCatalogItems(
                "brooklyn.catalog:",
                "  version: " + TEST_VERSION,
                "  items:",
                "  - id: " + SYMBOLIC_NAME,
                "    itemType: template",
                "    item:",
                "      services:",
                "      - type: basic-app",
                "  - id: basic-app",
                "    item:",
                "      type: " + ConfigAppForTest.class.getName());
        EntitySpec<? extends Application> spec = createAppSpec(
                "services:",
                "- type: " + ver(SYMBOLIC_NAME));
        List<SpecParameter<?>> params = spec.getParameters();
        assertEquals(params.size(), 2); // sample + defaultDisplayName
        assertEquals(ImmutableSet.copyOf(params), ImmutableSet.copyOf(BasicSpecParameter.fromClass(mgmt(), ConfigAppForTest.class)));
    }

    @Test
    public void testParametersCoercedOnSetAndReferences() throws Exception {
        Integer testValue = Integer.valueOf(55);
        addCatalogItems(
                "brooklyn.catalog:",
                "  id: " + SYMBOLIC_NAME,
                "  version: " + TEST_VERSION,
                "  item:",
                "    type: " + BasicApplication.class.getName(),
                "    brooklyn.parameters:",
                "    - name: num",
                "      type: integer",
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

    @Test
    public void testAppRootParameters() throws Exception {
        EntitySpec<? extends Application> spec = createAppSpec(
                "brooklyn.parameters:",
                "- simple",
                "services:",
                "- type: " + BasicApplication.class.getName());
        List<SpecParameter<?>> inputs = spec.getParameters();
        assertEquals(inputs.size(), 1);
        SpecParameter<?> firstInput = inputs.get(0);
        assertEquals(firstInput.getLabel(), "simple");
    }

    @Test
    public void testAppServiceParameters() throws Exception {
        EntitySpec<? extends Application> spec = createAppSpec(
                "services:",
                "- type: " + BasicApplication.class.getName(),
                "  brooklyn.parameters:",
                "  - simple");
        List<SpecParameter<?>> inputs = spec.getParameters();
        assertEquals(inputs.size(), 1);
        SpecParameter<?> firstInput = inputs.get(0);
        assertEquals(firstInput.getLabel(), "simple");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private AbstractBrooklynObjectSpec<?, ?> createSpec(CatalogItem<?, ?> item) {
        return (AbstractBrooklynObjectSpec<?,?>) catalog.createSpec((CatalogItem)item);
    }

    private EntitySpec<? extends Application> createAppSpec(String... lines) {
        return EntityManagementUtils.createEntitySpecForApplication(mgmt(), joinLines(lines));
    }

}
