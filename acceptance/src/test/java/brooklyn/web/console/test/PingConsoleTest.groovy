package brooklyn.web.console.test

import com.thoughtworks.selenium.DefaultSelenium
import com.thoughtworks.selenium.Selenium
import org.junit.After
import org.junit.Before
import org.junit.Test
import static org.junit.Assert.assertTrue
import static org.junit.Assert.assertArrayEquals

public class PingConsoleTest {
    Selenium selenium;

    @Before
    public void setUp() throws Exception {
        selenium = new DefaultSelenium("localhost", 4666, "*firefox", "http://localhost:9090/")
        selenium.start()
    }

    @After
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