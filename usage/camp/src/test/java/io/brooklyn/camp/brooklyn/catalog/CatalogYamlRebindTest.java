package io.brooklyn.camp.brooklyn.catalog;

import static org.testng.Assert.assertFalse;
import io.brooklyn.camp.brooklyn.AbstractYamlRebindTest;

import org.testng.annotations.Test;

import brooklyn.catalog.internal.CatalogUtils;
import brooklyn.entity.basic.BasicEntity;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.StartableApplication;
import brooklyn.policy.Enricher;
import brooklyn.policy.Policy;
import brooklyn.test.policy.TestEnricher;
import brooklyn.test.policy.TestPolicy;

import com.google.common.base.Predicates;
import com.google.common.collect.Iterables;

public class CatalogYamlRebindTest extends AbstractYamlRebindTest {

    // TODO Other tests (relating to https://issues.apache.org/jira/browse/BROOKLYN-149) include:
    //   - entities cannot be instantiated because class no longer on classpath (e.g. was OSGi)
    //   - config/attribute cannot be instantiated (e.g. because class no longer on classpath)
    //   - entity file corrupt
    
    // See https://issues.apache.org/jira/browse/BROOKLYN-149.
    // Deletes the catalog item before rebind, but the referenced types are still on the 
    // default classpath.
    @Test
    public void testRebindWithCatalogDeletedAndAppExisting() throws Exception {
        runRebindWithCatalogAndApp(true);
    }
    
    @Test
    public void testRebindWithCatalogAndApp() throws Exception {
        runRebindWithCatalogAndApp(false);
    }
    
    @SuppressWarnings("unused")
    protected void runRebindWithCatalogAndApp(boolean deleteCatalogBeforeRebind) throws Exception {
        String symbolicName = "my.catalog.app.id.load";
        String version = "0.1.2";
        
        // Create the catalog item
        addCatalogItems(
            "brooklyn.catalog:",
            "  id: " + symbolicName,
            "  version: " + version,
            "  item:",
            "    type: "+ BasicEntity.class.getName(),
            "    brooklyn.enrichers:",
            "    - type: "+TestEnricher.class.getName(),
            "    brooklyn.policies:",
            "    - type: "+TestPolicy.class.getName());

        // Create an app, using that catalog item
        String yaml = "name: simple-app-yaml\n" +
                "location: localhost\n" +
                "services: \n" +
                "- type: "+CatalogUtils.getVersionedId(symbolicName, version);
        origApp = (StartableApplication) createAndStartApplication(yaml);
        BasicEntity origEntity = (BasicEntity) Iterables.getOnlyElement(origApp.getChildren());
        TestPolicy origPolicy = (TestPolicy) Iterables.getOnlyElement(origEntity.getPolicies());
        TestEnricher origEnricher = (TestEnricher) Iterables.tryFind(origEntity.getEnrichers(), Predicates.instanceOf(TestEnricher.class)).get();

        // Depending on test-mode, delete the catalog item, and then rebind
        if (deleteCatalogBeforeRebind) {
            mgmt().getCatalog().deleteCatalogItem(symbolicName, version);
        }
        
        rebind();

        // Ensure app is still there
        BasicEntity newEntity = (BasicEntity) Iterables.getOnlyElement(newApp.getChildren());
        Policy newPolicy = Iterables.getOnlyElement(newEntity.getPolicies());
        Enricher newEnricher = Iterables.tryFind(newEntity.getEnrichers(), Predicates.instanceOf(TestEnricher.class)).get();

        // Ensure app is still usable - e.g. "stop" effector functions as expected
        newApp.stop();
        assertFalse(Entities.isManaged(newApp));
        assertFalse(Entities.isManaged(newEntity));
        
        if (!deleteCatalogBeforeRebind) {
            mgmt().getCatalog().deleteCatalogItem(symbolicName, version);
        }
    }
}
