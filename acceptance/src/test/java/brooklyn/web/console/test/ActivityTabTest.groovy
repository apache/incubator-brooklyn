package brooklyn.web.console.test

import org.testng.annotations.Test
import static brooklyn.web.console.test.SeleniumTest.selenium
import static brooklyn.web.console.test.SeleniumTest.waitFor
import static org.testng.Assert.assertTrue

public class ActivityTabTest {

    @Test(groups=["Selenium1"])
    public void effectorsTabShows() throws Exception {
        selenium.open("/detail/");

        // Wait for tree to load
        waitFor({selenium.isTextPresent("tomcat node 1a.3")});

        selenium.click("link=tomcat node 1a.3")
        selenium.click("link=Activity");
        waitFor({selenium.isTextPresent("Activity Status")});
        waitFor({selenium.isTextPresent("Update values")});
        waitFor({selenium.isTextPresent("Waiting")});
    }

}
