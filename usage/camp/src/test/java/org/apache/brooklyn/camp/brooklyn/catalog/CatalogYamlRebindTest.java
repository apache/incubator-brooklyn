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

import static com.google.common.base.Preconditions.checkNotNull;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.brooklyn.api.catalog.CatalogItem;
import org.apache.brooklyn.api.mgmt.rebind.mementos.BrooklynMementoPersister;
import org.apache.brooklyn.api.policy.Policy;
import org.apache.brooklyn.api.sensor.Enricher;
import org.apache.brooklyn.api.typereg.RegisteredType;
import org.apache.brooklyn.camp.brooklyn.AbstractYamlRebindTest;
import org.apache.brooklyn.core.BrooklynFeatureEnablement;
import org.apache.brooklyn.core.catalog.internal.CatalogUtils;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.StartableApplication;
import org.apache.brooklyn.core.mgmt.persist.BrooklynMementoPersisterToObjectStore;
import org.apache.brooklyn.core.mgmt.persist.PersistenceObjectStore;
import org.apache.brooklyn.core.mgmt.persist.PersistenceObjectStore.StoreObjectAccessor;
import org.apache.brooklyn.core.mgmt.rebind.RebindOptions;
import org.apache.brooklyn.core.test.policy.TestEnricher;
import org.apache.brooklyn.core.test.policy.TestPolicy;
import org.apache.brooklyn.entity.stock.BasicEntity;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.text.Strings;
import org.testng.annotations.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

public class CatalogYamlRebindTest extends AbstractYamlRebindTest {

    // TODO Other tests (relating to https://issues.apache.org/jira/browse/BROOKLYN-149) include:
    //   - entities cannot be instantiated because class no longer on classpath (e.g. was OSGi)
    //   - config/attribute cannot be instantiated (e.g. because class no longer on classpath)
    //   - entity file corrupt
    
    enum RebindWithCatalogTestMode {
        NO_OP,
        STRIP_DEPRECATION_AND_ENABLEMENT_FROM_CATALOG_ITEM,
        DEPRECATE_CATALOG,
        DISABLE_CATALOG,
        DELETE_CATALOG,
        REPLACE_CATALOG_WITH_NEWER_VERSION;
    }
    
    @Test
    public void testRebindWithCatalogAndApp() throws Exception {
        runRebindWithCatalogAndApp(RebindWithCatalogTestMode.NO_OP);
    }
    
    @Test
    public void testRebindWithCatalogDeprecatedAndAppExisting() throws Exception {
        runRebindWithCatalogAndApp(RebindWithCatalogTestMode.DEPRECATE_CATALOG);
    }
    
    @Test
    public void testRebindWithCatalogDisabledAndAppExisting() throws Exception {
        runRebindWithCatalogAndApp(RebindWithCatalogTestMode.DISABLE_CATALOG);
    }
    
    // See https://issues.apache.org/jira/browse/BROOKLYN-149.
    // Deletes the catalog item before rebind, but the referenced types are still on the 
    // default classpath.
    // Will fallback to loading from classpath.
    @Test
    public void testRebindWithCatalogDeletedAndAppExisting() throws Exception {
        runRebindWithCatalogAndApp(RebindWithCatalogTestMode.DELETE_CATALOG);
    }
    
    // Upgrades the catalog item before rebind, deleting the old version.
    // Will automatically upgrade.
    @Test
    public void testRebindWithCatalogUpgradedWithOldDeletedAndAppExisting() throws Exception {
        BrooklynFeatureEnablement.enable(BrooklynFeatureEnablement.FEATURE_AUTO_FIX_CATALOG_REF_ON_REBIND);
        runRebindWithCatalogAndApp(RebindWithCatalogTestMode.REPLACE_CATALOG_WITH_NEWER_VERSION);
    }
    
    /**
     * Old persisted state for catalog items may not have a "deprecated" or "disabled"
     * value. Need to check that their absence will default to false.
     */
    @Test
    public void testRebindWithCatalogPropertiesForDeprecationAndEnablementAbsent() throws Exception {
        runRebindWithCatalogAndApp(RebindWithCatalogTestMode.STRIP_DEPRECATION_AND_ENABLEMENT_FROM_CATALOG_ITEM);
    }

