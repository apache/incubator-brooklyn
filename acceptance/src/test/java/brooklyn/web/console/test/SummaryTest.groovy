package brooklyn.web.console.test

import org.testng.annotations.Test
import static brooklyn.web.console.test.SeleniumTest.selenium
import static brooklyn.web.console.test.SeleniumTest.waitFor

public class SummaryTest {
    @Test(groups = "Selenium1")
    public void testInitialText() {
        selenium.open("/entity/#summary");

        waitFor({selenium.isTextPresent("Select an entity in the tree to the left to work with it here.")});
    }
}
