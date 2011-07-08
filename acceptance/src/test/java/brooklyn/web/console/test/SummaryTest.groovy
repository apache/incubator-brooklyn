package brooklyn.web.console.test

import org.testng.annotations.Test
import static org.testng.Assert.*;

public class SummaryTest extends AbstractSeleniumTest {
    @Test public void testInitialText() {
        selenium.open("/entity/#summary");

        waitFor({selenium.isTextPresent("Select an entity in the tree to the left to work with it here.")});
    }
}
