package brooklyn.launcher

import brooklyn.management.ManagementContext
import org.apache.commons.io.IOUtils
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.webapp.WebAppContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory

public class WebAppRunner {
    static final Logger log = LoggerFactory.getLogger(WebAppRunner.class);
    private Server server;
    private int port;
    private String warClasspathPath;
    private ManagementContext managementContext;

    public WebAppRunner(ManagementContext managementContext,  int port=8081, String embeddedWarCP="/web-console.war") throws Exception {
        this.warClasspathPath = embeddedWarCP
        this.port = port
        this.managementContext = managementContext
    }

    /** Starts the embedded web application server. */
    public void start() throws Exception {
        log.debug("Starting Brooklyn console on port " + port)

        File war = File.createTempFile("embedded", "war")
        war.deleteOnExit()
        InputStream is = null
        OutputStream out = null

        try {
            is = WebAppRunner.class.getResourceAsStream(warClasspathPath)
            if (!is) {
                throw new IllegalArgumentException("WAR not found on classpath at $warClasspathPath")
            }
            out = new FileOutputStream(war)
            IOUtils.copy(is, out)
        } finally {
            IOUtils.closeQuietly(is)
            IOUtils.closeQuietly(out)
        }

        server = new Server(port)

        WebAppContext context = new WebAppContext()
        context.setAttribute("brooklynManagementContext", managementContext)
        context.war = war.getAbsolutePath()
        context.contextPath = "/"
        context.parentLoaderPriority = true

        server.handler = context
        server.start()

        log.info("Started Brooklyn console at http://localhost:" + port  + context.contextPath)
    }

    /** Asks the app server to stop and waits for it to finish up. */
    public void stop() throws Exception {
        server.stop()
        server.join()
    }
}
