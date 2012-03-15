package brooklyn.web.console.test

import org.testng.annotations.Test
import static brooklyn.web.console.test.SeleniumTest.selenium
import static brooklyn.web.console.test.SeleniumTest.waitFor
import static org.testng.Assert.assertTrue

public class EntityTreeTest {

    @Test(groups=["Selenium1"])
    public void effectorsTabShows() throws Exception {
        selenium.open("/detail/");

        // Wait for tree to load
        waitFor({selenium.isTextPresent("tomcat tier")});
        assertTrue(selenium.isTextPresent("tomcat tier"));
    }

}
