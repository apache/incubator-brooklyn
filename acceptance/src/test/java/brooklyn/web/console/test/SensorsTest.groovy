package brooklyn.web.console.test

import org.openqa.selenium.WebDriver
import org.openqa.selenium.firefox.FirefoxDriver
import org.openqa.selenium.firefox.FirefoxProfile
import org.testng.annotations.AfterTest
import org.testng.annotations.BeforeTest
import org.testng.annotations.Test
import static org.testng.Assert.assertTrue
import org.openqa.selenium.By

public class SensorsTest {
    WebDriver driver;
    @BeforeTest
    public void setUp() throws Exception {
        //TODO There must be another way to get past the confirmation dialog, needs investigating
        FirefoxProfile profile = new FirefoxProfile()
        profile.setPreference("network.http.phishy-userpass-length", 255)
        driver = new FirefoxDriver(profile)

        driver.get("http://admin:password@localhost:9090/entity/")
    }

    @Test
    public void testJun() {
        assertTrue(!!(driver.findElement(By.id("status")).getText() =~ "No data yet"));

        /*        verifyTrue(selenium.isTextPresent("No data yet."));
        verifyTrue(selenium.isTextPresent("Select an entity in the tree to the left to work with it here."));
        for (int second = 0;; second++) {
            if (second >= 60) fail("timeout");
            try { if (selenium.isTextPresent("tomcat node 1a.1")) break; } catch (Exception e) {}
            Thread.sleep(1000);
        }

        selenium.click("jstree-node-id-leaf-4");
        selenium.click("jstree-node-id-leaf-5");
        selenium.waitForPageToLoad("");
        verifyTrue(selenium.isTextPresent("status-message"));
        selenium.click("link=Sensors");
        verifyTrue(selenium.isTextPresent("HTTP port"));
        verifyEquals("8086", selenium.getText("css=tbody > tr:nth(2) > td:nth(2)"));
        selenium.click("jstree-node-id-leaf-10");
        selenium.click("jstree-node-id-leaf-11");
        verifyEquals("8092", selenium.getText("css=tbody > tr:nth(2) > td:nth(2)"));

        */
    }

    @AfterTest
    public void tearDown() throws Exception {
        driver.quit()
    }
}
