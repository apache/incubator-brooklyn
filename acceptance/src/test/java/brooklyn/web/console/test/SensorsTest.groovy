package brooklyn.web.console.test

import org.openqa.selenium.WebDriver
import org.openqa.selenium.firefox.FirefoxDriver
import org.openqa.selenium.firefox.FirefoxProfile
import org.testng.annotations.AfterTest
import org.testng.annotations.BeforeTest
import org.testng.annotations.Test
import static org.testng.Assert.assertTrue
import org.openqa.selenium.By
import org.openqa.selenium.support.ui.WebDriverWait
import org.openqa.selenium.support.ui.Wait
import org.openqa.selenium.support.ui.ExpectedCondition
import org.openqa.selenium.chrome.ChromeDriver

public class SensorsTest {
    WebDriver driver;
    static Wait<WebDriver> wait;


    @BeforeTest
    public void setUp() throws Exception {
        //TODO There must be another way to get past the confirmation dialog, needs investigating
        FirefoxProfile profile = new FirefoxProfile()
        profile.setPreference("network.http.phishy-userpass-length", 255)
        //System.properties.setProperty("webdriver.chrome.driver", "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome")
        //driver = new ChromeDriver()
        driver = new FirefoxDriver(profile)
        wait = new WebDriverWait(driver, 30);

        driver.get("http://admin:password@localhost:9090/entity/")
    }

    void clickId(String id) {
        driver.findElement(By.id(id)).click();
    }

    @Test
    public void testInitalDisplay() {
        assertTrue(!!(driver.findElement(By.id("status")).getText() =~ "No data yet"));
        assertTrue(!!(driver.findElement(By.tagName("body")).getText() =~
                "Select an entity in the tree to the left to work with it here."));

        println driver.findElement(By.tagName("body")).getText().contains("pplica");

        wait.until(new ExpectedCondition<Boolean>() {
            public Boolean apply(WebDriver webDriver) {

                System.out.println("Searching ...");
                //return !!(driver.findElement(By.tagName("body")).getText().contains("pplica"));
                return true;
            }
        });
    }

    @Test public void testUpdatesWhenEntitySelected () {
        driver.findElement(By.id("jstree-node-id-leaf-5")).click();

        wait.until(new ExpectedCondition<Boolean>() {
            public Boolean apply(WebDriver webDriver) {

                System.out.println("Searching ...");
                return !!(driver.findElement(By.id("status-message")).getText() =~ /\d{2}:\d{2}:\d{2}/);
            }
        });
    }

    @Test public void testSummaryShown() {
        driver.findElement(By.id("jstree-node-id-leaf-5")).click();
        sleep(3000);
        clickId("summary");

        wait.until(new ExpectedCondition<Boolean>() {
            public Boolean apply(WebDriver webDriver) {

                System.out.println("Searching ...");
                return !!(driver.findElement(By.tagName("body")).getText() =~ /HTTP Port/);
            }
        });
    }

/*

        verifyEquals("8086", selenium.getText("css=tbody > tr:nth(2) > td:nth(2)"));
        selenium.click("jstree-node-id-leaf-10");
        selenium.click("jstree-node-id-leaf-11");
        verifyEquals("8092", selenium.getText("css=tbody > tr:nth(2) > td:nth(2)"));

        */

    @AfterTest
    public void tearDown() throws Exception {
        driver.quit()
    }
}
