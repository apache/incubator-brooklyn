/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package brooklyn.launcher;

import static com.google.common.base.Preconditions.checkNotNull;
import io.brooklyn.camp.CampPlatform;
import io.brooklyn.camp.brooklyn.BrooklynCampPlatformLauncherNoServer;
import io.brooklyn.camp.brooklyn.spi.creation.BrooklynAssemblyTemplateInstantiator;
import io.brooklyn.camp.spi.AssemblyTemplate;
import io.brooklyn.camp.spi.instantiate.AssemblyTemplateInstantiator;

import java.io.Closeable;
import java.io.File;
import java.io.StringReader;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.catalog.CatalogLoadMode;
import brooklyn.config.BrooklynProperties;
import brooklyn.config.BrooklynServerConfig;
import brooklyn.config.BrooklynServiceAttributes;
import brooklyn.config.ConfigKey;
import brooklyn.config.ConfigPredicates;
import brooklyn.entity.Application;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.BrooklynShutdownHooks;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.SoftwareProcess;
import brooklyn.entity.basic.StartableApplication;
import brooklyn.entity.brooklynnode.BrooklynNode;
import brooklyn.entity.brooklynnode.LocalBrooklynNode;
import brooklyn.entity.proxying.EntitySpec;
import brooklyn.entity.rebind.PersistenceExceptionHandler;
import brooklyn.entity.rebind.PersistenceExceptionHandlerImpl;
import brooklyn.entity.rebind.RebindManager;
import brooklyn.entity.rebind.RebindManagerImpl;
import brooklyn.entity.rebind.persister.BrooklynMementoPersisterToObjectStore;
import brooklyn.entity.rebind.persister.BrooklynPersistenceUtils;
import brooklyn.entity.rebind.persister.PersistMode;
import brooklyn.entity.rebind.persister.PersistenceObjectStore;
import brooklyn.entity.rebind.transformer.CompoundTransformer;
import brooklyn.entity.trait.Startable;
import brooklyn.internal.BrooklynFeatureEnablement;
import brooklyn.launcher.config.StopWhichAppsOnShutdown;
import brooklyn.location.Location;
import brooklyn.location.LocationSpec;
import brooklyn.location.PortRange;
import brooklyn.location.basic.LocalhostMachineProvisioningLocation.LocalhostMachine;
import brooklyn.location.basic.PortRanges;
import brooklyn.management.ManagementContext;
import brooklyn.management.ha.HighAvailabilityManager;
import brooklyn.management.ha.HighAvailabilityManagerImpl;
import brooklyn.management.ha.HighAvailabilityMode;
import brooklyn.management.ha.ManagementNodeState;
import brooklyn.management.ha.ManagementPlaneSyncRecord;
import brooklyn.management.ha.ManagementPlaneSyncRecordPersister;
import brooklyn.management.ha.ManagementPlaneSyncRecordPersisterToObjectStore;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.management.internal.ManagementContextInternal;
import brooklyn.mementos.BrooklynMementoRawData;
import brooklyn.rest.BrooklynWebConfig;
import brooklyn.rest.filter.BrooklynPropertiesSecurityFilter;
import brooklyn.rest.security.provider.BrooklynUserWithRandomPasswordSecurityProvider;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.exceptions.FatalConfigurationRuntimeException;
import brooklyn.util.exceptions.FatalRuntimeException;
import brooklyn.util.exceptions.RuntimeInterruptedException;
import brooklyn.util.guava.Maybe;
import brooklyn.util.io.FileUtil;
import brooklyn.util.net.Networking;
import brooklyn.util.os.Os;
import brooklyn.util.stream.Streams;
import brooklyn.util.text.Strings;
import brooklyn.util.time.Duration;
import brooklyn.util.time.Time;

