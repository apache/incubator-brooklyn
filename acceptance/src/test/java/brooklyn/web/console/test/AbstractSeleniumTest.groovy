package brooklyn.web.console.test

import org.testng.annotations.AfterMethod
import org.testng.annotations.AfterTest
import org.testng.annotations.BeforeMethod
import org.testng.annotations.BeforeTest

import com.thoughtworks.selenium.DefaultSelenium
import com.thoughtworks.selenium.Selenium
import org.openqa.selenium.server.SeleniumServer

public abstract class AbstractSeleniumTest {
    static SeleniumServer server;
    Selenium selenium;

    @BeforeTest
    public static void startSeleniumServer() {
        server = new SeleniumServer();
        server.start();
    }

    @AfterTest
    public static void stopSeleniumServer() {
        server.stop();
    }

    @BeforeMethod
    public void setUp() throws Exception {
        selenium = new DefaultSelenium("localhost", 4444, "*firefox", "http://www.cloudsoftcorp.com/")
        selenium.start()
    }

    @AfterMethod
    public void tearDown() throws Exception {
        selenium.stop()
    }
}