    @SuppressWarnings({ "unused", "deprecation" })
    protected void runRebindWithCatalogAndApp(RebindWithCatalogTestMode mode) throws Exception {
        String appSymbolicName = "my.catalog.app.id.load";
        String appVersion = "0.1.0";
        String appCatalogFormat = Joiner.on("\n").join(
                "brooklyn.catalog:",
                "  id: " + appSymbolicName,
                "  version: %s",
                "  item:",
                "    type: "+ BasicEntity.class.getName(),
                "    brooklyn.enrichers:",
                "    - type: "+TestEnricher.class.getName(),
                "    brooklyn.policies:",
                "    - type: "+TestPolicy.class.getName());
        
        String locSymbolicName = "my.catalog.loc.id.load";
        String locVersion = "1.0.0";
        String locCatalogFormat = Joiner.on("\n").join(
                "brooklyn.catalog:",
                "  id: " + locSymbolicName,
                "  version: %s",
                "  item.type: location",
                "  item:",
                "    type: localhost");
        
        // Create the catalog items
        CatalogItem<?, ?> appItem = Iterables.getOnlyElement(addCatalogItems(String.format(appCatalogFormat, appVersion)));
        CatalogItem<?, ?> locItem = Iterables.getOnlyElement(addCatalogItems(String.format(locCatalogFormat, locVersion)));
        final String appItemId = appItem.getId();
        final String locItemId = locItem.getId();
        
        // Create an app, using that catalog item
        String yaml = "name: simple-app-yaml\n" +
                "location: \"brooklyn.catalog:"+CatalogUtils.getVersionedId(locSymbolicName, locVersion)+"\"\n" +
                "services: \n" +
                "- type: "+CatalogUtils.getVersionedId(appSymbolicName, appVersion);
        origApp = (StartableApplication) createAndStartApplication(yaml);
        BasicEntity origEntity = (BasicEntity) Iterables.getOnlyElement(origApp.getChildren());
        TestPolicy origPolicy = (TestPolicy) Iterables.getOnlyElement(origEntity.policies());
        TestEnricher origEnricher = (TestEnricher) Iterables.tryFind(origEntity.enrichers(), Predicates.instanceOf(TestEnricher.class)).get();
        assertEquals(origEntity.getCatalogItemId(), appSymbolicName+":"+appVersion);

        // Depending on test-mode, delete the catalog item, and then rebind
        switch (mode) {
            case DEPRECATE_CATALOG:
                CatalogUtils.setDeprecated(mgmt(), appSymbolicName, appVersion, true);
                CatalogUtils.setDeprecated(mgmt(), locSymbolicName, locVersion, true);
                break;
            case DISABLE_CATALOG:
                CatalogUtils.setDisabled(mgmt(), appSymbolicName, appVersion, true);
                CatalogUtils.setDisabled(mgmt(), locSymbolicName, locVersion, true);
                break;
            case DELETE_CATALOG:
                mgmt().getCatalog().deleteCatalogItem(appSymbolicName, appVersion);
                mgmt().getCatalog().deleteCatalogItem(locSymbolicName, locVersion);
                break;
            case REPLACE_CATALOG_WITH_NEWER_VERSION:
                mgmt().getCatalog().deleteCatalogItem(appSymbolicName, appVersion);
                mgmt().getCatalog().deleteCatalogItem(locSymbolicName, locVersion);
                appVersion = "0.2.0";
                locVersion = "1.1.0";
                addCatalogItems(String.format(appCatalogFormat, appVersion));
                addCatalogItems(String.format(locCatalogFormat, locVersion));
                break;
            case STRIP_DEPRECATION_AND_ENABLEMENT_FROM_CATALOG_ITEM:
                // set everything false -- then below we rebind with these fields removed to ensure that we can rebind
                CatalogUtils.setDeprecated(mgmt(), appSymbolicName, appVersion, false);
                CatalogUtils.setDeprecated(mgmt(), locSymbolicName, locVersion, false);
                CatalogUtils.setDisabled(mgmt(), appSymbolicName, appVersion, false);
                CatalogUtils.setDisabled(mgmt(), locSymbolicName, locVersion, false);
                break;
            case NO_OP:
                break; // no-op
            default:
                throw new IllegalStateException("Unknown mode: "+mode);
        }

        // Rebind
        if (mode == RebindWithCatalogTestMode.STRIP_DEPRECATION_AND_ENABLEMENT_FROM_CATALOG_ITEM) {
            // Edit the persisted state to remove the "deprecated" and "enablement" tags for our catalog items
            rebind(RebindOptions.create()
                    .stateTransformer(new Function<BrooklynMementoPersister, Void>() {
                        @Override public Void apply(BrooklynMementoPersister input) {
                            PersistenceObjectStore objectStore = ((BrooklynMementoPersisterToObjectStore)input).getObjectStore();
                            StoreObjectAccessor appItemAccessor = objectStore.newAccessor("catalog/"+Strings.makeValidFilename(appItemId));
                            StoreObjectAccessor locItemAccessor = objectStore.newAccessor("catalog/"+Strings.makeValidFilename(locItemId));
                            String appItemMemento = checkNotNull(appItemAccessor.get(), "appItem in catalog");
                            String locItemMemento = checkNotNull(locItemAccessor.get(), "locItem in catalog");
                            String newAppItemMemento = removeFromXml(appItemMemento, ImmutableList.of("catalogItem/deprecated", "catalogItem/disabled"));
                            String newLocItemMemento = removeFromXml(locItemMemento, ImmutableList.of("catalogItem/deprecated", "catalogItem/disabled"));
                            appItemAccessor.put(newAppItemMemento);
                            locItemAccessor.put(newLocItemMemento);
                            return null;
                        }}));
        } else {
            rebind();
        }

        // Ensure app is still there, and that it is usable - e.g. "stop" effector functions as expected
        BasicEntity newEntity = (BasicEntity) Iterables.getOnlyElement(newApp.getChildren());
        Policy newPolicy = Iterables.getOnlyElement(newEntity.policies());
        Enricher newEnricher = Iterables.tryFind(newEntity.enrichers(), Predicates.instanceOf(TestEnricher.class)).get();
        assertEquals(newEntity.getCatalogItemId(), appSymbolicName+":"+appVersion);

        newApp.stop();
        assertFalse(Entities.isManaged(newApp));
        assertFalse(Entities.isManaged(newEntity));
        
        // Ensure catalog item is as expecpted
        RegisteredType newAppItem = mgmt().getTypeRegistry().get(appSymbolicName, appVersion);
        RegisteredType newLocItem = mgmt().getTypeRegistry().get(locSymbolicName, locVersion);

        boolean itemDeployable;
        switch (mode) {
        case DISABLE_CATALOG:
            assertTrue(newAppItem.isDisabled());
            assertTrue(newLocItem.isDisabled());
            itemDeployable = false;
            break;
        case DELETE_CATALOG:
            assertNull(newAppItem);
            assertNull(newLocItem);
            itemDeployable = false;
            break;
        case DEPRECATE_CATALOG:
            assertTrue(newAppItem.isDeprecated());
            assertTrue(newLocItem.isDeprecated());
            itemDeployable = true;
            break;
        case NO_OP:
        case STRIP_DEPRECATION_AND_ENABLEMENT_FROM_CATALOG_ITEM:
        case REPLACE_CATALOG_WITH_NEWER_VERSION:
            assertNotNull(newAppItem);
            assertNotNull(newLocItem);
            assertFalse(newAppItem.isDeprecated());
            assertFalse(newAppItem.isDisabled());
            assertFalse(newLocItem.isDeprecated());
            assertFalse(newLocItem.isDisabled());
            itemDeployable = true;
            break;
        default:
            throw new IllegalStateException("Unknown mode: "+mode);
        }

        // Try to deploy a new app
        String yaml2 = "name: simple-app-yaml2\n" +
                "location: \"brooklyn.catalog:"+CatalogUtils.getVersionedId(locSymbolicName, locVersion)+"\"\n" +
                "services: \n" +
                "- type: "+CatalogUtils.getVersionedId(appSymbolicName, appVersion);

        if (itemDeployable) {
            StartableApplication app2 = (StartableApplication) createAndStartApplication(yaml2);
            BasicEntity entity2 = (BasicEntity) Iterables.getOnlyElement(app2.getChildren());
            assertEquals(entity2.getCatalogItemId(), appSymbolicName+":"+appVersion);
        } else {
            try {
                StartableApplication app2 = (StartableApplication) createAndStartApplication(yaml2);
                fail();
            } catch (Exception e) {
                if (mode == RebindWithCatalogTestMode.DELETE_CATALOG) {
                    if (!e.toString().contains("cannot be matched")) throw e;
                } else {
                    assertEquals(mode, RebindWithCatalogTestMode.DISABLE_CATALOG);
                    if (!e.toString().contains("cannot be matched")) throw e;
                }
            }
        }
    }
    
    /**
     * Given the "/"-separated path for the elements to be removed, it removes these from the xml
     * and returns the transformed XML.
     */
    private String removeFromXml(String xml, List<String> elementsToRemove) {
        try {
            InputSource source = new InputSource(new StringReader(xml));
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(source);

            for (String elementToRemove : elementsToRemove) {
                Node current = null;
                boolean first = true;
                for (String tag : elementToRemove.split("/")) {
                    NodeList matches;
                    if (first) {
                        matches = doc.getElementsByTagName(tag);
                        first = false;
                    } else {
                        matches = ((Element)current).getElementsByTagName(tag);
                    }
                    if (matches.getLength() > 0) {
                        current = matches.item(0);
                    } else {
                        current = null;
                        break;
                    }
                }
                if (current != null) {
                    current.getParentNode().removeChild(current);
                }
            }
          
            DOMSource domSource = new DOMSource(doc);
            StringWriter writer = new StringWriter();
            StreamResult result = new StreamResult(writer);
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.transform(domSource, result);
            
            return writer.toString();
          
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }
}
