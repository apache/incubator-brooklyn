package brooklyn.rest.resources;

import brooklyn.rest.BaseResourceTest;
import brooklyn.rest.views.SwaggerUiView;
import com.sun.jersey.api.client.UniformInterfaceException;
import org.testng.annotations.Test;

import java.io.IOException;

import static org.testng.Assert.assertEquals;

public class SwaggerUiResourceTest extends BaseResourceTest {

  @Override
  protected void setUpResources() throws Exception {
//    addResource(new SwaggerUiResource());
//    addResource(new ViewMessageBodyWriter());
  }

  // we don't have HttpHeaders inside the InMemoryTestContainer
  @Test(enabled = false)
  public void testSwaggerUiTemplateWorks() throws IOException {
    final String url = "/v1/api/docs";
    try {
      client().resource(url).get(SwaggerUiView.class);
    } catch (UniformInterfaceException e) {
      assertEquals(e.getResponse().getStatus(), 404);
    }
  }
}
