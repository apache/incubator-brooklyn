package brooklyn.web.console.test

import org.testng.annotations.Test
import static org.testng.Assert.*
import org.testng.Assert;

public class SensorsTest extends AbstractSeleniumTest {

    @Test public void testSensors() {
        selenium.open("/entity/");

        // Wait for tree to load
        waitFor({selenium.isTextPresent("tomcat")});

        selenium.click("link=tomcat node 1a.3")
        selenium.click("link=Sensors");
        waitFor({selenium.isTextPresent("http.port")});

        assertTrue(selenium.isTextPresent("http.port"));
        assertTrue(selenium.isTextPresent("HTTP port"));
        assertTrue(selenium.isTextPresent("JMX host"));
        assertTrue(selenium.isTextPresent("JMX port"));
    }
}
