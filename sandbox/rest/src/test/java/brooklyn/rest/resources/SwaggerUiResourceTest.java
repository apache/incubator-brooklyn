package brooklyn.rest.resources;

import brooklyn.rest.BaseIntegrationTest;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.testng.Assert.assertEquals;

public class SwaggerUiResourceTest extends BaseIntegrationTest {

  HttpClient httpClient = new DefaultHttpClient();

  @Test
  public void testSwaggerUiTemplateWorks() throws IOException {

    final String url = "http://localhost:8080/v1/api/docs";
    HttpGet get = new HttpGet(url);
    HttpResponse response = httpClient.execute(get);
    assertEquals(response.getStatusLine().getStatusCode(), 200);
  }



}
