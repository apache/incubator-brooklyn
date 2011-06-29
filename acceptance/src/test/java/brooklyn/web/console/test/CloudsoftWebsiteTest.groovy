package brooklyn.web.console.test

import static org.testng.Assert.*

import org.testng.annotations.AfterClass
import org.testng.annotations.AfterTest
import org.testng.annotations.BeforeClass
import org.testng.annotations.BeforeTest
import org.testng.annotations.Test

import com.thoughtworks.selenium.DefaultSelenium
import com.thoughtworks.selenium.Selenium
import org.openqa.selenium.WebElement
import org.openqa.selenium.WebDriver
import org.openqa.selenium.firefox.FirefoxDriver
import org.openqa.selenium.By

public class CloudsoftWebsiteTest extends AbstractSeleniumTest {
    Selenium selenium;

    @BeforeTest
    public void setUp() throws Exception {
        selenium = new DefaultSelenium("localhost", 4666, "*firefox", "http://www.cloudsoftcorp.com/")
        selenium.start()
    }

    @AfterTest
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
