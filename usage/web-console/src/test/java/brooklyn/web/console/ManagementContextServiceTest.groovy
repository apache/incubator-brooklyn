package brooklyn.web.console

import static org.testng.Assert.*

import org.testng.annotations.BeforeTest
import org.testng.annotations.Test
import brooklyn.entity.Application
import com.google.common.collect.ImmutableList

class ManagementContextServiceTest {

    ManagementContextService testService

    @BeforeTest
    protected void setUp() {
        testService = new ManagementContextService()
    }

    @Test
    void testGetApplication() {
        Collection<Application> apps = testService.getApplications();
        assertEquals(1, apps.size())
        assertEquals("Application", ImmutableList.copyOf(apps).get(0).getDisplayName())
    }
}