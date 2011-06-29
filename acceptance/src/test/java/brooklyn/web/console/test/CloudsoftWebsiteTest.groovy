package brooklyn.web.console.test

import static org.testng.Assert.*

import org.testng.annotations.Test
import org.openqa.selenium.WebDriver
import org.openqa.selenium.firefox.FirefoxDriver

public class CloudsoftWebsiteTest {

    @Test
    public void findApplicationMobility() throws Exception {
        WebDriver driver = new FirefoxDriver();
        driver.get("http://www.cloudsoftcorp.com/");
        assertEquals(driver.getTitle(), "Bringing Intelligent Application Mobility to the Cloud")
        assertTrue(driver.findElementById("main_content").text.contains("Elastic Application Platform"))
        driver.quit()
    }

}
