package brooklyn.web.console.test

import org.testng.annotations.Test
import static org.testng.Assert.*;

public class SensorsTest extends AbstractSeleniumTest {

    @Test public void testSensors() {
        selenium.open("/entity/");

        // Wait for tree to load
        waitFor({selenium.isTextPresent("tomcat")});

        selenium.click("jstree-node-id-leaf-4")
        selenium.click("link=Sensors");
        waitFor({selenium.isTextPresent("HTTP port")});

        assertTrue(selenium.isTextPresent("HTTP port"));
        assertTrue(selenium.isTextPresent("8085"));
    }
}
