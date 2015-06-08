package io.brooklyn.camp.brooklyn.catalog;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import io.brooklyn.camp.brooklyn.AbstractYamlRebindTest;

import org.testng.annotations.Test;

import brooklyn.catalog.internal.CatalogUtils;
import brooklyn.entity.basic.BasicEntity;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.StartableApplication;
import brooklyn.internal.BrooklynFeatureEnablement;
import brooklyn.policy.Enricher;
import brooklyn.policy.Policy;
import brooklyn.test.policy.TestEnricher;
import brooklyn.test.policy.TestPolicy;

import com.google.common.base.Joiner;
import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;

public class CatalogYamlRebindTest extends AbstractYamlRebindTest {

    // TODO Other tests (relating to https://issues.apache.org/jira/browse/BROOKLYN-149) include:
    //   - entities cannot be instantiated because class no longer on classpath (e.g. was OSGi)
    //   - config/attribute cannot be instantiated (e.g. because class no longer on classpath)
    //   - entity file corrupt
    
    enum RebindWithCatalogTestMode {
        NO_OP,
        DELETE_CATALOG,
        REPLACE_CATALOG_WITH_NEWER_VERSION;
    }
    
    @Test
    public void testRebindWithCatalogAndApp() throws Exception {
        runRebindWithCatalogAndApp(RebindWithCatalogTestMode.NO_OP);
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
    
    @SuppressWarnings("unused")
    protected void runRebindWithCatalogAndApp(RebindWithCatalogTestMode mode) throws Exception {
        String symbolicName = "my.catalog.app.id.load";
        String version = "0.1.2";
        String catalogFormat = Joiner.on("\n").join(
                "brooklyn.catalog:",
                "  id: " + symbolicName,
                "  version: %s",
                "  item:",
                "    type: "+ BasicEntity.class.getName(),
                "    brooklyn.enrichers:",
                "    - type: "+TestEnricher.class.getName(),
                "    brooklyn.policies:",
                "    - type: "+TestPolicy.class.getName());
        
        // Create the catalog item
        addCatalogItems(String.format(catalogFormat, version));

        // Create an app, using that catalog item
        String yaml = "name: simple-app-yaml\n" +
                "location: localhost\n" +
                "services: \n" +
                "- type: "+CatalogUtils.getVersionedId(symbolicName, version);
        origApp = (StartableApplication) createAndStartApplication(yaml);
        BasicEntity origEntity = (BasicEntity) Iterables.getOnlyElement(origApp.getChildren());
        TestPolicy origPolicy = (TestPolicy) Iterables.getOnlyElement(origEntity.getPolicies());
        TestEnricher origEnricher = (TestEnricher) Iterables.tryFind(origEntity.getEnrichers(), Predicates.instanceOf(TestEnricher.class)).get();
        assertEquals(origEntity.getCatalogItemId(), symbolicName+":"+version);

        // Depending on test-mode, delete the catalog item, and then rebind
        switch (mode) {
            case DELETE_CATALOG:
                mgmt().getCatalog().deleteCatalogItem(symbolicName, version);
                break;
            case REPLACE_CATALOG_WITH_NEWER_VERSION:
                mgmt().getCatalog().deleteCatalogItem(symbolicName, version);
                version = "0.1.3";
                addCatalogItems(String.format(catalogFormat, version));
                break;
            case NO_OP:
                // no-op
        }
        
        rebind();

        // Ensure app is still there
        BasicEntity newEntity = (BasicEntity) Iterables.getOnlyElement(newApp.getChildren());
        Policy newPolicy = Iterables.getOnlyElement(newEntity.getPolicies());
        Enricher newEnricher = Iterables.tryFind(newEntity.getEnrichers(), Predicates.instanceOf(TestEnricher.class)).get();
        assertEquals(newEntity.getCatalogItemId(), symbolicName+":"+version);

        // Ensure app is still usable - e.g. "stop" effector functions as expected
        newApp.stop();
        assertFalse(Entities.isManaged(newApp));
        assertFalse(Entities.isManaged(newEntity));
    }
}
