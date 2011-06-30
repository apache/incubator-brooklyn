package brooklyn.web.console.test

import org.openqa.selenium.WebDriver
import org.openqa.selenium.firefox.FirefoxDriver
import org.openqa.selenium.firefox.FirefoxProfile
import org.testng.annotations.Test
import static org.testng.Assert.assertTrue

public class EntityTreeTest {

    @Test
    public void entityControllerShowsEntities() throws Exception {
        FirefoxProfile profile = new FirefoxProfile()
        profile.setPreference("network.http.phishy-userpass-length", 255) //hooky!
        WebDriver driver = new FirefoxDriver(profile)
        driver.get("http://admin:password@localhost:9090/entity")
        driver.findElementById("demo1").text.contains("tomcat node 1a.1")
        driver.quit()
    }

}
