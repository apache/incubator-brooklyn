package brooklyn.rest.resources;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertTrue;

import java.io.InputStream;
import java.net.URI;

import javax.ws.rs.core.Response;

import org.testng.annotations.Test;

import brooklyn.entity.Application;
import brooklyn.entity.Entity;
import brooklyn.management.osgi.OsgiStandaloneTest;
import brooklyn.rest.domain.ApplicationSummary;
import brooklyn.rest.domain.CatalogEntitySummary;
import brooklyn.rest.testing.BrooklynRestResourceTest;
import brooklyn.util.stream.Streams;

import com.google.common.collect.Iterables;
import com.sun.jersey.api.client.ClientResponse;

public class CatalogBundleResourceTest extends BrooklynRestResourceTest {

    private static final String SIMPLE_ENTITY_TYPE = "brooklyn.osgi.tests.SimpleEntity";

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
        registerAndLaunchAndAssertSimpleEntity(registeredTypeName, SIMPLE_ENTITY_TYPE);
    }

    @Test
    public void testLaunchApplicationWithCatalogReferencingOtherCatalogYaml() throws Exception {
        String referencedRegisteredTypeName = "my.catalog.app.id.referenced";
        String referrerRegisteredTypeName = "my.catalog.app.id.referring";
        addCatalogOSGiEntity(referencedRegisteredTypeName, SIMPLE_ENTITY_TYPE);
        addCatalogOSGiEntity(referrerRegisteredTypeName, referencedRegisteredTypeName);

        String yaml = "{ name: simple-app-yaml, location: localhost, services: [ { serviceType: "+referrerRegisteredTypeName+" } ] }";
        ApplicationSummary appSummary = createAndWaitForApp(yaml);

        String appId = appSummary.getId();
        assertEquals(appSummary.getSpec().getName(), "simple-app-yaml");

        Application app = (Application) getManagementContext().getEntityManager().getEntity(appId);
        Entity simpleEntity = Iterables.getOnlyElement(app.getChildren());
        assertEquals(simpleEntity.getEntityType().getName(), SIMPLE_ENTITY_TYPE);
    }

    @Test
    public void testLaunchApplicationWithTypeUsingJavaColonPrefixInYaml() throws Exception {
        String registeredTypeName = SIMPLE_ENTITY_TYPE;
        String serviceName = "java:"+SIMPLE_ENTITY_TYPE;
        registerAndLaunchAndAssertSimpleEntity(registeredTypeName, serviceName);
    }

    @Test
    public void testLaunchApplicationLoopWithJavaTypeNameInYaml() throws Exception {
        String registeredTypeName = SIMPLE_ENTITY_TYPE;
        String serviceName = SIMPLE_ENTITY_TYPE;
        registerAndLaunchAndAssertSimpleEntity(registeredTypeName, serviceName);
    }

    @Test
    public void testLaunchApplicationLoopCatalogIdInYamlFails() throws Exception {
        String registeredTypeName = "self.referencing.type";
        registerAndLaunchFailsWithRecursionError(registeredTypeName, registeredTypeName);
    }

    private void registerAndLaunchAndAssertSimpleEntity(String registeredTypeName, String serviceType) {
        addCatalogOSGiEntity(registeredTypeName, serviceType);
        try {
            String yaml = "{ name: simple-app-yaml, location: localhost, services: [ { serviceType: "+registeredTypeName+" } ] }";
            ApplicationSummary appSummary = createAndWaitForApp(yaml);
    
            String appId = appSummary.getId();
            assertEquals(appSummary.getSpec().getName(), "simple-app-yaml");
    
            Application app = (Application) getManagementContext().getEntityManager().getEntity(appId);
            Entity simpleEntity = Iterables.getOnlyElement(app.getChildren());
            assertEquals(simpleEntity.getEntityType().getName(), SIMPLE_ENTITY_TYPE);
        } finally {
            deleteCatalogEntity(registeredTypeName);
        }
    }

    private void registerAndLaunchFailsWithRecursionError(String registeredTypeName, String serviceType) {
        addCatalogOSGiEntity(registeredTypeName, serviceType);
        try {
            String yaml = "{ name: simple-app-yaml, location: localhost, services: [ { serviceType: "+registeredTypeName+" } ] }";
            ClientResponse response = client().resource("/v1/applications")
                    .entity(yaml, "application/x-yaml")
                    .post(ClientResponse.class);
    
            int responseStatus = response.getStatus();
            String responseContent = getResponseContentAsString(response);
    
            assertFalse(responseStatus/100 == 2, "response="+response+"; content="+responseContent);
            assertTrue(responseContent.contains("Recursive reference to "+registeredTypeName), "content="+responseContent);
        } finally {
            deleteCatalogEntity(registeredTypeName);
        }
    }

    private ApplicationSummary createAndWaitForApp(String yaml) {
        ClientResponse response = client().resource("/v1/applications")
                .entity(yaml, "application/x-yaml")
                .post(ClientResponse.class);
        assertTrue(response.getStatus()/100 == 2, "response is "+response);

        URI appUri = response.getLocation();
        waitForApplicationToBeRunning(response.getLocation());
        
        return client().resource(appUri).get(ApplicationSummary.class);
    }
    
    private void addCatalogOSGiEntity(String registeredTypeName) {
        addCatalogOSGiEntity(registeredTypeName, SIMPLE_ENTITY_TYPE);
    }
    
    private void addCatalogOSGiEntity(String registeredTypeName, String serviceType) {
        String catalogYaml =
            "name: "+registeredTypeName+"\n"+
            // FIXME name above should be unnecessary -- slight problem somewhere currently
            // as testListApplicationYaml fails without the line above
            "brooklyn.catalog:\n"+
            "  id: " + registeredTypeName + "\n"+
            "  name: My Catalog App\n"+
            "  description: My description\n"+
            "  icon_url: classpath://path/to/myicon.jpg\n"+
            "  version: 0.1.2\n"+
            "  libraries:\n"+
            "  - url: " + OsgiStandaloneTest.BROOKLYN_TEST_OSGI_ENTITIES_URL + "\n"+
            "\n"+
            "services:\n"+
            "- type: " + serviceType;

        addCatalogEntity(catalogYaml);
    }

    private void addCatalogEntity(String catalogYaml) {
        ClientResponse catalogResponse = client().resource("/v1/catalog")
            .post(ClientResponse.class, catalogYaml);

        assertEquals(catalogResponse.getStatus(), Response.Status.CREATED.getStatusCode());
    }
    
    private void deleteCatalogEntity(String catalogItem) {
        ClientResponse catalogResponse = client().resource("/v1/catalog/entities/"+catalogItem)
            .delete(ClientResponse.class);

        assertEquals(catalogResponse.getStatus(), Response.Status.NO_CONTENT.getStatusCode());
    }
    
    private String getResponseContentAsString(ClientResponse response) {
        InputStream in = null;
        try {
            in = response.getEntityInputStream();
            return new String(Streams.readFully(in));
        } finally {
            Streams.closeQuietly(in);
        }
    }
}
