package brooklyn.web.console.test

import static org.junit.Assert.*

import org.junit.Test
import com.thoughtworks.selenium.DefaultSelenium
import com.thoughtworks.selenium.Selenium
import org.junit.BeforeClass
import org.junit.AfterClass
import org.junit.After
import org.openqa.selenium.WebElement
import org.junit.Before
import org.openqa.selenium.WebDriver
import org.openqa.selenium.firefox.FirefoxDriver
import org.openqa.selenium.By

public class CloudsoftWebsiteTest {
    Selenium selenium;

    @Before
    public void setUp() throws Exception {
        selenium = new DefaultSelenium("localhost", 4666, "*firefox", "http://www.cloudsoftcorp.com/")
        selenium.start()
    }

    @After
    public void tearDown() throws Exception {
        selenium.stop()
    }

    @Test
    public void findApplicationMobility() throws Exception {
        selenium.open("http://www.cloudsoftcorp.com/");
        assertTrue(selenium.isTextPresent("Application Mobility"))
        selenium.close()
    }

}