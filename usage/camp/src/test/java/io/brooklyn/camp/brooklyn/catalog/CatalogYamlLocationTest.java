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
package io.brooklyn.camp.brooklyn.catalog;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNull;
import io.brooklyn.camp.brooklyn.AbstractYamlTest;

import java.util.List;

import org.testng.annotations.Test;

import brooklyn.catalog.CatalogItem;
import brooklyn.catalog.CatalogPredicates;
import brooklyn.entity.Entity;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.location.Location;
import brooklyn.location.LocationDefinition;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation;
import brooklyn.management.osgi.OsgiStandaloneTest;
import brooklyn.test.TestResourceUnavailableException;
import brooklyn.util.text.StringFunctions;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

public class CatalogYamlLocationTest extends AbstractYamlTest {
    private static final String LOCALHOST_LOCATION_SPEC = "localhost";
    private static final String LOCALHOST_LOCATION_TYPE = LocalhostMachineProvisioningLocation.class.getName();
    private static final String SIMPLE_LOCATION_TYPE = "brooklyn.osgi.tests.SimpleLocation";

    @Test
    public void testAddCatalogItem() throws Exception {
        assertEquals(countCatalogLocations(), 0);

        String symbolicName = "my.catalog.location.id.load";
        addCatalogOSGiLocation(symbolicName, SIMPLE_LOCATION_TYPE);

        CatalogItem<?, ?> item = mgmt().getCatalog().getCatalogItem(symbolicName, TEST_VERSION);
        assertEquals(item.getSymbolicName(), symbolicName);
        assertEquals(countCatalogLocations(), 1);

        // Item added to catalog should automatically be available in location registry
        LocationDefinition def = mgmt().getLocationRegistry().getDefinedLocationByName(symbolicName);
        assertEquals(def.getId(), symbolicName);
        assertEquals(def.getName(), symbolicName);

        // Deleting item: should be gone from catalog, and from location registry
        deleteCatalogEntity(symbolicName);

        assertEquals(countCatalogLocations(), 0);
        assertNull(mgmt().getLocationRegistry().getDefinedLocationByName(symbolicName));
    }

    @Test
    public void testLaunchApplicationReferencingLocationClass() throws Exception {
        String symbolicName = "my.catalog.location.id.launch";
        addCatalogLocation(symbolicName, LOCALHOST_LOCATION_TYPE);
        runLaunchApplicationReferencingLocation(symbolicName, LOCALHOST_LOCATION_TYPE);

        deleteCatalogEntity(symbolicName);
    }

    @Test
    public void testLaunchApplicationReferencingLocationSpec() throws Exception {
        String symbolicName = "my.catalog.location.id.launch";
        addCatalogLocation(symbolicName, LOCALHOST_LOCATION_SPEC);
        runLaunchApplicationReferencingLocation(symbolicName, LOCALHOST_LOCATION_TYPE);

        deleteCatalogEntity(symbolicName);
    }

    @Test
    public void testLaunchApplicationReferencingOsgiLocation() throws Exception {
        String symbolicName = "my.catalog.location.id.launch";
        addCatalogOSGiLocation(symbolicName, SIMPLE_LOCATION_TYPE);
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
            "  - type: brooklyn.entity.basic.BasicStartable");

        Entity simpleEntity = Iterables.getOnlyElement(app.getChildren());
        Location location = Iterables.getOnlyElement(simpleEntity.getLocations());
        assertEquals(location.getClass().getName(), locType);
        assertEquals(location.getConfig(new BasicConfigKey<String>(String.class, "config1")), "config1");
        assertEquals(location.getConfig(new BasicConfigKey<String>(String.class, "config2")), "config2 override");
        assertEquals(location.getConfig(new BasicConfigKey<String>(String.class, "config3")), "config3");
    }

    private void addCatalogLocation(String symbolicName, String serviceType) {
        addCatalogLocation(symbolicName, serviceType, ImmutableList.<String>of());
    }

    private void addCatalogOSGiLocation(String symbolicName, String serviceType) {
        TestResourceUnavailableException.throwIfResourceUnavailable(getClass(), OsgiStandaloneTest.BROOKLYN_TEST_OSGI_ENTITIES_PATH);
        addCatalogLocation(symbolicName, serviceType, ImmutableList.of(OsgiStandaloneTest.BROOKLYN_TEST_OSGI_ENTITIES_URL));
    }
    
    private void addCatalogLocation(String symbolicName, String serviceType, List<String> libraries) {
        ImmutableList.Builder<String> yaml = ImmutableList.<String>builder().add(
                "brooklyn.catalog:",
                "  id: " + symbolicName,
                "  name: My Catalog Location",
                "  description: My description",
                "  version: " + TEST_VERSION);
        if (libraries.size() > 0) {
            yaml.add("  libraries:")
                .addAll(Lists.transform(libraries, StringFunctions.prepend("  - url: ")));
        }
        yaml.add(
                "",
                "brooklyn.locations:",
                "- type: " + serviceType,
                "  brooklyn.config:",
                "    config1: config1",
                "    config2: config2");
        
        
        addCatalogItem(yaml.build());
    }

    private int countCatalogLocations() {
        return Iterables.size(mgmt().getCatalog().getCatalogItems(CatalogPredicates.IS_LOCATION));
    }
}
