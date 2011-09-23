package brooklyn.web.console.test

import org.testng.annotations.Test
import static brooklyn.web.console.test.SeleniumTest.selenium
import static brooklyn.web.console.test.SeleniumTest.waitFor
import static org.testng.Assert.assertTrue
import org.testng.annotations.BeforeMethod
import static org.testng.Assert.assertFalse

public class SensorsTest {

    //TODO Need to add tests to make sure the sensor values change DT

    @BeforeMethod
    private void getToSensors(){
        selenium.open("/detail/");

        // Wait for tree to load
        waitFor({selenium.isTextPresent("tomcat")});

        selenium.click("link=tomcat node 1a.3")
        selenium.click("link=Sensors");
    }

    @Test(groups = "Selenium1")
    public void testSensors() {
        waitFor({selenium.isTextPresent("http.port")});

        assertTrue(selenium.isTextPresent("http.port"));
        assertTrue(selenium.isTextPresent("HTTP port"));
        assertTrue(selenium.isTextPresent("JMX port"));
    }

    @Test(groups = "Selenium1")
    public void testForNewSensorsAdded() {
        waitFor({selenium.isTextPresent("test.sensor")});
        assertTrue(selenium.isTextPresent("Added and removed every 5s"))
    }

    @Test(groups = "Selenium1")
    public void testForSensorRemoved() {
        waitFor({selenium.isTextPresent("test.sensor")});
        waitFor({!selenium.isTextPresent("test.sensor")});
        assertTrue(!selenium.isTextPresent("Added and removed every 5s"))
    }
}
