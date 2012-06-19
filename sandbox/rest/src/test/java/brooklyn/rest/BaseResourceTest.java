package brooklyn.rest;

import brooklyn.rest.api.Application;
import com.sun.jersey.api.client.UniformInterfaceException;
import com.yammer.dropwizard.testing.ResourceTest;
import java.net.URI;
import java.util.concurrent.TimeoutException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;


public abstract class BaseResourceTest extends ResourceTest {

  @BeforeClass
  public void setUp() throws Exception {
    setUpJersey();
  }

  @AfterClass
  public void tearDown() throws Exception {
    tearDownJersey();
  }

  protected void waitForApplicationToBeRunning(URI applicationRef) throws InterruptedException, TimeoutException {
    int count = 0;
    while (getApplicationStatus(applicationRef) != Application.Status.RUNNING) {
      Thread.sleep(7000);
      count += 1;
      if (count == 20) {
        throw new TimeoutException("Taking to long to get to RUNNING.");
      }
    }
  }

  protected Application.Status getApplicationStatus(URI uri) {
    return client().resource(uri).get(Application.class).getStatus();
  }

  protected void waitForPageNotFoundResponse(String resource, Class<?> clazz)
      throws InterruptedException, TimeoutException {
    int count = 0;
    while (true) {
      try {
        client().resource(resource).get(clazz);

      } catch (UniformInterfaceException e) {
        if (e.getResponse().getStatus() == 404) {
          break;
        }
      }
      Thread.sleep(5000);
      count += 1;
      if (count > 20) {
        throw new TimeoutException("Timeout while waiting for 404 on " + resource);
      }
    }
  }
}
