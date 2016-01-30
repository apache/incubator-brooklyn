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
import static org.testng.Assert.assertTrue;

import java.util.List;

import org.apache.brooklyn.api.catalog.CatalogItem;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.internal.AbstractBrooklynObjectSpec;
import org.apache.brooklyn.api.objs.SpecParameter;
import org.apache.brooklyn.api.typereg.RegisteredType;
import org.apache.brooklyn.camp.brooklyn.AbstractYamlTest;
import org.apache.brooklyn.entity.stock.BasicApplication;
import org.apache.brooklyn.test.support.TestResourceUnavailableException;
import org.apache.brooklyn.util.osgi.OsgiTestResources;
import org.testng.Assert;
import org.testng.annotations.Test;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.Iterables;
import com.google.common.reflect.TypeToken;

public class SpecParameterParsingTest  extends AbstractYamlTest {
    
    @Test
    public void testYamlInputsParsed() {
        String itemId = add(
                "brooklyn.catalog:",
                "  id: test.inputs",
                "  version: 0.0.1",
                "  item: ",
                "    type: "+ BasicApplication.class.getName(),
                "    brooklyn.parameters:",
                "    - simple",
                "    - name: explicit_name",
                "    - name: third_input",
                "      type: integer");
        EntitySpec<?> item = mgmt().getTypeRegistry().createSpec(mgmt().getTypeRegistry().get(itemId), null, EntitySpec.class);
        List<SpecParameter<?>> inputs = item.getParameters();
        assertEquals(inputs.size(), 3);
        SpecParameter<?> firstInput = inputs.get(0);
        assertEquals(firstInput.getLabel(), "simple");
        assertEquals(firstInput.isPinned(), true);
        assertEquals(firstInput.getConfigKey().getName(), "simple");
        assertEquals(firstInput.getConfigKey().getTypeToken(), TypeToken.of(String.class));
        
        SpecParameter<?> secondInput = inputs.get(1);
        assertEquals(secondInput.getLabel(), "explicit_name");
        assertEquals(secondInput.isPinned(), true);
        assertEquals(secondInput.getConfigKey().getName(), "explicit_name");
        assertEquals(secondInput.getConfigKey().getTypeToken(), TypeToken.of(String.class));
        
        SpecParameter<?> thirdInput = inputs.get(2);
        assertEquals(thirdInput.getLabel(), "third_input");
        assertEquals(thirdInput.isPinned(), true);
        assertEquals(thirdInput.getConfigKey().getName(), "third_input");
        assertEquals(thirdInput.getConfigKey().getTypeToken(), TypeToken.of(Integer.class));
    }

    @Test
    public void testOsgiType() {
        TestResourceUnavailableException.throwIfResourceUnavailable(getClass(), OsgiTestResources.BROOKLYN_TEST_OSGI_ENTITIES_PATH);

        String itemId = add(
                "brooklyn.catalog:",
                "  id: test.inputs",
                "  version: 0.0.1",
                "  libraries:",
                "  - classpath://" + OsgiTestResources.BROOKLYN_TEST_OSGI_ENTITIES_PATH,
                "  item: ",
                "    type: "+ BasicApplication.class.getName(),
                "    brooklyn.parameters:",
                "    - name: simple",
                "      type: " + OsgiTestResources.BROOKLYN_TEST_OSGI_ENTITIES_SIMPLE_ENTITY);
        AbstractBrooklynObjectSpec<?,?> spec = createSpec(itemId);
        List<SpecParameter<?>> inputs = spec.getParameters();
        assertEquals(inputs.size(), 1);
        SpecParameter<?> firstInput = inputs.get(0);
        assertEquals(firstInput.getLabel(), "simple");
        assertTrue(firstInput.isPinned());
        assertEquals(firstInput.getConfigKey().getName(), "simple");
        assertEquals(firstInput.getConfigKey().getTypeToken().getRawType().getName(), OsgiTestResources.BROOKLYN_TEST_OSGI_ENTITIES_SIMPLE_ENTITY);
    }

    @Test
    public void testOsgiClassScanned() {
        TestResourceUnavailableException.throwIfResourceUnavailable(getClass(), OsgiTestResources.BROOKLYN_TEST_OSGI_ENTITIES_PATH);
        TestResourceUnavailableException.throwIfResourceUnavailable(getClass(), OsgiTestResources.BROOKLYN_TEST_MORE_ENTITIES_V2_PATH);

        addMulti("brooklyn.catalog:",
            "    items:",
            "    - scanJavaAnnotations: true",
            "      version: 2.0.test_java",
            "      libraries:",
            "      - classpath://" + OsgiTestResources.BROOKLYN_TEST_OSGI_ENTITIES_PATH,
            "      - classpath://" + OsgiTestResources.BROOKLYN_TEST_MORE_ENTITIES_V2_PATH);

        RegisteredType item = mgmt().getTypeRegistry().get(OsgiTestResources.BROOKLYN_TEST_MORE_ENTITIES_MORE_ENTITY);
        assertEquals(item.getVersion(), "2.0.test_java");
        assertEquals(item.getLibraries().size(), 2);
        AbstractBrooklynObjectSpec<?,?> spec = createSpec(item);
        List<SpecParameter<?>> inputs = spec.getParameters();
        if (inputs.isEmpty()) Assert.fail("no inputs (if you're in the IDE, mvn clean install may need to be run to rebuild osgi test JARs)");
        assertEquals(inputs.size(), 1);
        SpecParameter<?> input = inputs.get(0);
        assertEquals(input.getLabel(), "more_config");
        assertFalse(input.isPinned());
        assertEquals(input.getConfigKey().getName(), "more_config");
    }

    private String add(String... def) {
        return Iterables.getOnlyElement(addMulti(def));
    }

    private Iterable<String> addMulti(String... def) {
        return Iterables.transform(catalog.addItems(Joiner.on('\n').join(def)),
            new Function<CatalogItem<?,?>, String>() {
                @Override
                public String apply(CatalogItem<?, ?> input) {
                    return input.getId();
                }
            });
    }

    private AbstractBrooklynObjectSpec<?, ?> createSpec(String itemId) {
        RegisteredType item = mgmt().getTypeRegistry().get(itemId);
        Assert.assertNotNull(item, "Could not load: "+itemId);
        return createSpec(item);
    }
    
    private AbstractBrooklynObjectSpec<?, ?> createSpec(RegisteredType item) {
        return mgmt().getTypeRegistry().createSpec(item, null, EntitySpec.class);
    }

}
