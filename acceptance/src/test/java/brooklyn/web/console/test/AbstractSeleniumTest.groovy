package brooklyn.web.console.test

import com.thoughtworks.selenium.DefaultSelenium
import com.thoughtworks.selenium.Selenium
import org.junit.After
import org.junit.AfterClass
import org.junit.Before
import org.junit.BeforeClass
import org.openqa.selenium.server.SeleniumServer

public abstract class AbstractSeleniumTest {
    static SeleniumServer server;
    Selenium selenium;

    @BeforeClass
    public static void startSeleniumServer() {
        server = new SeleniumServer();
        server.start();
    }

    @AfterClass
    public static void stopSeleniumServer() {
        server.stop();
    }

    @Before
    public void setUp() throws Exception {
        selenium = new DefaultSelenium("localhost", 4444, "*firefoxchrome", "http://www.cloudsoftcorp.com/")
        selenium.start()
    }

    @After
    public void tearDown() throws Exception {
        selenium.stop()
    }
}