package brooklyn.web.console.test

import org.testng.annotations.Test
import static org.testng.Assert.*;

public class SensorsTest extends AbstractSeleniumTest {

    @Test public void testInitialText() {
        selenium.open("/entity/#summary");

        waitFor({selenium.isTextPresent("tomcat node 1a.1")});
        waitFor({selenium.isTextPresent("Select an entity in the tree to the left to work with it here.")});
    }

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

    @Test public void statusIndicator() {
        selenium.open("/entity/");

        selenium.isTextPresent("No data yet.")

        // Wait for tree to load
        waitFor({selenium.isTextPresent("tomcat")});

        selenium.click("jstree-node-id-leaf-4")

        waitFor({selenium.isTextPresent("regexp:\d{2}:\d{2}:\d{2}")});
    }
}
