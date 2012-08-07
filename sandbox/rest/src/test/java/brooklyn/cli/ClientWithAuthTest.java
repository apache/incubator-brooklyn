package brooklyn.cli;

import com.google.common.collect.Lists;
import java.util.Arrays;
import java.util.List;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

@Test(suiteName = "ClientWithAuthTest")
public class ClientWithAuthTest extends ClientTest {

  @BeforeSuite(alwaysRun = true)
  public void beforeClass() throws Exception {
    super.oneTimeSetUp("config/test-config-with-auth.yml");
  }

  @AfterSuite(alwaysRun = true)
  public void afterClass() throws Exception {
    super.oneTimeTearDown();
  }

  @Override
  protected void runWithArgs(String... args) throws Exception {
    List<String> argsWithCredentials = Lists.newArrayList(
        "--user", "admin",
        "--password", "admin",
        "--endpoint", "http://localhost:60080"
    );
    argsWithCredentials.addAll(Arrays.asList(args));

    brooklynClient.run(argsWithCredentials.toArray(new String[argsWithCredentials.size()]));
  }
}
