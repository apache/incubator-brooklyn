package brooklyn.launcher;

import static com.google.common.base.Preconditions.checkNotNull;
import io.brooklyn.camp.CampPlatform;
import io.brooklyn.camp.brooklyn.BrooklynCampPlatformLauncherNoServer;
import io.brooklyn.camp.brooklyn.spi.creation.BrooklynAssemblyTemplateInstantiator;
import io.brooklyn.camp.spi.AssemblyTemplate;
import io.brooklyn.camp.spi.instantiate.AssemblyTemplateInstantiator;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.net.InetAddress;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.BrooklynProperties;
import brooklyn.config.BrooklynProperties.Factory.Builder;
import brooklyn.config.BrooklynServerConfig;
import brooklyn.config.BrooklynServiceAttributes;
import brooklyn.config.ConfigKey;
import brooklyn.entity.Application;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.BrooklynShutdownHooks;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.StartableApplication;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.rebind.RebindManager;
import brooklyn.entity.rebind.RebindManagerImpl;
import brooklyn.entity.rebind.persister.BrooklynMementoPersisterToMultiFile;
import brooklyn.entity.trait.Startable;
import brooklyn.location.Location;
import brooklyn.location.PortRange;
import brooklyn.location.basic.PortRanges;
import brooklyn.management.ManagementContext;
import brooklyn.management.ha.HighAvailabilityManager;
import brooklyn.management.ha.HighAvailabilityManagerImpl;
import brooklyn.management.ha.HighAvailabilityMode;
import brooklyn.management.ha.ManagementPlaneSyncRecordPersister;
import brooklyn.management.ha.ManagementPlaneSyncRecordPersisterToMultiFile;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.management.internal.ManagementContextInternal;
import brooklyn.mementos.BrooklynMementoPersister;
import brooklyn.rest.BrooklynWebConfig;
import brooklyn.rest.security.BrooklynPropertiesSecurityFilter;
import brooklyn.util.exceptions.CompoundRuntimeException;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.exceptions.RuntimeInterruptedException;
import brooklyn.util.net.Networking;
import brooklyn.util.stream.Streams;
import brooklyn.util.time.Duration;
import brooklyn.util.time.Time;

