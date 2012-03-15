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
        def apps = testService.getApplications();
        assertEquals(1, apps.size())
        assertEquals("Application", apps.asList().get(0).getDisplayName())
    }
}