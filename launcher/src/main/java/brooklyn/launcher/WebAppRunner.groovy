package brooklyn.launcher

import brooklyn.management.ManagementContext
import brooklyn.util.ResourceUtils;
import brooklyn.util.flags.FlagUtils;
import brooklyn.util.flags.SetFromFlag;

import org.eclipse.jetty.server.Server
import org.eclipse.jetty.webapp.WebAppContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import com.google.common.io.ByteStreams
import com.google.common.io.Closeables

public class WebAppRunner {
    static final Logger log = LoggerFactory.getLogger(WebAppRunner.class);
    private Server server;
    @SetFromFlag
    private int port=8081;
    @SetFromFlag
    private String war="classpath://brooklyn.war";
    @SetFromFlag
    /** map of context-prefix to file */
    private Map<String,String> wars=[:];
    private ManagementContext managementContext;

    /** accepts flags:  port, war (url of war file which is the root), and wars (map of context-prefix to url) */
    public WebAppRunner(Map flags=[:], ManagementContext managementContext) {
        this.managementContext = managementContext
        FlagUtils.setFieldsFromFlags(flags, this);
    }        
    public WebAppRunner(ManagementContext managementContext,  int port=8081, String warUrl="brooklyn.war") {
        this(managementContext, port:port, war:warUrl);
    }

    /** Starts the embedded web application server. */
    public void start() throws Exception {
        log.debug("Starting Brooklyn console on port " + port)

        File war = File.createTempFile("embedded", "war")
        war.deleteOnExit()
        InputStream is = null
        OutputStream out = null

        try {
            is = new ResourceUtils(this).getResourceFromUrl(war)
            if (!is) throw new NullPointerException()
            out = new FileOutputStream(war)
            ByteStreams.copy(is, out)
        } catch (Exception e) {
            throw new IllegalArgumentException("WAR not found at $war (tried as URL, on classpath, or as file)", e)
        } finally {
            Closeables.closeQuietly(is)
            Closeables.closeQuietly(out)
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
