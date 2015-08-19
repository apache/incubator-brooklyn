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
import static org.testng.Assert.assertNull;

import java.util.Collection;
import java.util.List;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;
import org.apache.brooklyn.api.catalog.CatalogItem;
import org.apache.brooklyn.api.catalog.CatalogItem.CatalogBundle;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.location.LocationDefinition;
import org.apache.brooklyn.api.location.LocationSpec;
import org.apache.brooklyn.camp.brooklyn.AbstractYamlTest;
import org.apache.brooklyn.core.catalog.CatalogPredicates;
import org.apache.brooklyn.core.config.BasicConfigKey;
import org.apache.brooklyn.core.mgmt.osgi.OsgiStandaloneTest;
import org.apache.brooklyn.location.localhost.LocalhostMachineProvisioningLocation;
import org.apache.brooklyn.test.support.TestResourceUnavailableException;
import org.apache.brooklyn.util.text.StringFunctions;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

public class CatalogYamlLocationTest extends AbstractYamlTest {
    private static final String LOCALHOST_LOCATION_SPEC = "localhost";
    private static final String LOCALHOST_LOCATION_TYPE = LocalhostMachineProvisioningLocation.class.getName();
    private static final String SIMPLE_LOCATION_TYPE = "brooklyn.osgi.tests.SimpleLocation";

    @AfterMethod
    public void tearDown() {
        for (CatalogItem<Location, LocationSpec<?>> ci : mgmt().getCatalog().getCatalogItems(CatalogPredicates.IS_LOCATION)) {
            mgmt().getCatalog().deleteCatalogItem(ci.getSymbolicName(), ci.getVersion());
        }
    }
    
    @Test
    public void testAddCatalogItem() throws Exception {
        assertEquals(countCatalogLocations(), 0);

        String symbolicName = "my.catalog.location.id.load";
        addCatalogLocation(symbolicName, LOCALHOST_LOCATION_TYPE, null);
        assertAdded(symbolicName, LOCALHOST_LOCATION_TYPE);
        removeAndAssert(symbolicName);
    }

    @Test
    public void testAddCatalogItemOsgi() throws Exception {
        assertEquals(countCatalogLocations(), 0);

        String symbolicName = "my.catalog.location.id.load";
        addCatalogLocation(symbolicName, SIMPLE_LOCATION_TYPE, getOsgiLibraries());
        assertAdded(symbolicName, SIMPLE_LOCATION_TYPE);
        assertOsgi(symbolicName);
        removeAndAssert(symbolicName);
    }

    @Test
    public void testAddCatalogItemTopLevelItemSyntax() throws Exception {
        assertEquals(countCatalogLocations(), 0);

        String symbolicName = "my.catalog.location.id.load";
        addCatalogLocationTopLevelItemSyntax(symbolicName, LOCALHOST_LOCATION_TYPE, null);
        assertAdded(symbolicName, LOCALHOST_LOCATION_TYPE);
        removeAndAssert(symbolicName);
    }

    @Test
    public void testAddCatalogItemOsgiTopLevelItemSyntax() throws Exception {
        assertEquals(countCatalogLocations(), 0);

        String symbolicName = "my.catalog.location.id.load";
        addCatalogLocationTopLevelItemSyntax(symbolicName, SIMPLE_LOCATION_TYPE, getOsgiLibraries());
        assertAdded(symbolicName, SIMPLE_LOCATION_TYPE);
        assertOsgi(symbolicName);
        removeAndAssert(symbolicName);
    }

    private void assertOsgi(String symbolicName) {
        CatalogItem<?, ?> item = mgmt().getCatalog().getCatalogItem(symbolicName, TEST_VERSION);
        Collection<CatalogBundle> libs = item.getLibraries();
        assertEquals(libs.size(), 1);
        assertEquals(Iterables.getOnlyElement(libs).getUrl(), Iterables.getOnlyElement(getOsgiLibraries()));
    }

    private void assertAdded(String symbolicName, String expectedJavaType) {
        CatalogItem<?, ?> item = mgmt().getCatalog().getCatalogItem(symbolicName, TEST_VERSION);
        assertEquals(item.getSymbolicName(), symbolicName);
        assertEquals(countCatalogLocations(), 1);

        // Item added to catalog should automatically be available in location registry
        LocationDefinition def = mgmt().getLocationRegistry().getDefinedLocationByName(symbolicName);
        assertEquals(def.getId(), symbolicName);
        assertEquals(def.getName(), symbolicName);
        
        LocationSpec<?> spec = (LocationSpec<?>)mgmt().getCatalog().createSpec(item);
        assertEquals(spec.getType().getName(), expectedJavaType);
    }
    
    private void removeAndAssert(String symbolicName) {
        // Deleting item: should be gone from catalog, and from location registry
        deleteCatalogEntity(symbolicName);

        assertEquals(countCatalogLocations(), 0);
        assertNull(mgmt().getLocationRegistry().getDefinedLocationByName(symbolicName));
    }

