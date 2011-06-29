package brooklyn.web.console.test

import org.testng.annotations.Test
import static org.testng.Assert.assertTrue

public class PingConsoleTest extends AbstractSeleniumTest {

    @Test
    public void findEntityController() throws Exception {
        selenium.open("http://localhost:9090/");
        assertTrue(selenium.isTextPresent("EntityController"))
        selenium.close()
    }

}
