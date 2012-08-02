package brooklyn.rest.resources;

import brooklyn.rest.BaseResourceTest;
import com.sun.jersey.api.client.ClientResponse;
import javax.ws.rs.core.Response;
import static org.testng.Assert.assertEquals;
import org.testng.annotations.Test;

public class VersionResourceTest extends BaseResourceTest {

  @Test
  public void testGetVersion() {
    ClientResponse response = client().resource("/v1/version")
        .get(ClientResponse.class);

    assertEquals(response.getStatus(), Response.Status.OK.getStatusCode());
    String version = response.getEntity(String.class);
    assertEquals("a.b.c-SNAPSHOT", version);
  }

  @Override
  protected void setUpResources() throws Exception {
    addResource(new VersionResource());
  }
}
