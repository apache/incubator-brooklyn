package brooklyn.launcher

import static brooklyn.test.TestUtils.*
import static java.util.concurrent.TimeUnit.*
import static org.testng.Assert.*
import groovy.time.TimeDuration

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.Test

import brooklyn.config.BrooklynServiceAttributes
import brooklyn.management.internal.LocalManagementContext
import brooklyn.util.BrooklynLanguageExtensions
import brooklyn.util.internal.TimeExtras

public class WebAppRunnerTest {
    static { TimeExtras.init() }

    public static final Logger log = LoggerFactory.getLogger(WebAppRunnerTest.class);
            
    private static TimeDuration TIMEOUT_MS;
    static { TIMEOUT_MS = 30*SECONDS }
    
    /**
     * This test requires the brooklyn.war to work. (Should be placed by maven build.)
     */
    @Test
    public void testStartWar1() {
        WebAppRunner launcher = new WebAppRunner(new LocalManagementContext(), port:8090,
            attributes:[(BrooklynServiceAttributes.BROOKLYN_AUTOLOGIN_USERNAME):'admin']);
        assertNotNull(launcher);
        
        launcher.start();
        try {
            assertBrooklynAt("http://localhost:8090/");
        } finally {
            launcher.stop();
        }
    }

    public static void assertBrooklynAt(String url) {
        assertUrlHasText(url, "Brooklyn Web Console", "Dashboard");
    }
    
    public static void assertUrlHasText(String url, String ...phrases) {
        String contents;
        executeUntilSucceeds(timeout:TIMEOUT_MS, maxAttempts:50) {
            contents = new URL(url).openStream().getText();
            assertTrue(contents!=null && contents.length()>0)
        }
        for (String text: phrases) {
            if (!contents.contains(text)) {
                println "CONTENTS OF URL MISSING TEXT: $text\n"+contents
                fail("URL $url does not contain text: $text")
            }
        }
    }
        
    @Test
    public void testStartSecondaryWar() {
        WebAppRunner launcher = new WebAppRunner(new LocalManagementContext(), 
            port: 8090, war:"brooklyn.war", wars:["hello":"hello-world.war"],
            attributes:[(BrooklynServiceAttributes.BROOKLYN_AUTOLOGIN_USERNAME):'admin']);
        assertNotNull(launcher);
        
        launcher.start();
        try {

            assertBrooklynAt("http://localhost:8090/");
            assertUrlHasText("http://localhost:8090/hello",
                "This is the home page for a sample application");

        } finally {
            launcher.stop();
        }
    }

}
