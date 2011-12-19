package brooklyn.launcher

import brooklyn.management.ManagementContext
import brooklyn.util.ResourceUtils;
import brooklyn.util.flags.FlagUtils;
import brooklyn.util.flags.SetFromFlag;

import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.handler.HandlerList;
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
    @SetFromFlag
    private Map<String,Object> attributes=[:];
    private ManagementContext managementContext;

    /** accepts flags:  port, 
     * war (url of war file which is the root), 
     * wars (map of context-prefix to url),
     * attrs (map of attribute-name : object pairs passed to the servlet) */
    public WebAppRunner(Map flags=[:], ManagementContext managementContext) {
        this.managementContext = managementContext
        FlagUtils.setFieldsFromFlags(flags, this);
    }        
    public WebAppRunner(ManagementContext managementContext,  int port, String warUrl="brooklyn.war") {
        this(managementContext, port:port, war:warUrl);
    }

    public static File writeToTempFile(InputStream is, String prefix, String suffix) {
        File tmpWarFile = File.createTempFile("embedded", "war")
        tmpWarFile.deleteOnExit()
        
        OutputStream out = null
        try {
            if (!is) throw new NullPointerException()
            out = new FileOutputStream(tmpWarFile)
            ByteStreams.copy(is, out)
        } finally {
            Closeables.closeQuietly(is)
            Closeables.closeQuietly(out)
        }
        tmpWarFile
    }
    /** Starts the embedded web application server. */
    public void start() throws Exception {
        log.debug("Starting Brooklyn console on port " + port)

        server = new Server(port)

        
        def handlers = []
                
        wars.each { pathSpec, warUrl ->
            String cleanPathSpec = pathSpec;
            while (cleanPathSpec.startsWith("/")) cleanPathSpec = cleanPathSpec.substring(1)
            File tmpWarFile = writeToTempFile(new ResourceUtils(this).getResourceFromUrl(warUrl), "embedded-"+cleanPathSpec, "war");

            WebAppContext context = new WebAppContext()
            context.setAttribute("brooklynManagementContext", managementContext)
            context.war = tmpWarFile.getAbsolutePath()
            context.contextPath = "/"+cleanPathSpec
            context.parentLoaderPriority = true
            handlers << context
        }

        File tmpWarFile = writeToTempFile(new ResourceUtils(this).getResourceFromUrl(war), "embedded", "war");
        WebAppContext context;
        context = new WebAppContext()
        context.setAttribute("brooklynManagementContext", managementContext)
        context.war = tmpWarFile.getAbsolutePath()
        context.contextPath = "/"
        context.parentLoaderPriority = true
        handlers << context
        
        HandlerList hl = new HandlerList()
        hl.setHandlers(handlers.toArray(new WebAppContext[0]))

        server.handler = hl
        
        server.start()

        log.info("Started Brooklyn console at http://localhost:" + port+", running "+war+(wars? " and "+wars.values() : ""))
    }

    /** Asks the app server to stop and waits for it to finish up. */
    public void stop() throws Exception {
        server.stop()
        server.join()
    }
}
