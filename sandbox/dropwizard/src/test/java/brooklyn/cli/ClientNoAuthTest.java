package brooklyn.cli;

import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.Test;

@Test(suiteName = "ClientNoAuthTest")
public class ClientNoAuthTest extends ClientTest {

  @BeforeSuite(alwaysRun = true)
  public void beforeClass() throws Exception {
    super.oneTimeSetUp("config/test-config.yml");
  }

  @AfterSuite(alwaysRun = true)
  public void afterClass() throws Exception {
    super.oneTimeTearDown();
  }
}
