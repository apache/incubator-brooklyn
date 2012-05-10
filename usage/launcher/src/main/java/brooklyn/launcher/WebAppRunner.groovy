package brooklyn.launcher

import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.handler.HandlerList
import org.eclipse.jetty.webapp.WebAppContext
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.config.BrooklynServiceAttributes
import brooklyn.management.ManagementContext
import brooklyn.util.BrooklynLanguageExtensions;
import brooklyn.util.ResourceUtils
import brooklyn.util.flags.FlagUtils
import brooklyn.util.flags.SetFromFlag
import brooklyn.util.internal.TimeExtras;

import com.google.common.base.Preconditions;
import com.google.common.io.ByteStreams
import com.google.common.io.Closeables

/** Starts the web-app running, connected to the given management context */
public class WebAppRunner {
    private static final Logger log = LoggerFactory.getLogger(WebAppRunner.class);
    
    public static final String BROOKLYN_WAR_URL = "classpath://brooklyn.war";
     
    private Server server;
    
    @SetFromFlag
    int port=8081;
    
    @SetFromFlag
    private String war=BROOKLYN_WAR_URL;
    
    /** map of context-prefix to file */
    @SetFromFlag
    Map<String,String> wars=[:];
    
    @SetFromFlag
    Map<String,Object> attributes=[:];
    
    ManagementContext managementContext;

    /** accepts flags:  port, 
     * war (url of war file which is the root), 
     * wars (map of context-prefix to url),
     * attrs (map of attribute-name : object pairs passed to the servlet) */
    public WebAppRunner(Map flags=[:], ManagementContext managementContext) {
        this.managementContext = managementContext
        Map leftovers = FlagUtils.setFieldsFromFlags(flags, this);
        if (leftovers) log.warn("Ignoring unknown flags "+leftovers);
    }
    public WebAppRunner(ManagementContext managementContext,  int port, String warUrl="brooklyn.war") {
        this(managementContext, port:port, war:warUrl);
    }

    public WebAppRunner setPort(int port) {
        this.port = port;
        this
    }
    public WebAppRunner setWar(String url) {
        this.war = url;
        this
    }
    public WebAppRunner addWar(String path, String warUrl) {
        wars.put(path, warUrl);
        this
    }
    public WebAppRunner addAttribute(String field, Object value) {
        attributes.put(field, value);
        this
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
        log.info("Starting Brooklyn console at http://localhost:" + port+", running "+war+(wars? " and "+wars.values() : ""))

        server = new Server(port)
        def handlers = []
                
        wars.each { pathSpec, warUrl ->
            String cleanPathSpec = pathSpec;
            while (cleanPathSpec.startsWith("/")) cleanPathSpec = cleanPathSpec.substring(1)
            File tmpWarFile = writeToTempFile(new ResourceUtils(this).getResourceFromUrl(warUrl), "embedded-"+cleanPathSpec, "war");

            WebAppContext context = new WebAppContext()
            context.setAttribute(BrooklynServiceAttributes.BROOKLYN_MANAGEMENT_CONTEXT, managementContext)
            attributes.each { k,v -> context.setAttribute(k, v) }
            context.war = tmpWarFile.getAbsolutePath()
            context.contextPath = "/"+cleanPathSpec
            context.parentLoaderPriority = true
            handlers << context
        }

        File tmpWarFile = writeToTempFile(new ResourceUtils(this).getResourceFromUrl(war), "embedded", "war");
        WebAppContext context;
        context = new WebAppContext()
        context.setAttribute(BrooklynServiceAttributes.BROOKLYN_MANAGEMENT_CONTEXT, managementContext)
        attributes.each { k,v -> context.setAttribute(k, v) }
        context.war = tmpWarFile.getAbsolutePath()
        context.contextPath = "/"
        context.parentLoaderPriority = true
        handlers << context
        
        HandlerList hl = new HandlerList()
        hl.setHandlers(handlers.toArray(new WebAppContext[0]))

        server.handler = hl
        
        server.start()
        //reinit required because grails wipes our language extension bindings
        BrooklynLanguageExtensions.reinit();

        log.info("Started Brooklyn console at http://localhost:" + port+", running "+war+(wars? " and "+wars.values() : ""))
    }

    /** Asks the app server to stop and waits for it to finish up. */
    public void stop() throws Exception {
        log.info("Stopping Brooklyn web console at http://localhost:" + port+" ("+war+(wars? " and "+wars.values() : "")+")")
        server.stop()
        try { server.join() } catch (Exception e) { /* NPE may be thrown e.g. if threadpool not started */ }
        log.info("Stopped Brooklyn web console at http://localhost:" + port)
    }
    
    public Server getServer() {
        server
    }
}
