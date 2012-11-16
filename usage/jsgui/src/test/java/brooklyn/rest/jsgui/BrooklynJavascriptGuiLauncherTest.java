package brooklyn.rest.jsgui;

import org.eclipse.jetty.server.Server;
import org.testng.annotations.AfterTest;
import org.testng.annotations.Test;

import brooklyn.test.HttpTestUtils;

/** Convenience and demo for launching programmatically. */
public class BrooklynJavascriptGuiLauncherTest {

    Server server = null;
    
    @AfterTest
    public void stopServer() throws Exception {
        if (server!=null) {
            server.stop();
            server = null;
        }
    }
    
    @Test
    public void testJavascriptWithoutRest() throws Exception {
        server = BrooklynJavascriptGuiLauncher.startJavascriptWithoutRest();
        checkUrlContains("/index.html", "Brooklyn");
    }

    @Test
    public void testJavascriptWithRest() throws Exception {
        server = BrooklynJavascriptGuiLauncher.startJavascriptAndRest();
        checkUrlContains("/index.html", "Brooklyn");
        checkUrlContains("/v1/catalog/entities", "Tomcat");
    }

    protected void checkUrlContains(String path, String text) {
        String rootUrl = "http://localhost:"+server.getConnectors()[0].getLocalPort();
        HttpTestUtils.assertContentContainsText(rootUrl+path, text);
    }

}
