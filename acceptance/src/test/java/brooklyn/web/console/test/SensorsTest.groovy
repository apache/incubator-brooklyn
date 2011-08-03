package brooklyn.web.console.test

import org.testng.annotations.Test
import static brooklyn.web.console.test.SeleniumTest.selenium
import static brooklyn.web.console.test.SeleniumTest.waitFor
import static org.testng.Assert.assertTrue

public class SensorsTest {

    @Test(groups = "Selenium1")
    public void testSensors() {
        selenium.open("/detail/");

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
