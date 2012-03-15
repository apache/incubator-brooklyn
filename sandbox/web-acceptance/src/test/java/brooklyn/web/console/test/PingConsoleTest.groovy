package brooklyn.web.console.test

import static org.testng.Assert.*

import org.testng.annotations.Test
import org.openqa.selenium.WebDriver
import org.openqa.selenium.firefox.FirefoxDriver

public class PingConsoleTest {

    @Test
    public void findEntityController() throws Exception {
        WebDriver driver = new FirefoxDriver();
        driver.get("http://localhost:9090/");
        assertTrue(driver.getPageSource().contains("EntityController"))
        driver.quit()
    }

}
