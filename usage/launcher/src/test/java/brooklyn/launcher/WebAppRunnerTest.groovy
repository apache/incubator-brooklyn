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
import brooklyn.util.internal.BrooklynSystemProperties;
import brooklyn.util.internal.TimeExtras

public class WebAppRunnerTest {
    static { TimeExtras.init() }

    public static final Logger log = LoggerFactory.getLogger(WebAppRunnerTest.class);
            
    private static TimeDuration TIMEOUT_MS;
    static { TIMEOUT_MS = 30*SECONDS }
    
    public static createLauncher(Map properties) {
        Map bigProps = [:] + properties;
        Map attributes = bigProps.attributes
        if (attributes==null) {
            attributes = [:]
        } else {
            attributes = [:] + attributes; //copy map, don't change what was supplied
        }
        bigProps.attributes = attributes;
        attributes.put(BrooklynSystemProperties.SECURITY_PROVIDER.getPropertyName(), 'brooklyn.web.console.security.AnyoneSecurityProvider');
        return new WebAppRunner(bigProps, new LocalManagementContext());
    }
    
    /**
     * This test requires the brooklyn.war to work. (Should be placed by maven build.)
     */
    @Test
    public void testStartWar1() {
        WebAppRunner launcher = createLauncher(port:8090);
        assertNotNull(launcher);
        
        try {
            launcher.start();
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
        WebAppRunner launcher = createLauncher(port:8090, war:"brooklyn.war", wars:["hello":"hello-world.war"]);
        assertNotNull(launcher);
        
        try {
            launcher.start();

            assertBrooklynAt("http://localhost:8090/");
            assertUrlHasText("http://localhost:8090/hello",
                "This is the home page for a sample application");

        } finally {
            launcher.stop();
        }
    }

    @Test
    public void testStartSecondaryWarAfter() {
        WebAppRunner launcher = createLauncher(port:8090, war:"brooklyn.war");
        assertNotNull(launcher);
        
        try {
            launcher.start();
            launcher.deploy("/hello", "hello-world.war");

            assertBrooklynAt("http://localhost:8090/");
            assertUrlHasText("http://localhost:8090/hello",
                "This is the home page for a sample application");

        } finally {
            launcher.stop();
        }
    }


}
