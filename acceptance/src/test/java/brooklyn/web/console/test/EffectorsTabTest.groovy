package brooklyn.web.console.test

import org.testng.annotations.Test
import static org.testng.Assert.*


public class EffectorsTabTest extends AbstractSeleniumTest{

    @Test
    public void effectorsTabShows() throws Exception {
        selenium.open("/entity/");

        // Wait for tree to load
        waitFor({selenium.isTextPresent("tomcat")});

        selenium.click("link=tomcat node 1a.3")
        selenium.click("link=Effectors");
        waitFor({selenium.isTextPresent("Parameters")});

        assertTrue(selenium.isTextPresent("Please select an effector to invoke"));
        assertTrue(selenium.isTextPresent("Restart Tomcat"));
        assertTrue(selenium.isTextPresent("Stop Tomcat"));
        assertTrue(selenium.isTextPresent("Start Tomcat"));
    }

}
