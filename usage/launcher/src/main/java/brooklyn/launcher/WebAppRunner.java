package brooklyn.launcher;

import brooklyn.config.BrooklynServiceAttributes;
import brooklyn.management.ManagementContext;
import brooklyn.util.BrooklynLanguageExtensions;
import brooklyn.util.MutableMap;
import brooklyn.util.ResourceUtils;
import brooklyn.util.flags.FlagUtils;
import brooklyn.util.flags.SetFromFlag;
import com.google.common.base.Throwables;
import com.google.common.io.ByteStreams;
import com.google.common.io.Closeables;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.webapp.WebAppContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

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

    public WebAppRunner setWar(String url) {
        this.war = url;
        return this;
    }

    public WebAppRunner addWar(String path, String warUrl) {
        wars.put(path, warUrl);
        return this;
    }

    public WebAppRunner addAttribute(String field, Object value) {
        attributes.put(field, value);
        return this;
    }

    public static File writeToTempFile(InputStream is, String prefix, String suffix) {
        File tmpWarFile;

        try {
            tmpWarFile = File.createTempFile("embedded", "war");
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
        tmpWarFile.deleteOnExit();

        OutputStream out = null;
        try {
            if (is == null) throw new NullPointerException();
            out = new FileOutputStream(tmpWarFile);
            ByteStreams.copy(is, out);
        } catch (IOException e) {
            throw Throwables.propagate(e);
        } finally {
            Closeables.closeQuietly(is);
            Closeables.closeQuietly(out);
        }
        return tmpWarFile;
    }

    /**
     * Starts the embedded web application server.
     */
    public void start() throws Exception {
        log.info("Starting Brooklyn console at http://localhost:" + port + ", running " + war + (wars != null ? " and " + wars.values() : ""));

        server = new Server(port);
        List<WebAppContext> handlers = new LinkedList<WebAppContext>();

        for (Map.Entry<String, String> entry : wars.entrySet()) {
            String pathSpec = entry.getKey();
            String warUrl = entry.getValue();
            String cleanPathSpec = pathSpec;
            while (cleanPathSpec.startsWith("/"))
                cleanPathSpec = cleanPathSpec.substring(1);

            File tmpWarFile = writeToTempFile(new ResourceUtils(this).getResourceFromUrl(warUrl), "embedded-" + cleanPathSpec, "war");

            WebAppContext context = new WebAppContext();
            context.setAttribute(BrooklynServiceAttributes.BROOKLYN_MANAGEMENT_CONTEXT, managementContext);
            for (Map.Entry<String, Object> attributeEntry : attributes.entrySet()) {
                context.setAttribute(attributeEntry.getKey(), attributeEntry.getValue());
            }

            context.setWar(tmpWarFile.getAbsolutePath());
            context.setContextPath("/" + cleanPathSpec);
            context.setParentLoaderPriority(true);
            handlers.add(context);
        }

        File tmpWarFile = writeToTempFile(new ResourceUtils(this).getResourceFromUrl(war), "embedded", "war");
        WebAppContext context;
        context = new WebAppContext();
        context.setAttribute(BrooklynServiceAttributes.BROOKLYN_MANAGEMENT_CONTEXT, managementContext);
        for (Map.Entry<String, Object> attributeEntry : attributes.entrySet()) {
            context.setAttribute(attributeEntry.getKey(), attributeEntry.getValue());
        }

        context.setWar(tmpWarFile.getAbsolutePath());
        context.setContextPath("/");
        context.setParentLoaderPriority(true);
        handlers.add(context);

        HandlerList hl = new HandlerList();
        hl.setHandlers(handlers.toArray(new WebAppContext[0]));

        server.setHandler(hl);

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

    public Server getServer() {
        return server;
    }
}
