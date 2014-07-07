package brooklyn.rest.resources;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.net.URI;

import javax.ws.rs.core.Response;

import org.testng.annotations.Test;

import brooklyn.entity.basic.BasicApplication;
import brooklyn.management.osgi.OsgiStandaloneTest;
import brooklyn.rest.domain.ApplicationSummary;
import brooklyn.rest.testing.BrooklynRestResourceTest;

import com.sun.jersey.api.client.ClientResponse;

public class CatalogBundleResourceTest extends BrooklynRestResourceTest {

    @Test
    public void testDeployApplicationYaml() throws Exception {
        String registeredTypeName = "my.catalog.app.id";
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
            "  - url: classpath:/" + OsgiStandaloneTest.BROOKLYN_TESTS_OSGI_ENTITIES_0_1_0_URL + "\n"+
            "\n"+
            "services:\n"+
            "- type: brooklyn.osgi.tests.SimpleEntity\n";

        ClientResponse catalogResponse = client().resource("/v1/catalog")
            .post(ClientResponse.class, catalogYaml);

        assertEquals(catalogResponse.getStatus(), Response.Status.CREATED.getStatusCode());

        String yaml = "{ name: simple-app-yaml, location: localhost, services: [ { serviceType: "+registeredTypeName+" } ] }";
        
        ClientResponse response = client().resource("/v1/applications")
            .entity(yaml, "application/x-yaml")
            .post(ClientResponse.class);
        assertTrue(response.getStatus()/100 == 2, "response is "+response);
        
        // Expect app to be running
        URI appUri = response.getLocation();
        waitForApplicationToBeRunning(response.getLocation());
        assertEquals(client().resource(appUri).get(ApplicationSummary.class).getSpec().getName(), "simple-app-yaml");
      }

}
