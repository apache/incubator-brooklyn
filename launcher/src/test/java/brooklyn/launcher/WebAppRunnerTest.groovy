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
     * This test requires the brooklyn.war to work.
     */
    @Test
    public void ping() {
        WebAppRunner launcher = new WebAppRunner(new LocalManagementContext(), 8090, "/brooklyn.war");
        assertNotNull(launcher);
        
        launcher.start();
        
        executeUntilSucceeds(timeout:TIMEOUT_MS, maxAttempts:50) {
            assertNotNull(new URL("http://localhost:8090/").getContent())
        }

        launcher.stop();
    }
}
