package brooklyn.launcher;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.BrooklynProperties;
import brooklyn.config.BrooklynServiceAttributes;
import brooklyn.entity.Application;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.StartableApplication;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.trait.Startable;
import brooklyn.location.Location;
import brooklyn.location.PortRange;
import brooklyn.location.basic.PortRanges;
import brooklyn.management.ManagementContext;
import brooklyn.management.internal.AbstractManagementContext;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.rest.security.BrooklynPropertiesSecurityFilter;

import com.google.common.annotations.Beta;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.io.Closeables;

/**
 * Example usage is:
 *  * <pre>
 * {@code
 * BrooklynLauncherCli launcher = BrooklynLauncherCli.newInstance()
 *     .application(new WebClusterDatabaseExample().appDisplayName("Web-cluster example"))
 *     .location("localhost")
 *     .start();
 * 
 * Entities.dumpInfo(launcher.getApplications());
 * </pre>
 * 
 * TODO Resolve duplication between this and BrooklynLauncher.
 * 
 * @author aled
 */
@Beta
public class BrooklynLauncherCli {

    private static final Logger LOG = LoggerFactory.getLogger(BrooklynLauncherCli.class);

    /** Creates a configurable (fluent API) launcher for use starting the web console and Brooklyn applications. */
    public static BrooklynLauncherCli newInstance() {
        return new BrooklynLauncherCli();
    }
    
    private final Map<String,Object> brooklynAdditionalProperties = Maps.newLinkedHashMap();
    private BrooklynProperties brooklynProperties;
    private ManagementContext managementContext;
    
    private final List<String> locationSpecs = new ArrayList<String>();
    private final List<Location> locations = new ArrayList<Location>();

    private final List<Application> appsToManage = new ArrayList<Application>();
    private final List<ApplicationBuilder> appBuildersToManage = new ArrayList<ApplicationBuilder>();
    private final List<Application> apps = new ArrayList<Application>();
    
    private boolean startWebApps = true;
    private PortRange port = PortRanges.fromString("8081+");
    private Map<String,String> webApps = new LinkedHashMap<String,String>();
    private Map<String, ?> webconsoleFlags = Maps.newLinkedHashMap();
    private boolean installSecurityFilter = true;

    private volatile BrooklynWebServer webServer;

    private boolean started;

    public List<Application> getApplications() {
        if (!started) throw new IllegalStateException("Cannot retrieve application until started");
        return ImmutableList.copyOf(apps);
    }
    
    public BrooklynServerDetails getServerDetails() {
        if (!started) throw new IllegalStateException("Cannot retrieve server details until started");
        return new BrooklynServerDetails(webServer, managementContext);
    }
    
    /** 
     * Specifies that the launcher should manage the given Brooklyn application.
     * The application must not yet be managed. 
     * The application will not be started as part of this call (callers can
     * subsequently call {@link start()} or {@link getApplications()}.
     * 
     * @see application(ApplicationBuilder)
     */
    public BrooklynLauncherCli application(Application app) {
        if (Entities.isManaged(app)) throw new IllegalArgumentException("Application must not already be managed");
        appsToManage.add(checkNotNull(app, "app"));
        return this;
    }

    /** 
     * Specifies that the launcher should build and manage the given Brooklyn application.
     * The application must not yet be managed. 
     * The application will not be started as part of this call (callers can
     * subsequently call {@link start()} or {@link getApplications()}.
     * 
     * @see application(Application)
     */
    public BrooklynLauncherCli application(ApplicationBuilder appBuilder) {
        appBuildersToManage.add(checkNotNull(appBuilder, "appBuilder"));
        return this;
    }

    /** 
     * Specifies that the launcher should build and manage the Brooklyn application
     * described by the given spec.
     * The application will not be started as part of this call (callers can
     * subsequently call {@link start()} or {@link getApplications()}.
     * 
     * @see application(Application)
     */
    public BrooklynLauncherCli application(EntitySpec<? extends StartableApplication> appSpec) {
        appBuildersToManage.add(new ApplicationBuilder(checkNotNull(appSpec, "appSpec")) {
                @Override protected void doBuild() {
                }});
        return this;
    }
    
    /**
     * Adds a location to be passed in on {@link start()}, when that calls
     * {@code application.start(locations)}.
     */
    public BrooklynLauncherCli location(Location location) {
        locations.add(checkNotNull(location, "location"));
        return this;
    }

    /**
     * Give the spec of an application, to be created.
     * 
     * @see location(Location)
     */
    public BrooklynLauncherCli location(String locationSpec) {
        locationSpecs.add(checkNotNull(locationSpec, "locationSpec"));
        return this;
    }
    
    public BrooklynLauncherCli locations(List<String> locationSpecs) {
        locationSpecs.addAll(checkNotNull(locationSpecs, "locationSpecs"));
        return this;
    }

    /** 
     * Specifies the management context this launcher should use. 
     * If not specified a new one is created automatically.
     */
    public BrooklynLauncherCli managementContext(ManagementContext context) {
        if (brooklynProperties != null) throw new IllegalStateException("Cannot set brooklynProperties and managementContext");
        this.managementContext = context;
        return this;
    }

