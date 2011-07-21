package brooklyn.web.console.test

import org.testng.annotations.Test
import static brooklyn.web.console.test.SeleniumTest.selenium
import static brooklyn.web.console.test.SeleniumTest.waitFor
import static org.testng.Assert.assertTrue

public class EffectorsTabTest {

    @Test(groups="Selenium1")
    public void effectorsTabShows() throws Exception {
        selenium.open("/entity/");

        // Wait for tree to load
        waitFor({selenium.isTextPresent("tomcat node 1a.3")});

        selenium.click("link=tomcat node 1a.3")
        selenium.click("link=Effectors");
        waitFor({selenium.isTextPresent("Parameters")});

        assertTrue(selenium.isTextPresent("Please select an effector to invoke"));
        assertTrue(selenium.isTextPresent("Restart Tomcat"));
        assertTrue(selenium.isTextPresent("Stop Tomcat"));
        assertTrue(selenium.isTextPresent("Start Tomcat"));
    }

}
