package brooklyn.web.console

import static org.testng.Assert.*

import org.testng.annotations.BeforeTest
import org.testng.annotations.Test

class ManagementContextServiceTest {

    def testService

    @BeforeTest
    protected void setUp() {
        testService = new ManagementContextService()
    }

    @Test
    void testGetApplication() {
        assertEquals("Application", testService.getApplications().asList().get(0).getDisplayName())
        assertEquals(1, testService.getApplications().size())
    }
}