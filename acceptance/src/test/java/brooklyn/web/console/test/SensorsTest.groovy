package brooklyn.web.console.test

import org.openqa.selenium.WebDriverBackedSelenium
import org.openqa.selenium.firefox.FirefoxDriver
import org.openqa.selenium.firefox.FirefoxProfile

import java.util.regex.Pattern

import org.testng.annotations.Test
import static org.testng.Assert.assertTrue

public class SensorsTest extends SeleneseTestCase {
    @Before
    public void setUp() throws Exception {
        WebDriver driver = new FirefoxDriver();
        String baseUrl = "http://localhost:8080/";
        Selenium selenium = new WebDriverBackedSelenium(driver, baseUrl);
        selenium.start();
    }

    @Test
    public void testJun() throws Exception {
        selenium.open("/brooklyn-web-console/entity/#summary");
        verifyTrue(selenium.isTextPresent("No data yet."));
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
    }

    @After
    public void tearDown() throws Exception {
        selenium.stop();
    }
}
