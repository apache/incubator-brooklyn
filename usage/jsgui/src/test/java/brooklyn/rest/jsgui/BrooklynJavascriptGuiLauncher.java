package brooklyn.rest.jsgui;

import java.net.InetSocketAddress;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.webapp.WebAppContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.rest.BrooklynRestApiLauncher;
import brooklyn.util.net.Networking;

/** launches Javascript GUI programmatically. and used for tests.
 * see {@link BrooklynRestApiLauncher} for more information. */
public class BrooklynJavascriptGuiLauncher {

    private static final Logger log = LoggerFactory.getLogger(BrooklynJavascriptGuiLauncher.class);
    
    public static void main(String[] args) throws Exception {
        startJavascriptAndRest();
        
        log.info("Press Ctrl-C to quit.");
    }
    
    final static int FAVOURITE_PORT = 8080;
    
    /** due to the ../jsgui trick in {@link BrooklynRestApiLauncher} we can just call that method */ 
    public static Server startJavascriptAndRest() throws Exception {
        return BrooklynRestApiLauncher.startRestResourcesViaFilter();
    }

    /** not much fun without a REST client. but TODO we should make it so the REST endpoint can be configured. */
    public static Server startJavascriptWithoutRest() throws Exception {
        WebAppContext context = new WebAppContext("./src/main/webapp", "/");
        
        Server server = new Server(new InetSocketAddress(Networking.LOOPBACK, Networking.nextAvailablePort(FAVOURITE_PORT)));
        server.setHandler(context);
        server.start();
        log.info("JS GUI server started (no REST) at  http://localhost:"+server.getConnectors()[0].getLocalPort()+"/");
        
        return server;
    }

}
