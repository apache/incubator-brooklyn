package brooklyn.rest.resources;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.net.URI;

import javax.ws.rs.core.Response;

import org.testng.annotations.Test;

import brooklyn.entity.Application;
import brooklyn.entity.Entity;
import brooklyn.management.osgi.OsgiStandaloneTest;
import brooklyn.rest.domain.ApplicationSummary;
import brooklyn.rest.domain.CatalogEntitySummary;
import brooklyn.rest.testing.BrooklynRestResourceTest;

import com.google.common.collect.Iterables;
import com.sun.jersey.api.client.ClientResponse;

public class CatalogBundleResourceTest extends BrooklynRestResourceTest {

    @Test
    public void testListApplicationYaml() throws Exception {
        String registeredTypeName = "my.catalog.app.id.load";
        addCatalogOSGiEntity(registeredTypeName);
        CatalogEntitySummary entityItem = client().resource("/v1/catalog/entities/"+registeredTypeName)
                .get(CatalogEntitySummary.class);

        assertEquals(entityItem.getRegisteredType(), registeredTypeName);
        
    }

    @Test
    public void testLaunchApplicationYaml() throws Exception {
        String registeredTypeName = "my.catalog.app.id.launch";
        addCatalogOSGiEntity(registeredTypeName);

        String yaml = "{ name: simple-app-yaml, location: localhost, services: [ { serviceType: "+registeredTypeName+" } ] }";
        
        ClientResponse response = client().resource("/v1/applications")
            .entity(yaml, "application/x-yaml")
            .post(ClientResponse.class);
        assertTrue(response.getStatus()/100 == 2, "response is "+response);
        
        // Expect app to be running
        URI appUri = response.getLocation();
        waitForApplicationToBeRunning(response.getLocation());
        ApplicationSummary appSummary = client().resource(appUri).get(ApplicationSummary.class);
        String appId = appSummary.getId();
        assertEquals(appSummary.getSpec().getName(), "simple-app-yaml");

        Application app = (Application) getManagementContext().getEntityManager().getEntity(appId);
        Entity simpleEntity = Iterables.getOnlyElement(app.getChildren());
        assertEquals(simpleEntity.getEntityType().getName(), "brooklyn.osgi.tests.SimpleEntity");
    }

    @Test
    public void testLaunchApplicationWithCatalogReferencingOtherCatalogYaml() throws Exception {
        String referencedRegisteredTypeName = "my.catalog.app.id.referenced";
        String referrerRegisteredTypeName = "my.catalog.app.id.referring";
        addCatalogOSGiEntity(referencedRegisteredTypeName);
        addCatalogEntityReferencingCatalogEntry(referrerRegisteredTypeName, referencedRegisteredTypeName);

        String yaml = "{ name: simple-app-yaml, location: localhost, services: [ { serviceType: "+referrerRegisteredTypeName+" } ] }";
        
        ClientResponse response = client().resource("/v1/applications")
            .entity(yaml, "application/x-yaml")
            .post(ClientResponse.class);
        assertTrue(response.getStatus()/100 == 2, "response is "+response);
        
        // Expect app to be running
        URI appUri = response.getLocation();
        waitForApplicationToBeRunning(response.getLocation());
        ApplicationSummary appSummary = client().resource(appUri).get(ApplicationSummary.class);
        String appId = appSummary.getId();
        assertEquals(appSummary.getSpec().getName(), "simple-app-yaml");

        Application app = (Application) getManagementContext().getEntityManager().getEntity(appId);
        Entity simpleEntity = Iterables.getOnlyElement(app.getChildren());
        assertEquals(simpleEntity.getEntityType().getName(), "brooklyn.osgi.tests.SimpleEntity");
    }

    private void addCatalogOSGiEntity(String registeredTypeName) {
        String catalogYaml =
            "name: "+registeredTypeName+"\n"+
            // FIXME name above should be unnecessary when brooklyn.catalog below is working
            "brooklyn.catalog:\n"+
            "  id: " + registeredTypeName + "\n"+
            "  name: My Catalog App\n"+
            "  description: My description\n"+
            "  icon_url: classpath://path/to/myicon.jpg\n"+
            "  version: 0.1.2\n"+
            "  libraries:\n"+
            "  - url: classpath:/" + OsgiStandaloneTest.BROOKLYN_TEST_OSGI_ENTITIES_URL + "\n"+
            "\n"+
            "services:\n"+
            "- type: brooklyn.osgi.tests.SimpleEntity\n";

        addCatalogEntity(registeredTypeName, catalogYaml);
    }

    private void addCatalogEntityReferencingCatalogEntry(String ownRegisteredTypeName, String otherRegisteredTypeName) {
        String catalogYaml =
            "name: "+ownRegisteredTypeName+"\n"+
            // FIXME name above should be unnecessary when brooklyn.catalog below is working
            "brooklyn.catalog:\n"+
            "  id: " + ownRegisteredTypeName + "\n"+
            "  name: My Referrer Catalog App\n"+
            "  description: My referrer description\n"+
            "  icon_url: classpath://path/to/myicon.jpg\n"+
            "  version: 0.2.1\n"+
            "\n"+
            "services:\n"+
            "- type: "+otherRegisteredTypeName+"\n";

        addCatalogEntity(ownRegisteredTypeName, catalogYaml);
    }

    private void addCatalogEntity(String registeredTypeName, String catalogYaml) {
        ClientResponse catalogResponse = client().resource("/v1/catalog")
            .post(ClientResponse.class, catalogYaml);

        assertEquals(catalogResponse.getStatus(), Response.Status.CREATED.getStatusCode());
    }
}
