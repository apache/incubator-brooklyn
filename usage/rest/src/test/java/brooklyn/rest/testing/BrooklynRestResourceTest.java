package brooklyn.rest.testing;

import java.net.URI;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;

import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;

import brooklyn.rest.domain.ApplicationSummary;

import com.sun.jersey.api.client.UniformInterfaceException;
import com.sun.jersey.spi.inject.Errors;


public abstract class BrooklynRestResourceTest extends BrooklynRestApiTest {

  @BeforeClass(alwaysRun=true)
  public void setUp() throws Exception {
      // need this to debug jersey inject errors 
    java.util.logging.Logger.getLogger(Errors.class.getName()).setLevel(Level.INFO);
    
    setUpJersey();
  }

  @AfterClass(alwaysRun=false)
  public void tearDown() throws Exception {
    tearDownJersey();
  }

  protected void waitForApplicationToBeRunning(URI applicationRef) throws InterruptedException, TimeoutException {
    int count = 0;
    while (getApplicationStatus(applicationRef) != ApplicationSummary.Status.RUNNING) {
      if (getApplicationStatus(applicationRef) == ApplicationSummary.Status.ERROR)
        throw new RuntimeException("Application failed with ERROR.");
      Thread.sleep(100);
      count += 1;
      if (count >= 100) {
        throw new TimeoutException("Taking to long to get to RUNNING.");
      }
    }
  }

  protected ApplicationSummary.Status getApplicationStatus(URI uri) {
    return client().resource(uri).get(ApplicationSummary.class).getStatus();
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
      Thread.sleep(100);
      count += 1;
      if (count > 200) {
        throw new TimeoutException("Timeout while waiting for 404 on " + resource);
      }
    }
  }
}
