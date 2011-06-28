package brooklyn.web.console

import grails.test.*
import brooklyn.entity.Application

class ManagementContextServiceTests extends GrailsUnitTestCase {

    def testService

    protected void setUp() {
        testService = new ManagementContextService()
        super.setUp()
    }

    protected void tearDown() {
        super.tearDown()
    }

    void testGetApplication() {
        assertEquals("Application", testService.getApplications().asList().get(0).getDisplayName())
        assertEquals(1, testService.getApplications().size())
    }
}