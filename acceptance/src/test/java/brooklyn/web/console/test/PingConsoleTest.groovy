package brooklyn.web.console.test

import static org.testng.Assert.*

import org.testng.annotations.AfterTest
import org.testng.annotations.BeforeTest
import org.testng.annotations.Test

import com.thoughtworks.selenium.DefaultSelenium
import com.thoughtworks.selenium.Selenium

public class PingConsoleTest extends AbstractSeleniumTest {
    Selenium selenium;

    @BeforeTest
    public void setUp() throws Exception {
        selenium = new DefaultSelenium("localhost", 4666, "*firefox", "http://localhost:9090/")
        selenium.start()
    }

    @AfterTest
    public void tearDown() throws Exception {
        selenium.stop()
    }

    @Test
    public void findEntityController() throws Exception {
        selenium.open("http://localhost:9090/");
        assertTrue(selenium.isTextPresent("EntityController"))
        selenium.close()
    }
}
