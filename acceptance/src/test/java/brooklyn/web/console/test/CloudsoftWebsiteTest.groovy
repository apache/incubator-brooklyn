package brooklyn.web.console.test

import org.junit.Test
import static org.junit.Assert.assertTrue

public class CloudsoftWebsiteTest extends AbstractSeleniumTest {

    @Test
    public void findApplicationMobility() throws Exception {
        selenium.open("http://www.cloudsoftcorp.com/");
        assertTrue(selenium.isTextPresent("Application Mobility"))
        selenium.close()
    }

}