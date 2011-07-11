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
        //TODO There must be another way to get past the confirmation dialog, needs investigating
        profile.setPreference("network.http.phishy-userpass-length", 255)
        WebDriver driver = new FirefoxDriver(profile)
        driver.get("http://admin:password@localhost:9090/entity/")
        driver.findElementById("demo1").text.contains("tomcat node 1a.1")
        driver.quit()
    }

}
