package brooklyn.launcher;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.Closeable;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.BrooklynProperties;
import brooklyn.config.BrooklynProperties.Factory.Builder;
import brooklyn.config.BrooklynServiceAttributes;
import brooklyn.entity.Application;
import brooklyn.entity.basic.AbstractApplication;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.StartableApplication;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.trait.Startable;
import brooklyn.location.Location;
import brooklyn.location.PortRange;
import brooklyn.location.basic.PortRanges;
import brooklyn.management.ManagementContext;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.management.internal.ManagementContextInternal;
import brooklyn.rest.BrooklynWebConfig;
import brooklyn.rest.security.BrooklynPropertiesSecurityFilter;
import brooklyn.util.exceptions.CompoundRuntimeException;
import brooklyn.util.net.Networking;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.Closeables;

/**
 * Example usage is:
 *  * <pre>
 * {@code
 * BrooklynLauncher launcher = BrooklynLauncher.newInstance()
 *     .application(new WebClusterDatabaseExample().appDisplayName("Web-cluster example"))
 *     .location("localhost")
 *     .start();
 * 
 * Entities.dumpInfo(launcher.getApplications());
 * </pre>
 */
public class BrooklynLauncher {

    private static final Logger LOG = LoggerFactory.getLogger(BrooklynLauncher.class);