import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;

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
    private final List<String> yamlAppsToManage = new ArrayList<String>();
    private final List<Application> apps = new ArrayList<Application>();
    
    private boolean startWebApps = true;
    private PortRange port = PortRanges.fromString("8081+");
    private InetAddress bindAddress = null;
    private Map<String,String> webApps = new LinkedHashMap<String,String>();
    private Map<String, ?> webconsoleFlags = Maps.newLinkedHashMap();
    private Boolean skipSecurityFilter = null;
    private boolean shutdownOnExit = true;
    private PersistMode persistMode = PersistMode.DISABLED;
    private HighAvailabilityMode highAvailabilityMode = HighAvailabilityMode.DISABLED;
    private File persistenceDir;
    private Duration persistPeriod = Duration.ONE_SECOND;
    private Duration haHeartbeatTimeout = Duration.THIRTY_SECONDS;
    private Duration haHeartbeatPeriod = Duration.ONE_SECOND;
    
    private volatile BrooklynWebServer webServer;
    private CampPlatform campPlatform;

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
     * subsequently call {@link #start()} or {@link #getApplications()}.
     * 
     * @see #application(ApplicationBuilder)
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
     * subsequently call {@link #start()} or {@link #getApplications()}.
     * 
     * @see #application(Application)
     */
    public BrooklynLauncher application(ApplicationBuilder appBuilder) {
        appBuildersToManage.add(checkNotNull(appBuilder, "appBuilder"));
        return this;
    }

    /** 
     * Specifies that the launcher should build and manage the Brooklyn application
     * described by the given spec.
     * The application will not be started as part of this call (callers can
     * subsequently call {@link #start()} or {@link #getApplications()}.
     * 
     * @see #application(Application)
     */
    public BrooklynLauncher application(EntitySpec<? extends StartableApplication> appSpec) {
        appBuildersToManage.add(new ApplicationBuilder(checkNotNull(appSpec, "appSpec")) {
                @Override protected void doBuild() {
                }});
        return this;
    }

    /**
     * Specifies that the launcher should build and manage the Brooklyn application
     * described by the given YAML blueprint.
     * The application will not be started as part of this call (callers can
     * subsequently call {@link #start()} or {@link #getApplications()}.
     *
     * @see #application(Application)
     */
    public BrooklynLauncher application(String yaml) {
        this.yamlAppsToManage.add(yaml);
        return this;
    }

    /**
     * Adds a location to be passed in on {@link #start()}, when that calls
     * {@code application.start(locations)}.
     */
    public BrooklynLauncher location(Location location) {
        locations.add(checkNotNull(location, "location"));
        return this;
    }

    /**
     * Give the spec of an application, to be created.
     * 
     * @see #location(Location)
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
    public <T> BrooklynLauncher brooklynProperties(ConfigKey<T> key, T value) {
        return brooklynProperties(key.getName(), value);
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
    
    public BrooklynLauncher persistMode(PersistMode persistMode) {
        this.persistMode = persistMode;
        return this;
    }
    
    public BrooklynLauncher highAvailabilityMode(HighAvailabilityMode highAvailabilityMode) {
        this.highAvailabilityMode = highAvailabilityMode;
        return this;
    }
    
    public BrooklynLauncher persistenceDir(String persistenceDir) {
        return persistenceDir(new File(persistenceDir));
    }

    public BrooklynLauncher persistenceDir(File persistenceDir) {
        this.persistenceDir = persistenceDir;
        return this;
    }

    public BrooklynLauncher persistPeriod(Duration persistPeriod) {
        this.persistPeriod = persistPeriod;
        return this;
    }

    public BrooklynLauncher haHeartbeatTimeout(Duration val) {
        this.haHeartbeatTimeout = val;
        return this;
    }

    /**
     * Controls both the frequency of heartbeats, and the frequency of checking the health of other nodes.
     */
    public BrooklynLauncher haHeartbeatPeriod(Duration val) {
        this.haHeartbeatPeriod = val;
        return this;
    }
    
    /**
     * Starts the web server (with web console) and Brooklyn applications, as per the specifications configured. 
     * @return An object containing details of the web server and the management context.
     */
    public BrooklynLauncher start() {
        if (started) throw new IllegalStateException("Cannot start() or launch() multiple times");
        started = true;
        
        // Create the management context
        if (managementContext == null) {
            if (brooklynProperties == null) {
                Builder builder = new BrooklynProperties.Factory.Builder();
                if (globalBrooklynPropertiesFile != null) builder.globalPropertiesFile(globalBrooklynPropertiesFile);
                if (localBrooklynPropertiesFile != null) builder.localPropertiesFile(localBrooklynPropertiesFile);
                managementContext = new LocalManagementContext(builder, brooklynAdditionalProperties);
            } else {
                managementContext = new LocalManagementContext(brooklynProperties, brooklynAdditionalProperties);
            }
            brooklynProperties = ((ManagementContextInternal)managementContext).getBrooklynProperties();
            
            // We created the management context, so we are responsible for terminating it
            BrooklynShutdownHooks.invokeTerminateOnShutdown(managementContext);
            
        } else if (brooklynProperties == null) {
            brooklynProperties = ((ManagementContextInternal)managementContext).getBrooklynProperties();
            brooklynProperties.addFromMap(brooklynAdditionalProperties);
        }

        // Create the locations
        locations.addAll(managementContext.getLocationRegistry().resolve(locationSpecs));

        // Add a CAMP platform (TODO include a flag for this?)
        campPlatform = new BrooklynCampPlatformLauncherNoServer()
                .useManagementContext(managementContext)
                .launch()
                .getCampPlatform();
        // TODO start CAMP rest _server_ in the below (at /camp) ?
        
        // Start the web-console
        if (startWebApps) {
            startWebApps();
        }
        
        initPersistence();
        createApps();
        startApps();
        
        return this;
    }

    protected void startWebApps() {
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

    protected void initPersistence() {
        // Prepare the rebind directory, and initialise the RebindManager as required
        if (persistMode == PersistMode.DISABLED) {
            LOG.info("Persistence disabled");
        } else {
            if (persistenceDir == null) {
                persistenceDir = new File( BrooklynServerConfig.getPersistenceDir(brooklynProperties) );
            }
            preparePersistenceDir(persistenceDir);
            
            RebindManager rebindManager = managementContext.getRebindManager();
            BrooklynMementoPersister persister = new BrooklynMementoPersisterToMultiFile(persistenceDir, managementContext.getCatalog().getRootClassLoader());
            ((RebindManagerImpl)rebindManager).setPeriodicPersistPeriod(persistPeriod);
            rebindManager.setPersister(persister);
        }
        
        // Initialise the HA manager as required
        if (highAvailabilityMode == HighAvailabilityMode.DISABLED) {
            LOG.info("High availability disabled");
        } else {
            File haDir = new File(persistenceDir, "plane");
            
            HighAvailabilityManager haManager = managementContext.getHighAvailabilityManager();
            ManagementPlaneSyncRecordPersister persister = new ManagementPlaneSyncRecordPersisterToMultiFile(
                    haDir, 
                    managementContext.getCatalog().getRootClassLoader(), 
                    managementContext.getManagementNodeId());
            ((HighAvailabilityManagerImpl)haManager).setHeartbeatTimeout(haHeartbeatTimeout);
            ((HighAvailabilityManagerImpl)haManager).setPollPeriod(haHeartbeatPeriod);
            haManager.setPersister(persister);
        }
        
        // Now start the HA Manager and the Rebind manager, as required
        if (highAvailabilityMode == HighAvailabilityMode.DISABLED) {
            HighAvailabilityManager haManager = managementContext.getHighAvailabilityManager();
            haManager.disabled();

            if (persistMode != PersistMode.DISABLED) {
                boolean rebinding;
                switch (persistMode) {
                    case CLEAN:
                        rebinding = false;
                        break;
                    case REBIND:
                        rebinding = true;
                        break;
                    case AUTO:
                        rebinding = (persistenceDir.exists() && !isMementoDirEmpty(persistenceDir));
                        break;
                    default:
                        throw new FatalConfigurationRuntimeException("Unexpected persist mode "+persistMode+"; modified during initialization?!");
                };
                
                RebindManager rebindManager = managementContext.getRebindManager();
                if (rebinding) {
                    LOG.info("Management node (no high availability) rebinding to entities using "+persistenceDir.getAbsolutePath());

                    ClassLoader classLoader = managementContext.getCatalog().getRootClassLoader();
                    try {
                        rebindManager.rebind(classLoader);
                    } catch (IOException e) {
                        LOG.warn("Management node (no high availability) failed to rebind (rethrowing)");
                        throw Exceptions.propagate(e);
                    }
                } else {
                    LOG.info("Management node (no high availability) starting, no existing entities to rebind in "+persistenceDir.getAbsolutePath());
                }
                rebindManager.start();
            }
        } else {
            // Let the HA manager decide when rebind needs to be called (based on whether other nodes in plane
            // are already running).
            
            HighAvailabilityMode startMode;
            switch (highAvailabilityMode) {
                case AUTO:
                case MASTER:
                case STANDBY:
                    startMode = highAvailabilityMode;
                    break;
                case DISABLED:
                    throw new IllegalStateException("Unexpected code-branch for high availability mode "+highAvailabilityMode);
                default:       
                    throw new IllegalStateException("Unexpected high availability mode "+highAvailabilityMode);
            }
            
            LOG.info("Management node (with high availability) starting");
            HighAvailabilityManager haManager = managementContext.getHighAvailabilityManager();
            haManager.start(startMode);
        }
    }

    /**
     * Prepares the persistence directory for use (e.g. backing up old dir, checking is non-empty 
     * or deleting as required, etc).
     */
    protected void preparePersistenceDir(File dir) {
        try {
            String persistencePath = dir.getAbsolutePath();
            
            switch (persistMode) {
                case CLEAN:
                    if (dir.exists()) {
                        checkPersistenceDirAccessible(dir);
                        try {
                            File old = moveDirectory(dir);
                            LOG.info("Persist-clean using "+persistencePath+"; moved old directory to "+old.getAbsolutePath());
                        } catch (IOException e) {
                            throw new FatalConfigurationRuntimeException("Error moving old persistence directory "+dir.getAbsolutePath(), e);
                        }
                    } else {
                        LOG.info("Persist-clean using "+persistencePath+"; no pre-existing persisted data");
                    }
                    break;
                case REBIND:
                    checkPersistenceDirAccessible(dir);
                    checkPersistenceDirNonEmpty(dir);
                    try {
                        File backup = backupDirectory(dir);
                        LOG.info("Persist-rebind using "+persistencePath+"; backed up directory to "+backup.getAbsolutePath());
                    } catch (IOException e) {
                        throw new FatalConfigurationRuntimeException("Error backing up persistence directory "+dir.getAbsolutePath(), e);
                    }
                    break;
                case AUTO:
                    if (dir.exists()) {
                        checkPersistenceDirAccessible(dir);
                    }
                    if (dir.exists() && !isMementoDirEmpty(dir)) {
                        try {
                            File backup = backupDirectory(dir);
                            LOG.info("Persist-auto will rebind using "+persistencePath+"; backed up directory to "+backup.getAbsolutePath());
                        } catch (IOException e) {
                            throw new FatalConfigurationRuntimeException("Error backing up persistence directory "+dir.getAbsolutePath(), e);
                        }
                    } else {
                        LOG.info("Persist-auto using fresh "+persistencePath+"; no pre-existing persisted data");
                    }
                    break;
                default:
                    throw new FatalConfigurationRuntimeException("Unexpected persist mode "+persistMode+"; modified during initialization?!");
            };
            
            if (!dir.exists()) {
                boolean success = dir.mkdirs();
                if (!success) {
                    throw new FatalConfigurationRuntimeException("Failed to create persistence directory "+dir);
                }
            }

        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }

    protected void checkPersistenceDirAccessible(File persistenceDir) {
        if (!(persistenceDir.exists() && persistenceDir.isDirectory() && persistenceDir.canRead() && persistenceDir.canWrite())) {
            throw new FatalConfigurationRuntimeException("Invalid persistence directory "+persistenceDir+" because "+
                    (!persistenceDir.exists() ? "does not exist" :
                        (!persistenceDir.isDirectory() ? "not a directory" :
                            (!persistenceDir.canRead() ? "not readable" :
                                (!persistenceDir.canWrite() ? "not writable" : "unknown reason")))));
        }
    }
    
    protected void checkPersistenceDirNonEmpty(File persistenceDir) {
        if (isMementoDirEmpty(persistenceDir)) {
            throw new FatalConfigurationRuntimeException("Invalid persistence directory "+persistenceDir+" because directory is empty");
        }
    }

    protected void createApps() {
        for (ApplicationBuilder appBuilder : appBuildersToManage) {
            StartableApplication app = appBuilder.manage(managementContext);
            apps.add(app);
        }
        for (Application app : appsToManage) {
            Entities.startManagement(app, managementContext);
            apps.add(app);
        }
        for (String blueprint : yamlAppsToManage) {
            Application app = getAppFromYaml(blueprint);
            // Note: BrooklynAssemblyTemplateInstantiator automatically puts applications under management.
            apps.add(app);
        }
    }

    protected Application getAppFromYaml(String input) {
        AssemblyTemplate at = campPlatform.pdp()
                .registerDeploymentPlan(new StringReader(input));
         BrooklynAssemblyTemplateInstantiator instantiator;
        try {
            AssemblyTemplateInstantiator ati = at.getInstantiator().newInstance();
            if (ati instanceof BrooklynAssemblyTemplateInstantiator) {
                instantiator = BrooklynAssemblyTemplateInstantiator.class.cast(ati);
            } else {
                throw new IllegalStateException("Cannot create application with instantiator: " + ati);
            }
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
        return instantiator.create(at, campPlatform);
    }
    
    protected void startApps() {
        List<Throwable> appExceptions = Lists.newArrayList();
        for (Application app : apps) {
            if (app instanceof Startable) {
                if (shutdownOnExit) BrooklynShutdownHooks.invokeStopOnShutdown(app);
                try {
                    LOG.info("Starting brooklyn application {} in location{} {}", new Object[] { app, locations.size()!=1?"s":"", locations });
                    ((Startable)app).start(locations);
                } catch (Exception e) {
                    LOG.error("Error starting "+app+": "+Exceptions.collapseText(e), Exceptions.getFirstInteresting(e));
                    appExceptions.add(Exceptions.collapse(e));
                    
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

        // TODO Do we want to do this as part of managementContext.terminate, so after other threads are terminated etc?
        // Otherwise the app can change between this persist and the terminate.
        if (persistMode != PersistMode.DISABLED) {
            try {
                Stopwatch stopwatch = Stopwatch.createStarted();
                managementContext.getRebindManager().waitForPendingComplete(Duration.TEN_SECONDS);
                LOG.info("Finished waiting for persist; took "+Time.makeTimeStringRounded(stopwatch));
            } catch (RuntimeInterruptedException e) {
                Thread.currentThread().interrupt(); // keep going with shutdown
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // keep going with shutdown
            } catch (TimeoutException e) {
                LOG.warn("Timeout after 10 seconds waiting for persistance to write all data; continuing");
            }
        }
        
        if (managementContext instanceof ManagementContextInternal) {
            ((ManagementContextInternal)managementContext).terminate();
        }
        
        for (Location loc : locations) {
            if (loc instanceof Closeable) {
                Streams.closeQuietly((Closeable)loc);
            }
        }
    }
    
    static File backupDirectory(File dir) throws IOException, InterruptedException {
        File parentDir = dir.getParentFile();
        String simpleName = dir.getName();
        String timestamp = new SimpleDateFormat("yyyy-MM-dd-hhmm-ss").format(new Date());
        File backupDir = new File(parentDir, simpleName+"-"+timestamp+".bak");
        
        String cmd = "cp -R "+dir.getAbsolutePath()+" "+backupDir.getAbsolutePath();
        Process proc = Runtime.getRuntime().exec(cmd);
        proc.waitFor();
        if (proc.exitValue() != 0) {
            throw new IOException("Error backing up directory, with command "+cmd);
        }
        return backupDir;
    }

    static File moveDirectory(File dir) throws InterruptedException, IOException {
        File parentDir = dir.getParentFile();
        String simpleName = dir.getName();
        String timestamp = new SimpleDateFormat("yyyy-MM-dd-hhmm-ss").format(new Date());
        File newDir = new File(parentDir, simpleName+"-"+timestamp+".old");
        
        String cmd = "mv  "+dir.getAbsolutePath()+" "+newDir.getAbsolutePath();
        Process proc = Runtime.getRuntime().exec(cmd);
        proc.waitFor();
        if (proc.exitValue() != 0) {
            throw new IOException("Error moving directory, with command "+cmd);
        }
        return newDir;
    }

    /**
     * Empty if directory is entirely empty, or only contains empty directories.
     */
    static boolean isMementoDirEmpty(File dir) {
        if (!dir.exists()) return false;
        for (File sub : dir.listFiles()) {
            if (sub.isFile()) return false;
            if (sub.isDirectory() && sub.listFiles().length > 0) return false;
        }
        return true;
    }
}
