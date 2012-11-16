package brooklyn.rest.resources;

import brooklyn.rest.testing.BrooklynRestResourceTest;

import com.sun.jersey.api.client.ClientResponse;
import javax.ws.rs.core.Response;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import org.testng.annotations.Test;

public class VersionResourceTest extends BrooklynRestResourceTest {

  @Test
  public void testGetVersion() {
    ClientResponse response = client().resource("/v1/version")
        .get(ClientResponse.class);

    assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
    String version = response.getEntity(String.class);

    assertTrue(version.matches("^\\d+\\.\\d+\\.\\d+.*"));
  }

  @Override
  protected void setUpResources() throws Exception {
    addResource(new VersionResource());
  }
}