    /** Creates a configurable (fluent API) launcher for use starting the web console and Brooklyn applications. */
    public static BrooklynLauncher newInstance() {
        return new BrooklynLauncher();
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
    private InetAddress bindAddress = null;
    private Map<String,String> webApps = new LinkedHashMap<String,String>();
    private Map<String, ?> webconsoleFlags = Maps.newLinkedHashMap();
    private Boolean skipSecurityFilter = null;
    private boolean shutdownOnExit = true;
    
    private volatile BrooklynWebServer webServer;

    private boolean started;
    private String globalBrooklynPropertiesFile;
    private String localBrooklynPropertiesFile;

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
    public BrooklynLauncher application(Application app) {
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
    public BrooklynLauncher application(ApplicationBuilder appBuilder) {
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
    public BrooklynLauncher application(EntitySpec<? extends StartableApplication> appSpec) {
        appBuildersToManage.add(new ApplicationBuilder(checkNotNull(appSpec, "appSpec")) {
                @Override protected void doBuild() {
                }});
        return this;
    }
    
    /**
     * Adds a location to be passed in on {@link start()}, when that calls
     * {@code application.start(locations)}.
     */
    public BrooklynLauncher location(Location location) {
        locations.add(checkNotNull(location, "location"));
        return this;
    }

    /**
     * Give the spec of an application, to be created.
     * 
     * @see location(Location)
     */
    public BrooklynLauncher location(String spec) {
        locationSpecs.add(checkNotNull(spec, "spec"));
        return this;
    }
    
    public BrooklynLauncher locations(List<String> specs) {
        locationSpecs.addAll(checkNotNull(specs, "specs"));
        return this;
    }

    public BrooklynLauncher shutdownOnExit(boolean val) {
        shutdownOnExit = val;
        return this;
    }

    public BrooklynLauncher globalBrooklynPropertiesFile(String file) {
        globalBrooklynPropertiesFile = file;
        return this;
    }
    
    public BrooklynLauncher localBrooklynPropertiesFile(String file) {
        localBrooklynPropertiesFile = file;
        return this;
    }
    
    /** 
     * Specifies the management context this launcher should use. 
     * If not specified a new one is created automatically.
     */
    public BrooklynLauncher managementContext(ManagementContext context) {
        if (brooklynProperties != null) throw new IllegalStateException("Cannot set brooklynProperties and managementContext");
        this.managementContext = context;
        return this;
    }

    /**
     * Specifies the brooklyn properties to be used. 
     * Must not be set if managementContext is explicitly set.
     */
    public BrooklynLauncher brooklynProperties(BrooklynProperties brooklynProperties){
        if (managementContext != null) throw new IllegalStateException("Cannot set brooklynProperties and managementContext");
        this.brooklynProperties = checkNotNull(brooklynProperties, "brooklynProperties");
        return this;
    }
    
    /**
     * Specifies an attribute passed to deployed webapps 
     * (in addition to {@link BrooklynServiceAttributes#BROOKLYN_MANAGEMENT_CONTEXT}
     */
    public BrooklynLauncher brooklynProperties(String field, Object value) {
        brooklynAdditionalProperties.put(checkNotNull(field, "field"), value);
        return this;
    }

    /** Specifies an attribute passed to deployed webapps 
     * (in addition to {@link BrooklynServiceAttributes#BROOKLYN_MANAGEMENT_CONTEXT}
     * 
     * @deprecated in 0.5; use {@link #brooklynProperties(String, Object)}
     */
    @Deprecated
    public BrooklynLauncher setAttribute(String field, Object value) {
        return brooklynProperties(field, value);
    }

    /** 
     * Specifies whether the launcher will start the Brooklyn web console 
     * (and any additional webapps specified); default true.
     */
    public BrooklynLauncher webconsole(boolean startWebApps) {
        this.startWebApps = startWebApps;
        return this;
    }

    public BrooklynLauncher installSecurityFilter(Boolean val) {
        this.skipSecurityFilter = val == null ? null : !val;
        return this;
    }

    /** 
     * Specifies the port where the web console (and any additional webapps specified) will listen; 
     * default "8081+" being the first available >= 8081.
     */ 
    public BrooklynLauncher webconsolePort(int port) {
        return webconsolePort(PortRanges.fromInteger(port));
    }

    /**
     * Specifies the port where the web console (and any additional webapps specified) will listen;
     * default "8081+" being the first available >= 8081.
     */
    public BrooklynLauncher webconsolePort(String port) {
        return webconsolePort(PortRanges.fromString(port));
    }

    /**
     * Specifies the port where the web console (and any additional webapps specified) will listen;
     * default "8081+" being the first available >= 8081.
     */ 
    public BrooklynLauncher webconsolePort(PortRange port) {
        this.port = port;
        return this;
    }

    /**
     * Specifies the NIC where the web console (and any additional webapps specified) will be bound;
     * default 0.0.0.0, unless no security is specified (e.g. users) in which case it is localhost.
     */ 
    public BrooklynLauncher bindAddress(InetAddress bindAddress) {
        this.bindAddress = bindAddress;
        return this;
    }
    
    /**
     * Specifies additional flags to be passed to {@link BrooklynWebServer}.
     */ 
    public BrooklynLauncher webServerFlags(Map<String,?> webServerFlags) {
        this.webconsoleFlags  = webServerFlags;
        return this;
    }

    /** 
     * Specifies an additional webapp to host on the webconsole port.
     * @param contextPath The context path (e.g. "/hello", or equivalently just "hello") where the webapp will be hosted.
     *      "/" will override the brooklyn console webapp.
     * @param warUrl The URL from which the WAR should be loaded, supporting classpath:// protocol in addition to file:// and http(s)://.
     */
    public BrooklynLauncher webapp(String contextPath, String warUrl) {
        webApps.put(contextPath, warUrl);
        return this;
    }
    
    /**
     * Starts the web server (with web console) and Brooklyn applications, as per the specifications configured. 
     * @return An object containing details of the web server and the management context.
     */
    public BrooklynLauncher start() {
        doLaunch();
        
        // Start the apps
        List<Throwable> appExceptions = Lists.newArrayList();
        for (Application app : apps) {
            if (app instanceof Startable) {
                if (shutdownOnExit) Entities.invokeStopOnShutdown(app);
                try {
                    LOG.info("Starting brooklyn application {} in location{} {}", new Object[] { app, locations.size()!=1?"s":"", locations });
                    ((Startable)app).start(locations);
                } catch (Exception e) {
                    LOG.error("Error starting "+app+": "+e, e);
                    appExceptions.add(e);
                    
                    if (Thread.currentThread().isInterrupted()) {
                        LOG.error("Interrupted while starting applications; aborting");
                        break;
                    }
                }
            }
        }
        if (appExceptions.size() > 0) {
            throw new CompoundRuntimeException("Error starting applications", appExceptions);
        }
        
        return this;
    }
    
    /**
     * For backwards compatibility, to implement launch(); will be deleted when that deprecated code is deleted.
     */
    protected BrooklynLauncher doLaunch() {
        if (started) throw new IllegalStateException("Cannot start() or launch() multiple times");
        started = true;
        
        // Create the management context
        if (managementContext == null) {
            if (brooklynProperties == null) {
                Builder builder = new BrooklynProperties.Factory.Builder();
                if (globalBrooklynPropertiesFile != null) builder.globalPropertiesFile(globalBrooklynPropertiesFile);
                if (localBrooklynPropertiesFile != null) builder.localPropertiesFile(localBrooklynPropertiesFile);
                brooklynProperties = builder.build();
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
            if (BrooklynWebConfig.hasNoSecurityOptions(brooklynProperties)) {
                if (bindAddress==null) {
                    LOG.info("Starting brooklyn web-console on loopback interface because no security config is set");
                    bindAddress = Networking.LOOPBACK;
                }
                if (skipSecurityFilter==null) {
                    LOG.debug("Starting brooklyn web-console without security because we are loopback and no security is set");
                    skipSecurityFilter = true;
                }
            }
            try {
                webServer = new BrooklynWebServer(webconsoleFlags, managementContext);
                webServer.setBindAddress(bindAddress);
                webServer.setPort(port);
                webServer.putAttributes(brooklynProperties);
                if (skipSecurityFilter != Boolean.TRUE) {
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
        
        if (managementContext instanceof ManagementContextInternal) {
            ((ManagementContextInternal)managementContext).terminate();
        }
        
        for (Location loc : locations) {
            if (loc instanceof Closeable) {
                Closeables.closeQuietly((Closeable)loc);
            }
        }
    }
    
    /* ---------------------------------------
     * ! EVERYTHING BELOW HERE IS DEPRECATED !
     * ---------------------------------------
     */
    
    /** Launches the web console on port 8081, and a Brooklyn application, with a single command,
     * in such a way that the web console is launched and the application is shutdown on server termination.
     * For readability and flexibility, clients may prefer the {@link #newLauncher()} fluent syntax.
     * 
     * @deprecated in 0.5; use newInstance().application(app).start()
     */
    @Deprecated
    public static ManagementContext manage(AbstractApplication app) {
        return manage(app, 8081, true, true);
    }

    /** Launches the web console on the given port, and a Brooklyn application, with a single command,
     * in such a way that the web console is launched and the application is shutdown on server termination.
     * For readability and flexibility, clients may prefer the {@link #newLauncher()} fluent syntax.
     * 
     * @deprecated in 0.5; use newInstance().webconsolePort(port).shutdownOnExit(true).application(app).start()
     */
    @Deprecated
    public static ManagementContext manage(final AbstractApplication app, int port){
        return manage(app, port,true,true);
    }

    /** Launches the web console on the given port, and a Brooklyn application, with a single command.
     * For readability and flexibility, clients may prefer the {@link #newLauncher()} builder-style syntax.
     * 
     * @deprecated in 0.5; use newInstance().webconsolePort(port).shutdownOnExit(shutdownApp).webconsole(startWebConsole).application(app).start()
     */
    @Deprecated
    public static ManagementContext manage(final AbstractApplication app, int port, boolean shutdownApp, boolean startWebConsole) {
        // Locate the management context
        Entities.startManagement(app);
        LocalManagementContext context = (LocalManagementContext) app.getManagementContext();

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

    /** Creates a configurable (fluent API) launcher for use starting the web console and Brooklyn applications.
     * 
     * @deprecated in 0.5; use newInstance();
     */
    @Deprecated
    public static BrooklynLauncher newLauncher() {
        return newInstance();
    }
    
    /** Specifies the management context this launcher should use. 
     * If not specified a new {@link LocalManagementContext} is used.
     * 
     * @deprecated in 0.5; use managementContext(context);
     */
    @Deprecated
    public BrooklynLauncher management(ManagementContext context) {
        return managementContext(context);
    }

    /** Specifies that the launcher should manage the given Brooklyn application.
     * The application will not be started as part of this call (callers should start it when appropriate, often after the launcher is launched).
     * The application must not yet be managed.
     * 
     * @deprecated in 0.5; use application(app);
     */
    @Deprecated
    public BrooklynLauncher managing(Application app) {
        return application(app);
    }

    /** Specifies that the launcher should build and manage the given Brooklyn application.
     * The application will not be started as part of this call (callers should start it when appropriate, often after the launcher is launched).
     * The application must not yet be managed.
     * 
     * @deprecated in 0.5; use application(app);
     */
    @Deprecated
    public BrooklynLauncher managing(ApplicationBuilder app) {
        return application(app);
    }

    /** Starts the web server (with web console) and Brooklyn applications, as per the specifications configured. 
     * @return An object containing details of the web server and the management context.
     * 
     * @deprecated in 0.5; use {@link #start()}; if you really don't want to start the apps then don't pass them in!
     */
    @Deprecated
    public BrooklynServerDetails launch() {
        // for backwards compatibility:
        shutdownOnExit(false);
        
        doLaunch();
        
        return getServerDetails();
    }
}
