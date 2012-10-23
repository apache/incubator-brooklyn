package brooklyn.launcher;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.BrooklynProperties;
import brooklyn.config.BrooklynServiceAttributes;
import brooklyn.entity.Application;
import brooklyn.entity.basic.AbstractApplication;
import brooklyn.entity.basic.Entities;
import brooklyn.location.PortRange;
import brooklyn.location.basic.PortRanges;
import brooklyn.management.ManagementContext;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.management.internal.NonDeploymentManagementContext;

public class BrooklynLauncher {

    protected static final Logger LOG = LoggerFactory.getLogger(BrooklynLauncher.class);

    /** Launches the web console on port 8081, and a Brooklyn application, with a single command,
     * in such a way that the web console is launched and the application is shutdown on server termination.
     * For readability and flexibility, clients may prefer the {@link #newLauncher()} fluent syntax. */
    public static ManagementContext manage(AbstractApplication app) {
        return manage(app, 8081, true, true);
    }

    /** Launches the web console on the given port, and a Brooklyn application, with a single command,
     * in such a way that the web console is launched and the application is shutdown on server termination.
     * For readability and flexibility, clients may prefer the {@link #newLauncher()} fluent syntax. */
    public static ManagementContext manage(final AbstractApplication app, int port){
        return manage(app, port,true,true);
    }

    /** Launches the web console on the given port, and a Brooklyn application, with a single command.
     * For readability and flexibility, clients may prefer the {@link #newLauncher()} builder-style syntax. */
    public static ManagementContext manage(final AbstractApplication app, int port, boolean shutdownApp, boolean startWebConsole) {
        // Locate the management context
        LocalManagementContext context = new LocalManagementContext();
        context.manage(app);

        if (startWebConsole) {
            try {
                new BrooklynWebServer(context, port).start();
            } catch (Exception e) {
                LOG.warn("Failed to start Brooklyn web-console", e);
            }
        }

        if (shutdownApp) Entities.invokeStopOnShutdown(app);
        
        return context;
    }

    /** Creates a configurable (fluent API) launcher for use starting the web console and Brooklyn applications. */
    public static BrooklynLauncher newLauncher() {
        return new BrooklynLauncher();
    }
    
    private ManagementContext context = null;
    private List<Application> appsToManage = new ArrayList<Application>();
    private BrooklynProperties brooklynProperties;
    private boolean startWebApps = true;
    private PortRange port = PortRanges.fromString("8081+");
    private Map<String,String> webApps = new LinkedHashMap<String,String>();
    //private Map<String, Object> attributes = new LinkedHashMap<String, Object>();

    /** Specifies the management context this launcher should use. 
     * If not specified a new {@link LocalManagementContext} is used. */
    public BrooklynLauncher management(ManagementContext context) {
        this.context = context;
        return this;
    }

    /** Specifies that the launcher should manage the given Brooklyn application.
     * The application will not be started as part of this call (callers should start it when appropriate, often after the launcher is launched).
     * The application must not yet be managed. */
    public BrooklynLauncher managing(Application app) {
        appsToManage.add(app);
        return this;
    }

    /** Specifies whether the launcher will start the Brooklyn web console (and any additional webapps specified); default true. */
    public BrooklynLauncher webconsole(boolean startWebApps) {
        this.startWebApps = startWebApps;
        return this;
    }
    
    /** Specifies the port where the web console (and any additional webapps specified) will listed; 
     * default "8081+" being the first available >= 8081. */ 
    public BrooklynLauncher webconsolePort(int port) {
        return webconsolePort(PortRanges.fromInteger(port));
    }

    /** Specifies the port where the web console (and any additional webapps specified) will listed;
     * default "8081+" being the first available >= 8081. */ 
    public BrooklynLauncher webconsolePort(String port) {
        return webconsolePort(PortRanges.fromString(port));
    }

    /** Specifies the port where the web console (and any additional webapps specified) will listed;
     * default "8081+" being the first available >= 8081. */ 
    public BrooklynLauncher webconsolePort(PortRange port) {
        this.port = port;
        return this;
    }

    public BrooklynLauncher brooklynProperties(BrooklynProperties brooklynProperties){
        this.brooklynProperties = brooklynProperties;
        return this;
    }

    /** Specifies an additional webapp to host on the webconsole port.
     * @param contextPath The context path (e.g. "/hello", or equivalently just "hello") where the webapp will be hosted.
     *      "/" will override the brooklyn console webapp.
     * @param warUrl The URL from which the WAR should be loaded, supporting classpath:// protocol in addition to file:// and http(s)://.
     */
    public BrooklynLauncher webapp(String contextPath, String warUrl) {
        webApps.put(contextPath, warUrl);
        return this;
    }
    
    /** Specifies an attribute passed to deployed webapps 
     * (in addition to {@link BrooklynServiceAttributes#BROOKLYN_MANAGEMENT_CONTEXT} */
    public BrooklynLauncher setAttribute(String field, Object value) {
        if(brooklynProperties == null){
            brooklynProperties = BrooklynProperties.Factory.newDefault();
        }
        brooklynProperties.put(field, value);
        return this;        
    }

    /** Starts the web server (with web console) and Brooklyn applications, as per the specifications configured. 
     * @return An object containing details of the web server and the management context. */
    public BrooklynServerDetails launch() {
        if(brooklynProperties == null){
            brooklynProperties = BrooklynProperties.Factory.newDefault();
        }

        for (Application app: appsToManage) {
            ManagementContext appContext = ((AbstractApplication)app).getManagementSupport().getManagementContext(true);
            if (!(appContext instanceof NonDeploymentManagementContext)) {
                if (context!=null && !context.equals(appContext)) throw new IllegalStateException("Can't start single web console with multiple applications with different active managers");
                context = appContext;
            }
        }
        if (context==null) {
            context = new LocalManagementContext(brooklynProperties);
        }
        for (Application app: appsToManage) {
            context.manage(app);
        }
        
        BrooklynWebServer webServer = null;
        if (startWebApps) {
            try {
                webServer = new BrooklynWebServer(context);
                webServer.setPort(port);
                webServer.putAttributes(brooklynProperties);
                
                for (Map.Entry<String, String> webapp : webApps.entrySet())
                    webServer.deploy(webapp.getKey(), webapp.getValue());
                
                webServer.start();
            } catch (Exception e) {
                LOG.warn("Failed to start Brooklyn web-console: "+e, e);
            }
        }
        
        return new BrooklynServerDetails(webServer, context);
    }
    
}
