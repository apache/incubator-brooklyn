package brooklyn.launcher

import brooklyn.config.BrooklynProperties

import static brooklyn.test.TestUtils.*
import static java.util.concurrent.TimeUnit.*
import static org.testng.Assert.*
import groovy.time.TimeDuration

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.Test
import brooklyn.management.internal.LocalManagementContext
import brooklyn.test.TestUtils
import brooklyn.util.internal.TimeExtras


/**
 * These tests require the brooklyn.war to work. (Should be placed by maven build.)
 */
public class WebAppRunnerTest {
    static { TimeExtras.init() }

    public static final Logger log = LoggerFactory.getLogger(WebAppRunnerTest.class);
            
    private static TimeDuration TIMEOUT_MS;
    static { TIMEOUT_MS = 30*SECONDS }
    
    public static BrooklynWebServer createWebServer(Map properties) {
        Map bigProps = [:] + properties;
        Map attributes = bigProps.attributes
        if (attributes==null) {
            attributes = [:]
        } else {
            attributes = [:] + attributes; //copy map, don't change what was supplied
        }
        bigProps.attributes = attributes;

        BrooklynProperties brooklynProperties = BrooklynProperties.Factory.newDefault();
        brooklynProperties.putAll(bigProps);
        brooklynProperties.put("brooklyn.webconsole.security.provider","brooklyn.web.console.security.AnyoneSecurityProvider")
        return new BrooklynWebServer(bigProps, new LocalManagementContext(brooklynProperties));
    }
    /** @deprecated since 0.4.0. user createWebServer, or better, use BrooklynLauncher.newLauncher() */
    public static BrooklynWebServer createLauncher(Map properties) {
        return createWebServer(properties);        
    }
    
    @Test
    public void testStartWar1() {
        BrooklynWebServer server = createWebServer(port:8090);
        assertNotNull(server);
        
        try {
            server.start();
            assertBrooklynAt("http://localhost:8090/");
        } finally {
            server.stop();
        }
    }

    public static void assertBrooklynAt(String url) {
        assertUrlHasText(url, "Brooklyn Web Console", "Dashboard");
    }
    
    /** @deprecated since 0.4.0 use TestUtils.assertUrlHasText */
    public static void assertUrlHasText(String url, String ...phrases) {
        TestUtils.assertUrlHasText(url, phrases);
        
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
        BrooklynWebServer server = createWebServer(port:8090, war:"brooklyn.war", wars:["hello":"hello-world.war"]);
        assertNotNull(server);
        
        try {
            server.start();

            assertBrooklynAt("http://localhost:8090/");
            assertUrlHasText("http://localhost:8090/hello",
                "This is the home page for a sample application");

        } finally {
            server.stop();
        }
    }

    @Test
    public void testStartSecondaryWarAfter() {
        BrooklynWebServer server = createWebServer(port:8090, war:"brooklyn.war");
        assertNotNull(server);
        
        try {
            server.start();
            server.deploy("/hello", "hello-world.war");

            assertBrooklynAt("http://localhost:8090/");
            assertUrlHasText("http://localhost:8090/hello",
                "This is the home page for a sample application");

        } finally {
            server.stop();
        }
    }

    @Test
    public void testStartWithLauncher() {
        BrooklynServerDetails details = BrooklynLauncher.newLauncher().
            setAttribute("brooklyn.webconsole.security.provider",'brooklyn.web.console.security.AnyoneSecurityProvider').
            webapp("/hello", "hello-world.war").launch();
        
        try {
            details.getWebServer().deploy("/hello2", "hello-world.war");

            assertBrooklynAt(details.getWebServerUrl());
            assertUrlHasText(details.getWebServerUrl()+"hello", "This is the home page for a sample application");
            assertUrlHasText(details.getWebServerUrl()+"hello2", "This is the home page for a sample application");
            assertUrlStatusCodeEventually(details.getWebServerUrl()+"hello0", 404);

        } finally {
            details.getWebServer().stop();
        }
    }
    
}
