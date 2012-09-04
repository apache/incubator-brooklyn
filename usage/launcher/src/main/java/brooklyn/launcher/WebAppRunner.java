package brooklyn.launcher;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.webapp.WebAppContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.BrooklynServiceAttributes;
import brooklyn.management.ManagementContext;
import brooklyn.util.BrooklynLanguageExtensions;
import brooklyn.util.MutableMap;
import brooklyn.util.ResourceUtils;
import brooklyn.util.flags.FlagUtils;
import brooklyn.util.flags.SetFromFlag;
import brooklyn.util.web.ContextHandlerCollectionHotSwappable;

import com.google.common.base.Throwables;

/**
 * Starts the web-app running, connected to the given management context
 */
public class WebAppRunner {
    private static final Logger log = LoggerFactory.getLogger(WebAppRunner.class);

    public static final String BROOKLYN_WAR_URL = "classpath://brooklyn.war";

    private Server server;

    @SetFromFlag
    private int port = 8081;

    @SetFromFlag
    private String war = BROOKLYN_WAR_URL;

    /**
     * map of context-prefix to file
     */
    @SetFromFlag
    private Map<String, String> wars = new LinkedHashMap<String, String>();

    @SetFromFlag
    private Map<String, Object> attributes = new LinkedHashMap<String, Object>();

    private ManagementContext managementContext;

    public WebAppRunner(ManagementContext managementContext) {
        this(new LinkedHashMap(), managementContext);
    }

    /**
     * accepts flags:  port,
     * war (url of war file which is the root),
     * wars (map of context-prefix to url),
     * attrs (map of attribute-name : object pairs passed to the servlet)
     */
    public WebAppRunner(Map flags, ManagementContext managementContext) {
        this.managementContext = managementContext;
        Map leftovers = FlagUtils.setFieldsFromFlags(flags, this);
        if (!leftovers.isEmpty())
            log.warn("Ignoring unknown flags " + leftovers);
    }

    public WebAppRunner(ManagementContext managementContext, int port) {
        this(managementContext, port, "brooklyn.war");
    }

    public WebAppRunner(ManagementContext managementContext, int port, String warUrl) {
        this(MutableMap.of("port", port, "war", warUrl), managementContext);
    }

    public WebAppRunner setPort(int port) {
        this.port = port;
        return this;
    }

    public int getPort() {
        return port;
    }

    /** @deprecated use {@link #deploy(String, String)} with "/" as first argument */
    public WebAppRunner setWar(String url) {
        this.war = url;
        return this;
    }

    /** @deprecated use {@link #deploy(String, String)} */
    public WebAppRunner addWar(String path, String warUrl) {
        deploy(path, warUrl);
        return this;
    }

    public WebAppRunner addAttribute(String field, Object value) {
        attributes.put(field, value);
        return this;
    }

    ContextHandlerCollectionHotSwappable handlers = new ContextHandlerCollectionHotSwappable();
    
    /**
     * Starts the embedded web application server.
     */
    public void start() throws Exception {
        log.info("Starting Brooklyn console at http://localhost:" + port + ", running " + war + (wars != null ? " and " + wars.values() : ""));

        server = new Server(port);

        for (Map.Entry<String, String> entry : wars.entrySet()) {
            String pathSpec = entry.getKey();
            String warUrl = entry.getValue();
            deploy(pathSpec, warUrl);
        }

        deploy("/", war);

        server.setHandler(handlers);
        server.start();
        //reinit required because grails wipes our language extension bindings
        BrooklynLanguageExtensions.reinit();

        log.info("Started Brooklyn console at http://localhost:" + port + ", running " + war + (wars != null ? " and " + wars.values() : ""));
    }

    /**
     * Asks the app server to stop and waits for it to finish up.
     */
    public void stop() throws Exception {
        log.info("Stopping Brooklyn web console at http://localhost:" + port + " (" + war + (wars != null ? " and " + wars.values() : "") + ")");
        server.stop();
        try {
            server.join();
        } catch (Exception e) {
            /* NPE may be thrown e.g. if threadpool not started */
        }
        log.info("Stopped Brooklyn web console at http://localhost:" + port);
    }

    /** serve given WAR at the given pathSpec; if not yet started, it is simply remembered until start;
     * if server already running, the context for this WAR is started  */
    public void deploy(String pathSpec, String warUrl) {
        String cleanPathSpec = pathSpec;
        while (cleanPathSpec.startsWith("/"))
            cleanPathSpec = cleanPathSpec.substring(1);
        boolean isRoot = cleanPathSpec.isEmpty();

        File tmpWarFile = ResourceUtils.writeToTempFile(new ResourceUtils(this).getResourceFromUrl(warUrl), 
                isRoot ? "ROOT" : ("embedded-" + cleanPathSpec), ".war");

        WebAppContext context = new WebAppContext();
        context.setAttribute(BrooklynServiceAttributes.BROOKLYN_MANAGEMENT_CONTEXT, managementContext);
        for (Map.Entry<String, Object> attributeEntry : attributes.entrySet()) {
            context.setAttribute(attributeEntry.getKey(), attributeEntry.getValue());
        }

        context.setWar(tmpWarFile.getAbsolutePath());
        context.setContextPath("/" + cleanPathSpec);
        context.setParentLoaderPriority(true);
        
        try {
            handlers.updateHandler(context);
        } catch (Exception e) {
            Throwables.propagate(e);
        }
    }

    public Server getServer() {
        return server;
    }
}
