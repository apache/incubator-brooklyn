package brooklyn.launcher

import static brooklyn.test.TestUtils.*
import static java.util.concurrent.TimeUnit.*
import static org.testng.Assert.*

import org.testng.annotations.Test

import brooklyn.management.internal.LocalManagementContext
import brooklyn.util.internal.TimeExtras
import groovy.time.TimeDuration

public class WebAppRunnerTest {
    static { TimeExtras.init() }

    private static TimeDuration TIMEOUT_MS;
	static { TIMEOUT_MS = 5*SECONDS }
    
    /**
     * This test requires the brooklyn.war to work. (Should be placed by maven build.)
     */
    @Test
    public void testStartWar1() {
        WebAppRunner launcher = new WebAppRunner(new LocalManagementContext(), port:8090,
            attributes:[brooklynWebAutologinUser:'admin']);
        assertNotNull(launcher);
        
        launcher.start();
        try {
            assertBrooklynAt("http://admin:password@localhost:8090/")        
        } finally {
            launcher.stop();
        }
    }

    private void assertBrooklynAt(String u) {
        executeUntilSucceeds(timeout:TIMEOUT_MS, maxAttempts:50) {
            String contents = new URL(u).openStream().getText();
//            println "contents: "+contents
            assertNotNull( contents )
            assertTrue( contents.contains("Brooklyn Webconsole - Dashboard") )
        }
    }
        
    @Test
    public void testStartSecondaryWar() {
        WebAppRunner launcher = new WebAppRunner(new LocalManagementContext(), 
            port: 8090, war:"brooklyn.war", wars:["hello":"hello-world.war"],
            attributes:[brooklynWebAutologinUser:'admin']);
        assertNotNull(launcher);
        
        launcher.start();
        try {

            assertBrooklynAt("http://admin:password@localhost:8090/");

            executeUntilSucceeds(timeout:TIMEOUT_MS, maxAttempts:50) {
                String contents = new URL("http://localhost:8090/hello").openStream().getText();
//                println "contents: "+contents
                assertNotNull( contents )
                assertTrue( contents.contains("This is the home page for a sample application") )
            }

        } finally {
            launcher.stop();
        }
    }

}
