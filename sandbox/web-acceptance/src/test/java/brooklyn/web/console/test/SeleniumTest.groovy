package brooklyn.web.console.test

import static org.testng.Assert.assertNotNull
import static org.testng.Assert.fail

import org.openqa.selenium.server.SeleniumServer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.AfterGroups
import org.testng.annotations.AfterSuite
import org.testng.annotations.BeforeGroups
import org.testng.annotations.BeforeSuite

import brooklyn.entity.basic.Entities
import brooklyn.launcher.BrooklynWebServer
import brooklyn.management.ManagementContext
import brooklyn.management.internal.LocalManagementContext

import com.thoughtworks.selenium.DefaultSelenium
import com.thoughtworks.selenium.Selenium

public class SeleniumTest {
    private static final Logger LOG = LoggerFactory.getLogger(SeleniumTest.class)
    private static SeleniumServer seleniumServer;
    private static BrooklynWebServer launcher;

    public static int timeout = 20;
    public static Selenium selenium;

    public static void waitFor(c) {
        for (int second = 0;; second++) {
            if (second >= timeout) fail("timeout");
            try {
                if (c()) {
                    break;
                }
            } catch (Exception e) {}
            Thread.sleep(1000);
        }
    }

    @BeforeSuite
    private static void startJetty() {
        LOG.info("Starting Jetty")
        ManagementContext context = new LocalManagementContext();
        Entities.startManagement(new TestApplication(mgmt: context), context);
        launcher = new BrooklynWebServer(context, 9090)
        launcher.start()

        // hold everything up till we have something running
        for (int i = 0; i < 60; i++) {
            try {
                new URL("http://localhost:9090/detail/").content
                break
            } catch (IOException e) {
                if (e.message.contains("401")) {
                    break;
                }
            }
        }

        assertNotNull(new URL("http://localhost:9090/").content)
    }

    @AfterSuite
    private static void stopJetty() {
        LOG.info("Stopping Jetty")
        launcher.stop()
    }

    @BeforeGroups(groups = "Selenium1")
    static void startSeleniumServer() {
        LOG.info("Starting Selenium")
        seleniumServer = new SeleniumServer()
        seleniumServer.start()
        selenium = new DefaultSelenium("localhost", 4444, "*firefox", "http://admin:password@localhost:9090/detail/")
        selenium.start()
    }

    @AfterGroups(groups = "Selenium1")
    static void stopSeleniumServer() {
        LOG.info("Stopping Selenium")
        if (selenium) selenium.stop()
        selenium = null
        seleniumServer.stop()
        seleniumServer = null
    }
}
