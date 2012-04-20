package brooklyn.rest;

import com.yammer.dropwizard.testing.ResourceTest;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;

/**
 * Making it test-ng friendly
 */
public abstract class BaseResourceTest extends ResourceTest {

  @BeforeClass
  public void setUp() throws Exception {
    setUpJersey();
  }

  @AfterClass
  public void tearDown() throws Exception {
    tearDownJersey();
  }

}