    /**
     * Specifies the brooklyn properties to be used. 
     * Must not be set if managementContext is explicitly set.
     */
    public BrooklynLauncherCli brooklynProperties(BrooklynProperties brooklynProperties){
        if (managementContext != null) throw new IllegalStateException("Cannot set brooklynProperties and managementContext");
        this.brooklynProperties = checkNotNull(brooklynProperties, "brooklynProperties");
        return this;
    }
    
    /**
     * Specifies an attribute passed to deployed webapps 
     * (in addition to {@link BrooklynServiceAttributes#BROOKLYN_MANAGEMENT_CONTEXT}
     */
    public BrooklynLauncherCli brooklynProperties(String field, Object value) {
        brooklynAdditionalProperties.put(checkNotNull(field, "field"), value);
        return this;
    }

    /** 
     * Specifies whether the launcher will start the Brooklyn web console 
     * (and any additional webapps specified); default true.
     */
    public BrooklynLauncherCli webconsole(boolean startWebApps) {
        this.startWebApps = startWebApps;
        return this;
    }

    public BrooklynLauncherCli installSecurityFilter(boolean val) {
        this.installSecurityFilter = val;
        return this;
    }

    /** 
     * Specifies the port where the web console (and any additional webapps specified) will listed; 
     * default "8081+" being the first available >= 8081.
     */ 
    public BrooklynLauncherCli webconsolePort(int port) {
        return webconsolePort(PortRanges.fromInteger(port));
    }

    /**
     * Specifies the port where the web console (and any additional webapps specified) will listed;
     * default "8081+" being the first available >= 8081.
     */
    public BrooklynLauncherCli webconsolePort(String port) {
        return webconsolePort(PortRanges.fromString(port));
    }

    /**
     * Specifies the port where the web console (and any additional webapps specified) will listed;
     * default "8081+" being the first available >= 8081.
     */ 
    public BrooklynLauncherCli webconsolePort(PortRange port) {
        this.port = port;
        return this;
    }

    /**
     * Specifies additional flags to be passed to {@link BrooklynWebServer}.
     */ 
    public BrooklynLauncherCli webServerFlags(Map<String,?> webServerFlags) {
        this.webconsoleFlags  = webServerFlags;
        return this;
    }

    /** 
     * Specifies an additional webapp to host on the webconsole port.
     * @param contextPath The context path (e.g. "/hello", or equivalently just "hello") where the webapp will be hosted.
     *      "/" will override the brooklyn console webapp.
     * @param warUrl The URL from which the WAR should be loaded, supporting classpath:// protocol in addition to file:// and http(s)://.
     */
    public BrooklynLauncherCli webapp(String contextPath, String warUrl) {
        webApps.put(contextPath, warUrl);
        return this;
    }
    
    /**
     * Starts the web server (with web console) and Brooklyn applications, as per the specifications configured. 
     * @return An object containing details of the web server and the management context.
     */
    public BrooklynLauncherCli start() {
        if (started) throw new IllegalStateException("Cannot start() multiple times");
        started = true;
        
        // Create the management context
        if (managementContext == null) {
            if (brooklynProperties == null) {
                brooklynProperties = BrooklynProperties.Factory.newDefault();
            }
            managementContext = new LocalManagementContext(brooklynProperties);
        }
        for (Map.Entry<String, Object> entry : brooklynAdditionalProperties.entrySet()) {
            brooklynProperties.put(entry.getKey(), entry.getValue());
        }

        // Create the locations
        locations.addAll(managementContext.getLocationRegistry().resolve(locationSpecs));
        
        // Start the web-console
        if (startWebApps) {
            try {
                webServer = new BrooklynWebServer(webconsoleFlags, managementContext);
                webServer.setPort(port);
                webServer.putAttributes(brooklynProperties);
                if (installSecurityFilter) {
                    webServer.setSecurityFilter(BrooklynPropertiesSecurityFilter.class);
                }
                
                for (Map.Entry<String, String> webapp : webApps.entrySet())
                    webServer.deploy(webapp.getKey(), webapp.getValue());
                
                webServer.start();
                
            } catch (Exception e) {
                LOG.warn("Failed to start Brooklyn web-console: "+e, e);
            }
        }
        
        // Create the apps
        for (ApplicationBuilder appBuilder : appBuildersToManage) {
            StartableApplication app = appBuilder.manage(managementContext);
            apps.add(app);
        }
        for (Application app : appsToManage) {
            Entities.startManagement(app, managementContext);
            apps.add(app);
        }
        
        // Start the apps
        for (Application app : apps) {
            if (app instanceof Startable) {
                ((Startable)app).start(locations);
            }
        }
        
        return this;
    }
    
    /**
     * Terminates this launch, but does <em>not</em> stop the applications (i.e. external processes
     * are left running, etc). However, by terminating the management console the brooklyn applications
     * become unusable.
     */
    public void terminate() {
        if (!started) return; // no-op
        
        if (webServer != null) {
            try {
                webServer.stop();
            } catch (Exception e) {
                LOG.warn("Error stopping web-server; continuing with termination", e);
            }
        }
        
        if (managementContext instanceof AbstractManagementContext) {
            ((AbstractManagementContext)managementContext).terminate();
        }
        
        for (Location loc : locations) {
            if (loc instanceof Closeable) {
                Closeables.closeQuietly((Closeable)loc);
            }
        }
    }
}