import com.google.common.base.Splitter;
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
    private boolean startBrooklynNode = false;
    private PortRange port = null;
    private InetAddress bindAddress = null;
    private InetAddress publicAddress = null;
    private Map<String,String> webApps = new LinkedHashMap<String,String>();
    private Map<String, ?> webconsoleFlags = Maps.newLinkedHashMap();
    private Boolean skipSecurityFilter = null;
    
    private boolean ignorePersistenceErrors = false;
    private boolean ignoreWebErrors = false;
    private boolean ignoreAppErrors = false;
    
    private StopWhichAppsOnShutdown stopWhichAppsOnShutdown = StopWhichAppsOnShutdown.THESE_IF_NOT_PERSISTED;
    
    private PersistMode persistMode = PersistMode.DISABLED;
    private HighAvailabilityMode highAvailabilityMode = HighAvailabilityMode.DISABLED;
    private String persistenceDir;
    private String persistenceLocation;
    private Duration persistPeriod = Duration.ONE_SECOND;
    private Duration haHeartbeatTimeout = Duration.THIRTY_SECONDS;
    private Duration haHeartbeatPeriod = Duration.ONE_SECOND;
    
    private volatile BrooklynWebServer webServer;
    private CampPlatform campPlatform;

    private boolean started;
    private String globalBrooklynPropertiesFile = Os.mergePaths(Os.home(), ".brooklyn", "brooklyn.properties");
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

    public BrooklynLauncher persistenceLocation(@Nullable String persistenceLocationSpec) {
        persistenceLocation = persistenceLocationSpec;
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
     * As {@link #webconsolePort(PortRange)} taking a single port
     */ 
    public BrooklynLauncher webconsolePort(int port) {
        return webconsolePort(PortRanges.fromInteger(port));
    }

    /**
     * As {@link #webconsolePort(PortRange)} taking a string range
     */
    public BrooklynLauncher webconsolePort(String port) {
        return webconsolePort(PortRanges.fromString(port));
    }

    /**
     * Specifies the port where the web console (and any additional webapps specified) will listen;
     * default "8081+" (or "8443+" for https) being the first available >= 8081.
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
     * Specifies the address that the management context's REST API will be available on. Defaults
     * to {@link #bindAddress} if it is not 0.0.0.0.
     * @see #bindAddress(java.net.InetAddress)
     */
    public BrooklynLauncher publicAddress(InetAddress publicAddress) {
        this.publicAddress = publicAddress;
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

    public BrooklynLauncher ignorePersistenceErrors(boolean ignorePersistenceErrors) {
        this.ignorePersistenceErrors = ignorePersistenceErrors;
        return this;
    }

    public BrooklynLauncher ignoreWebErrors(boolean ignoreWebErrors) {
        this.ignoreWebErrors = ignoreWebErrors;
        return this;
    }

    public BrooklynLauncher ignoreAppErrors(boolean ignoreAppErrors) {
        this.ignoreAppErrors = ignoreAppErrors;
        return this;
    }

    public BrooklynLauncher stopWhichAppsOnShutdown(StopWhichAppsOnShutdown stopWhich) {
        this.stopWhichAppsOnShutdown = stopWhich;
        return this;
    }

    public BrooklynLauncher shutdownOnExit(boolean val) {
        LOG.warn("Call to deprecated `shutdownOnExit`", new Throwable("source of deprecated call"));
        stopWhichAppsOnShutdown = StopWhichAppsOnShutdown.THESE_IF_NOT_PERSISTED;
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

    public BrooklynLauncher persistenceDir(@Nullable String persistenceDir) {
        this.persistenceDir = persistenceDir;
        return this;
    }

    public BrooklynLauncher persistenceDir(@Nullable File persistenceDir) {
        if (persistenceDir==null) return persistenceDir((String)null);
        return persistenceDir(persistenceDir.getAbsolutePath());
    }

    public BrooklynLauncher persistPeriod(Duration persistPeriod) {
        this.persistPeriod = persistPeriod;
        return this;
    }

    public BrooklynLauncher haHeartbeatTimeout(Duration val) {
        this.haHeartbeatTimeout = val;
        return this;
    }

    public BrooklynLauncher startBrooklynNode(boolean val) {
        this.startBrooklynNode = val;
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
     * @param destinationDir Directory for state to be copied to
     */
    public void copyPersistedState(String destinationDir) {
        copyPersistedState(destinationDir, null, null);
    }

    /**
     * @param destinationDir Directory for state to be copied to
     * @param destinationLocation Optional location if target for copied state is a blob store.
     */
    public void copyPersistedState(String destinationDir, @Nullable String destinationLocation) {
        copyPersistedState(destinationDir, destinationLocation, null);
    }

    /**
     * @param destinationDir Directory for state to be copied to
     * @param destinationLocationSpec Optional location if target for copied state is a blob store.
     * @param transformer Optional transformations to apply to retrieved state before it is copied.
     */
    public void copyPersistedState(String destinationDir, @Nullable String destinationLocationSpec, @Nullable CompoundTransformer transformer) {
        initManagementContext();
        try {
            highAvailabilityMode = HighAvailabilityMode.HOT_STANDBY;
            initPersistence();
        } catch (Exception e) {
            handleSubsystemStartupError(ignorePersistenceErrors, "persistence", e);
        }
        
        try {
            BrooklynMementoRawData memento = managementContext.getRebindManager().retrieveMementoRawData();
            if (transformer != null) memento = transformer.transform(memento);
            
            ManagementPlaneSyncRecord planeState = managementContext.getHighAvailabilityManager().getManagementPlaneSyncState();
            
            LOG.info("Persisting state to "+destinationDir+(destinationLocationSpec!=null ? " @ "+destinationLocationSpec : ""));
            PersistenceObjectStore destinationObjectStore = BrooklynPersistenceUtils.newPersistenceObjectStore(
                managementContext, destinationLocationSpec, destinationDir);
            BrooklynPersistenceUtils.writeMemento(managementContext, memento, destinationObjectStore);
            BrooklynPersistenceUtils.writeManagerMemento(managementContext, planeState, destinationObjectStore);

        } catch (Exception e) {
            Exceptions.propagateIfFatal(e);
            LOG.debug("Error copying persisted state (rethrowing): " + e, e);
            throw new FatalRuntimeException("Error copying persisted state: " +
                Exceptions.collapseText(e), e);
        }
    }

    /** @deprecated since 0.7.0 use {@link #copyPersistedState} instead */
    // Make private after deprecation
    @Deprecated
    public BrooklynMementoRawData retrieveState() {
        initManagementContext();
        initPersistence();
        return managementContext.getRebindManager().retrieveMementoRawData();
    }

    /**
     * @param memento The state to copy
     * @param destinationDir Directory for state to be copied to
     * @param destinationLocation Optional location if target for copied state is a blob store.
     * @deprecated since 0.7.0 use {@link #copyPersistedState} instead
     */
    // Make private after deprecation
    @Deprecated
    public void persistState(BrooklynMementoRawData memento, String destinationDir, @Nullable String destinationLocationSpec) {
        initManagementContext();
        PersistenceObjectStore destinationObjectStore = BrooklynPersistenceUtils.newPersistenceObjectStore(
            managementContext, destinationLocationSpec, destinationDir);
        BrooklynPersistenceUtils.writeMemento(managementContext, memento, destinationObjectStore);
    }

    /**
     * Starts the web server (with web console) and Brooklyn applications, as per the specifications configured. 
     * @return An object containing details of the web server and the management context.
     */
    public BrooklynLauncher start() {
        if (started) throw new IllegalStateException("Cannot start() or launch() multiple times");
        started = true;

        setCatalogLoadMode();

        // Create the management context
        initManagementContext();

        // Add a CAMP platform
        campPlatform = new BrooklynCampPlatformLauncherNoServer()
                .useManagementContext(managementContext)
                .launch()
                .getCampPlatform();
        // TODO start CAMP rest _server_ in the below (at /camp) ?
        
        try {
            initPersistence();
            startPersistence();
        } catch (Exception e) {
            handleSubsystemStartupError(ignorePersistenceErrors, "persistence", e);
        }

        // Create the locations. Must happen after persistence is started in case the
        // management context's catalog is loaded from persisted state. (Location
        // resolution uses the catalog's classpath to scan for resolvers.)
        locations.addAll(managementContext.getLocationRegistry().resolve(locationSpecs));

        // Start the web-console
        if (startWebApps) {
            try {
                startWebApps();
            } catch (Exception e) {
                handleSubsystemStartupError(ignoreWebErrors, "web apps", e);
            }
        }

        try {
            createApps();
            startApps();
        } catch (Exception e) {
            handleSubsystemStartupError(ignoreAppErrors, "managed apps", e);
        }

        if (startBrooklynNode) {
            try {
                startBrooklynNode();
            } catch (Exception e) {
                handleSubsystemStartupError(ignoreWebErrors, "web apps", e);
            }
        }

        return this;
    }

    /**
     * Sets {@link BrooklynServerConfig#CATALOG_LOAD_MODE} in {@link #brooklynAdditionalProperties}.
     * <p>
     * Checks {@link brooklyn.internal.BrooklynFeatureEnablement#FEATURE_CATALOG_PERSISTENCE_PROPERTY}
     * and the {@link #persistMode persistence mode}.
     */
    private void setCatalogLoadMode() {
        CatalogLoadMode catalogLoadMode;
        if (!BrooklynFeatureEnablement.isEnabled(BrooklynFeatureEnablement.FEATURE_CATALOG_PERSISTENCE_PROPERTY)) {
            catalogLoadMode = CatalogLoadMode.LOAD_BROOKLYN_CATALOG_URL;
        } else {
            catalogLoadMode = CatalogLoadMode.forPersistMode(persistMode);
        }
        brooklynProperties(BrooklynServerConfig.CATALOG_LOAD_MODE, catalogLoadMode);
    }

    private void initManagementContext() {
        // Create the management context
        if (managementContext == null) {
            if (brooklynProperties == null) {
                BrooklynProperties.Factory.Builder builder = BrooklynProperties.Factory.builderDefault();
                if (globalBrooklynPropertiesFile != null) {
                    if (fileExists(globalBrooklynPropertiesFile)) {
                        LOG.debug("Using global properties file "+globalBrooklynPropertiesFile);
                        // brooklyn.properties stores passwords (web-console and cloud credentials), 
                        // so ensure it has sensible permissions
                        checkFileReadable(globalBrooklynPropertiesFile);
                        checkFilePermissionsX00(globalBrooklynPropertiesFile);
                    } else {
                        LOG.debug("Global properties file "+globalBrooklynPropertiesFile+" does not exist, will ignore");
                    }
                    builder.globalPropertiesFile(globalBrooklynPropertiesFile);
                } else {
                    LOG.debug("Global properties file disabled");
                    builder.globalPropertiesFile(null);
                }
                
                if (localBrooklynPropertiesFile != null) {
                    checkFileReadable(localBrooklynPropertiesFile);
                    checkFilePermissionsX00(localBrooklynPropertiesFile);
                    builder.localPropertiesFile(localBrooklynPropertiesFile);
                }
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
    }

    private boolean fileExists(String file) {
        return new File(Os.tidyPath(file)).exists();
    }

    private void checkFileReadable(String file) {
        File f = new File(Os.tidyPath(file));
        if (!f.exists()) {
            throw new FatalRuntimeException("File "+file+" does not exist");
        }
        if (!f.isFile()) {
            throw new FatalRuntimeException(file+" is not a file");
        }
        if (!f.canRead()) {
            throw new FatalRuntimeException(file+" is not readable");
        }
    }
    
    private void checkFilePermissionsX00(String file) {
        File f = new File(Os.tidyPath(file));
        
        Maybe<String> permission = FileUtil.getFilePermissions(f);
        if (permission.isAbsent()) {
            LOG.debug("Could not determine permissions of file; assuming ok: "+f);
        } else {
            if (!permission.get().subSequence(4, 10).equals("------")) {
                throw new FatalRuntimeException("Invalid permissions for file "+file+"; expected ?00 but was "+permission.get());
            }
        }
    }
    
    private void handleSubsystemStartupError(boolean ignoreSuchErrors, String system, Exception e) {
        Exceptions.propagateIfFatal(e);
        if (ignoreSuchErrors) {
            LOG.error("Subsystem for "+system+" had startup error (continuing with startup): "+e, e);
        } else {
            throw Exceptions.propagate(e);
        }
    }

    protected void startWebApps() {
        // No security options in properties and no command line options overriding.
        if (Boolean.TRUE.equals(skipSecurityFilter) && bindAddress==null) {
            LOG.info("Starting Brooklyn web-console on loopback because security is explicitly disabled and no bind address specified");
            bindAddress = Networking.LOOPBACK;
        } else if (BrooklynWebConfig.hasNoSecurityOptions(brooklynProperties)) {
            if (bindAddress==null) {
                LOG.info("Starting Brooklyn web-console with passwordless access on localhost and protected access from any other interfaces (no bind address specified)");
            } else {
                if (Arrays.equals(new byte[] { 127, 0, 0, 1 }, bindAddress.getAddress())) { 
                    LOG.info("Starting Brooklyn web-console with passwordless access on localhost");
                } else if (Arrays.equals(new byte[] { 0, 0, 0, 0 }, bindAddress.getAddress())) { 
                    LOG.info("Starting Brooklyn web-console with passwordless access on localhost and random password (logged) required from any other interfaces");
                } else { 
                    LOG.info("Starting Brooklyn web-console with passwordless access on localhost (if permitted) and random password (logged) required from any other interfaces");
                }
            }
            brooklynProperties.put(
                    BrooklynWebConfig.SECURITY_PROVIDER_CLASSNAME,
                    BrooklynUserWithRandomPasswordSecurityProvider.class.getName());
        } else {
            LOG.debug("Starting Brooklyn using security properties: "+brooklynProperties.submap(ConfigPredicates.startingWith(BrooklynWebConfig.BASE_NAME_SECURITY)).asMapWithStringKeys());
        }
        if (bindAddress == null) bindAddress = Networking.ANY_NIC;

        LOG.debug("Starting Brooklyn web-console with bindAddress "+bindAddress+" and properties "+brooklynProperties);
        try {
            webServer = new BrooklynWebServer(webconsoleFlags, managementContext);
            webServer.setBindAddress(bindAddress);
            webServer.setPublicAddress(publicAddress);
            webServer.setPort(port);
            webServer.putAttributes(brooklynProperties);
            if (skipSecurityFilter != Boolean.TRUE) {
                webServer.setSecurityFilter(BrooklynPropertiesSecurityFilter.class);
            }
            for (Map.Entry<String, String> webapp : webApps.entrySet()) {
                webServer.addWar(webapp.getKey(), webapp.getValue());
            }
            webServer.start();

        } catch (Exception e) {
            LOG.warn("Failed to start Brooklyn web-console (rethrowing): " + Exceptions.collapseText(e));
            throw new FatalRuntimeException("Failed to start Brooklyn web-console: " + Exceptions.collapseText(e), e);
        }
    }

    protected void initPersistence() {
        // Prepare the rebind directory, and initialise the RebindManager as required
        final PersistenceObjectStore objectStore;
        if (persistMode == PersistMode.DISABLED) {
            LOG.info("Persistence disabled");
            objectStore = null;
            
        } else {
            try {
                if (persistenceLocation == null) {
                    persistenceLocation = brooklynProperties.getConfig(BrooklynServerConfig.PERSISTENCE_LOCATION_SPEC);
                }
                persistenceDir = BrooklynServerConfig.resolvePersistencePath(persistenceDir, brooklynProperties, persistenceLocation);
                objectStore = BrooklynPersistenceUtils.newPersistenceObjectStore(managementContext, persistenceLocation, persistenceDir, 
                    persistMode, highAvailabilityMode);
                    
                RebindManager rebindManager = managementContext.getRebindManager();
                
                BrooklynMementoPersisterToObjectStore persister = new BrooklynMementoPersisterToObjectStore(
                    objectStore,
                    ((ManagementContextInternal)managementContext).getBrooklynProperties(),
                    managementContext.getCatalog().getRootClassLoader());
                PersistenceExceptionHandler persistenceExceptionHandler = PersistenceExceptionHandlerImpl.builder().build();
                ((RebindManagerImpl) rebindManager).setPeriodicPersistPeriod(persistPeriod);
                rebindManager.setPersister(persister, persistenceExceptionHandler);
            } catch (FatalConfigurationRuntimeException e) {
                throw e;
            } catch (Exception e) {
                Exceptions.propagateIfFatal(e);
                LOG.debug("Error initializing persistence subsystem (rethrowing): "+e, e);
                throw new FatalRuntimeException("Error initializing persistence subsystem: "+
                    Exceptions.collapseText(e), e);
            }
        }
        
        // Initialise the HA manager as required
        if (highAvailabilityMode == HighAvailabilityMode.DISABLED) {
            LOG.info("High availability disabled");
        } else {
            if (objectStore==null)
                throw new FatalConfigurationRuntimeException("Cannot run in HA mode when no persistence configured.");

            HighAvailabilityManager haManager = managementContext.getHighAvailabilityManager();
            ManagementPlaneSyncRecordPersister persister =
                new ManagementPlaneSyncRecordPersisterToObjectStore(managementContext,
                    objectStore, managementContext.getCatalog().getRootClassLoader());
            ((HighAvailabilityManagerImpl)haManager).setHeartbeatTimeout(haHeartbeatTimeout);
            ((HighAvailabilityManagerImpl)haManager).setPollPeriod(haHeartbeatPeriod);
            haManager.setPersister(persister);
        }
    }
    
    protected void startPersistence() {
        // Now start the HA Manager and the Rebind manager, as required
        if (highAvailabilityMode == HighAvailabilityMode.DISABLED) {
            HighAvailabilityManager haManager = managementContext.getHighAvailabilityManager();
            haManager.disabled();

            if (persistMode != PersistMode.DISABLED) {
                startPersistenceWithoutHA();
            }
            
        } else {
            // Let the HA manager decide when objectstore.prepare and rebindmgr.rebind need to be called 
            // (based on whether other nodes in plane are already running).
            
            HighAvailabilityMode startMode;
            switch (highAvailabilityMode) {
                case AUTO:
                case MASTER:
                case STANDBY:
                case HOT_STANDBY:
                    startMode = highAvailabilityMode;
                    break;
                case DISABLED:
                    throw new IllegalStateException("Unexpected code-branch for high availability mode "+highAvailabilityMode);
                default:       
                    throw new IllegalStateException("Unexpected high availability mode "+highAvailabilityMode);
            }
            
            LOG.debug("Management node (with HA) starting");
            HighAvailabilityManager haManager = managementContext.getHighAvailabilityManager();
            // prepare after HA mode is known, to prevent backups happening in standby mode
            haManager.start(startMode);
        }
    }

    private void startPersistenceWithoutHA() {
        RebindManager rebindManager = managementContext.getRebindManager();
        if (Strings.isNonBlank(persistenceLocation))
            LOG.info("Management node (no HA) rebinding to entities at "+persistenceLocation+" in "+persistenceDir);
        else
            LOG.info("Management node (no HA) rebinding to entities on file system in "+persistenceDir);

        ClassLoader classLoader = managementContext.getCatalog().getRootClassLoader();
        try {
            rebindManager.rebind(classLoader, null, ManagementNodeState.MASTER);
        } catch (Exception e) {
            Exceptions.propagateIfFatal(e);
            LOG.debug("Error rebinding to persisted state (rethrowing): "+e, e);
            throw new FatalRuntimeException("Error rebinding to persisted state: "+
                Exceptions.collapseText(e), e);
        }
        rebindManager.startPersistence();
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

    protected void startBrooklynNode() {
        final String classpath = System.getenv("INITIAL_CLASSPATH");
        if (Strings.isBlank(classpath)) {
            LOG.warn("Cannot find INITIAL_CLASSPATH environment variable, skipping BrooklynNode entity creation");
            return;
        }
        if (webServer == null || !startWebApps) {
            LOG.info("Skipping BrooklynNode entity creation, BrooklynWebServer not running");
            return;
        }
        ApplicationBuilder brooklyn = new ApplicationBuilder() {
            @SuppressWarnings("deprecation")
            @Override
            protected void doBuild() {
                addChild(EntitySpec.create(LocalBrooklynNode.class)
                        .configure(SoftwareProcess.ENTITY_STARTED, true)
                        .configure(SoftwareProcess.RUN_DIR, System.getenv("ROOT"))
                        .configure(SoftwareProcess.INSTALL_DIR, System.getenv("BROOKLYN_HOME"))
                        .configure(BrooklynNode.ENABLED_HTTP_PROTOCOLS, ImmutableList.of(webServer.getHttpsEnabled() ? "https" : "http"))
                        .configure(webServer.getHttpsEnabled() ? BrooklynNode.HTTPS_PORT : BrooklynNode.HTTP_PORT, PortRanges.fromInteger(webServer.getActualPort()))
                        .configure(BrooklynNode.WEB_CONSOLE_BIND_ADDRESS, bindAddress)
                        .configure(BrooklynNode.WEB_CONSOLE_PUBLIC_ADDRESS, publicAddress)
                        .configure(BrooklynNode.CLASSPATH, Splitter.on(":").splitToList(classpath))
                        .configure(BrooklynNode.NO_WEB_CONSOLE_AUTHENTICATION, Boolean.TRUE.equals(skipSecurityFilter))
                        .configure(BrooklynNode.NO_SHUTDOWN_ON_EXIT, stopWhichAppsOnShutdown == StopWhichAppsOnShutdown.NONE)
                        .displayName("Brooklyn Console"));
            }
        };
        LocationSpec<?> spec = LocationSpec.create(LocalhostMachine.class).displayName("Local Brooklyn");
        Location localhost = managementContext.getLocationManager().createLocation(spec);
        brooklyn.appDisplayName("Brooklyn")
                .manage(managementContext)
                .start(ImmutableList.of(localhost));
    }

    protected Application getAppFromYaml(String input) {
        AssemblyTemplate at = campPlatform.pdp().registerDeploymentPlan(new StringReader(input));
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
        Application app = instantiator.create(at, campPlatform);
        return app;
    }
    
    protected void startApps() {
        if ((stopWhichAppsOnShutdown==StopWhichAppsOnShutdown.ALL) || 
            (stopWhichAppsOnShutdown==StopWhichAppsOnShutdown.ALL_IF_NOT_PERSISTED && persistMode==PersistMode.DISABLED)) {
            BrooklynShutdownHooks.invokeStopAppsOnShutdown(managementContext);
        }

        List<Throwable> appExceptions = Lists.newArrayList();
        for (Application app : apps) {
            if (app instanceof Startable) {
                
                if ((stopWhichAppsOnShutdown==StopWhichAppsOnShutdown.THESE) || 
                    (stopWhichAppsOnShutdown==StopWhichAppsOnShutdown.THESE_IF_NOT_PERSISTED && persistMode==PersistMode.DISABLED)) {
                    BrooklynShutdownHooks.invokeStopOnShutdown(app);
                }
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
        if (!appExceptions.isEmpty()) {
            Throwable t = Exceptions.create(appExceptions);
            throw new FatalRuntimeException("Error starting applications: "+Exceptions.collapseText(t), t);
        }
    }
    
    public boolean isStarted() {
        return started;
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
                if (managementContext.getHighAvailabilityManager().getPersister() != null) {
                    managementContext.getHighAvailabilityManager().getPersister().waitForWritesCompleted(Duration.TEN_SECONDS);
                }
                managementContext.getRebindManager().waitForPendingComplete(Duration.TEN_SECONDS);
                LOG.info("Finished waiting for persist; took "+Time.makeTimeStringRounded(stopwatch));
            } catch (RuntimeInterruptedException e) {
                Thread.currentThread().interrupt(); // keep going with shutdown
                LOG.warn("Persistence interrupted during shutdown: "+e, e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // keep going with shutdown
                LOG.warn("Persistence interrupted during shutdown: "+e, e);
            } catch (TimeoutException e) {
                LOG.warn("Timeout after 10 seconds waiting for persistence to write all data; continuing");
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
    
}
