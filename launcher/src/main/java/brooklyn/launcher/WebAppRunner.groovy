package brooklyn.launcher

import brooklyn.management.ManagementContext
import org.apache.commons.io.IOUtils
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.webapp.WebAppContext

public class WebAppRunner {
    private Server server;
    private int port;
    private String warClasspathPath;
    private ManagementContext managementContext;

    public WebAppRunner(ManagementContext managementContext,  int port=8081, String embeddedWarCP="/web-console.war") throws Exception {
        this.warClasspathPath = embeddedWarCP;
        this.port = port;
        this.managementContext = managementContext;
    }

    /** Starts the embedded web application server. */
    public void start() throws Exception {
        File war = File.createTempFile("embedded", "war");
        war.deleteOnExit();
        InputStream is = null;
        Writer out = null;

        try {
            is = WebAppRunner.class.getResourceAsStream(warClasspathPath);
            out = new FileWriter(war);
            IOUtils.copy(is, out);
        } finally {
            IOUtils.closeQuietly(is);
            IOUtils.closeQuietly(out);
        }

        server = new Server(port);

        WebAppContext context = new WebAppContext();
        context.setAttribute("brooklynManagementContext", managementContext);
        context.war = war.getAbsolutePath();
        context.contextPath = "/";
        context.parentLoaderPriority = true;

        server.handler = context;
        server.start();
    }

    /** Asks the app server to stop and waits for it to finish up. */
    public void stop() throws Exception {
        server.stop();
        server.join();
    }
}