    @Test
    public void testLaunchApplicationReferencingLocationClass() throws Exception {
        String symbolicName = "my.catalog.location.id.launch";
        addCatalogLocation(symbolicName, LOCALHOST_LOCATION_TYPE, null);
        runLaunchApplicationReferencingLocation(symbolicName, LOCALHOST_LOCATION_TYPE);

        deleteCatalogEntity(symbolicName);
    }

    @Test
    public void testLaunchApplicationReferencingLocationSpec() throws Exception {
        String symbolicName = "my.catalog.location.id.launch";
        addCatalogLocation(symbolicName, LOCALHOST_LOCATION_SPEC, null);
        runLaunchApplicationReferencingLocation(symbolicName, LOCALHOST_LOCATION_TYPE);

        deleteCatalogEntity(symbolicName);
    }

    @Test
    public void testLaunchApplicationReferencingLocationClassTopLevelItemSyntax() throws Exception {
        String symbolicName = "my.catalog.location.id.launch";
        addCatalogLocationTopLevelItemSyntax(symbolicName, LOCALHOST_LOCATION_TYPE, null);
        runLaunchApplicationReferencingLocation(symbolicName, LOCALHOST_LOCATION_TYPE);

        deleteCatalogEntity(symbolicName);
    }

    @Test
    public void testLaunchApplicationReferencingLocationSpecTopLevelSyntax() throws Exception {
        String symbolicName = "my.catalog.location.id.launch";
        addCatalogLocationTopLevelItemSyntax(symbolicName, LOCALHOST_LOCATION_SPEC, null);
        runLaunchApplicationReferencingLocation(symbolicName, LOCALHOST_LOCATION_TYPE);

        deleteCatalogEntity(symbolicName);
    }

    @Test
    public void testLaunchApplicationReferencingOsgiLocation() throws Exception {
        String symbolicName = "my.catalog.location.id.launch";
        addCatalogLocation(symbolicName, SIMPLE_LOCATION_TYPE, getOsgiLibraries());
        runLaunchApplicationReferencingLocation(symbolicName, SIMPLE_LOCATION_TYPE);
        
        deleteCatalogEntity(symbolicName);
    }
    
    protected void runLaunchApplicationReferencingLocation(String locTypeInYaml, String locType) throws Exception {
        Entity app = createAndStartApplication(
            "name: simple-app-yaml",
            "location: ",
            "  "+locTypeInYaml+":",
            "    config2: config2 override",
            "    config3: config3",
            "services: ",
            "  - type: org.apache.brooklyn.entity.stock.BasicStartable");

        Entity simpleEntity = Iterables.getOnlyElement(app.getChildren());
        Location location = Iterables.getOnlyElement(simpleEntity.getLocations());
        assertEquals(location.getClass().getName(), locType);
        assertEquals(location.getConfig(new BasicConfigKey<String>(String.class, "config1")), "config1");
        assertEquals(location.getConfig(new BasicConfigKey<String>(String.class, "config2")), "config2 override");
        assertEquals(location.getConfig(new BasicConfigKey<String>(String.class, "config3")), "config3");
    }

    private List<String> getOsgiLibraries() {
        TestResourceUnavailableException.throwIfResourceUnavailable(getClass(), OsgiStandaloneTest.BROOKLYN_TEST_OSGI_ENTITIES_PATH);
        return ImmutableList.of(OsgiStandaloneTest.BROOKLYN_TEST_OSGI_ENTITIES_URL);
    }
    
    private void addCatalogLocation(String symbolicName, String locationType, List<String> libraries) {
        ImmutableList.Builder<String> yaml = ImmutableList.<String>builder().add(
                "brooklyn.catalog:",
                "  id: " + symbolicName,
                "  name: My Catalog Location",
                "  description: My description",
                "  version: " + TEST_VERSION);
        if (libraries!=null && libraries.size() > 0) {
            yaml.add("  libraries:")
                .addAll(Lists.transform(libraries, StringFunctions.prepend("  - url: ")));
        }
        yaml.add(
                "  item.type: location",
                "  item:",
                "    type: " + locationType,
                "    brooklyn.config:",
                "      config1: config1",
                "      config2: config2");
        
        
        addCatalogItems(yaml.build());
    }

    private void addCatalogLocationTopLevelItemSyntax(String symbolicName, String locationType, List<String> libraries) {
        ImmutableList.Builder<String> yaml = ImmutableList.<String>builder().add(
                "brooklyn.catalog:",
                "  id: " + symbolicName,
                "  name: My Catalog Location",
                "  description: My description",
                "  version: " + TEST_VERSION);
        if (libraries!=null && libraries.size() > 0) {
            yaml.add("  libraries:")
                .addAll(Lists.transform(libraries, StringFunctions.prepend("  - url: ")));
        }
        yaml.add(
                "",
                "brooklyn.locations:",
                "- type: " + locationType,
                "  brooklyn.config:",
                "    config1: config1",
                "    config2: config2");
        
        
        addCatalogItems(yaml.build());
    }

    private int countCatalogLocations() {
        return Iterables.size(mgmt().getCatalog().getCatalogItems(CatalogPredicates.IS_LOCATION));
    }
}
