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
package org.apache.brooklyn.launcher;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import javax.annotation.Nullable;

import org.apache.brooklyn.api.entity.Application;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.location.LocationSpec;
import org.apache.brooklyn.api.location.PortRange;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.api.mgmt.ha.HighAvailabilityManager;
import org.apache.brooklyn.api.mgmt.ha.HighAvailabilityMode;
import org.apache.brooklyn.api.mgmt.ha.ManagementNodeState;
import org.apache.brooklyn.api.mgmt.ha.ManagementPlaneSyncRecord;
import org.apache.brooklyn.api.mgmt.ha.ManagementPlaneSyncRecordPersister;
import org.apache.brooklyn.api.mgmt.rebind.PersistenceExceptionHandler;
import org.apache.brooklyn.api.mgmt.rebind.RebindManager;
import org.apache.brooklyn.api.mgmt.rebind.mementos.BrooklynMementoRawData;
import org.apache.brooklyn.camp.CampPlatform;
import org.apache.brooklyn.camp.brooklyn.BrooklynCampPlatformLauncherNoServer;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.catalog.internal.CatalogInitialization;
import org.apache.brooklyn.core.config.ConfigPredicates;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.StartableApplication;
import org.apache.brooklyn.core.entity.factory.ApplicationBuilder;
import org.apache.brooklyn.core.entity.trait.Startable;
import org.apache.brooklyn.core.internal.BrooklynProperties;
import org.apache.brooklyn.core.location.PortRanges;
import org.apache.brooklyn.core.mgmt.EntityManagementUtils;
import org.apache.brooklyn.core.mgmt.ha.HighAvailabilityManagerImpl;
import org.apache.brooklyn.core.mgmt.ha.ManagementPlaneSyncRecordPersisterToObjectStore;
import org.apache.brooklyn.core.mgmt.internal.BrooklynShutdownHooks;
import org.apache.brooklyn.core.mgmt.internal.LocalManagementContext;
import org.apache.brooklyn.core.mgmt.internal.ManagementContextInternal;
import org.apache.brooklyn.core.mgmt.persist.BrooklynMementoPersisterToObjectStore;
import org.apache.brooklyn.core.mgmt.persist.BrooklynPersistenceUtils;
import org.apache.brooklyn.core.mgmt.persist.PersistMode;
import org.apache.brooklyn.core.mgmt.persist.PersistenceObjectStore;
import org.apache.brooklyn.core.mgmt.rebind.PersistenceExceptionHandlerImpl;
import org.apache.brooklyn.core.mgmt.rebind.RebindManagerImpl;
import org.apache.brooklyn.core.mgmt.rebind.transformer.CompoundTransformer;
import org.apache.brooklyn.core.server.BrooklynServerConfig;
import org.apache.brooklyn.core.server.BrooklynServerPaths;
import org.apache.brooklyn.entity.brooklynnode.BrooklynNode;
import org.apache.brooklyn.entity.brooklynnode.LocalBrooklynNode;
import org.apache.brooklyn.entity.software.base.SoftwareProcess;
import org.apache.brooklyn.launcher.config.StopWhichAppsOnShutdown;
import org.apache.brooklyn.location.localhost.LocalhostMachineProvisioningLocation.LocalhostMachine;
import org.apache.brooklyn.rest.BrooklynWebConfig;
import org.apache.brooklyn.rest.filter.BrooklynPropertiesSecurityFilter;
import org.apache.brooklyn.rest.security.provider.BrooklynUserWithRandomPasswordSecurityProvider;
import org.apache.brooklyn.rest.util.ShutdownHandler;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.exceptions.FatalConfigurationRuntimeException;
import org.apache.brooklyn.util.exceptions.FatalRuntimeException;
import org.apache.brooklyn.util.exceptions.RuntimeInterruptedException;
import org.apache.brooklyn.util.guava.Maybe;
import org.apache.brooklyn.util.io.FileUtil;
import org.apache.brooklyn.util.net.Networking;
import org.apache.brooklyn.util.os.Os;
import org.apache.brooklyn.util.stream.Streams;
import org.apache.brooklyn.util.text.Strings;
import org.apache.brooklyn.util.time.Duration;
import org.apache.brooklyn.util.time.Time;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.Beta;
import com.google.common.base.Function;
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
    private Boolean useHttps = null;
    private InetAddress bindAddress = null;
    private InetAddress publicAddress = null;
    private Map<String,String> webApps = new LinkedHashMap<String,String>();
    private Map<String, ?> webconsoleFlags = Maps.newLinkedHashMap();
    private Boolean skipSecurityFilter = null;
    
    private boolean ignoreWebErrors = false;
    private boolean ignorePersistenceErrors = true;
    private boolean ignoreCatalogErrors = true;
    private boolean ignoreAppErrors = true;
    
    private StopWhichAppsOnShutdown stopWhichAppsOnShutdown = StopWhichAppsOnShutdown.THESE_IF_NOT_PERSISTED;
    private ShutdownHandler shutdownHandler;
    
    private Function<ManagementContext,Void> customizeManagement = null;
    private CatalogInitialization catalogInitialization = null;
    
    private PersistMode persistMode = PersistMode.DISABLED;
    private HighAvailabilityMode highAvailabilityMode = HighAvailabilityMode.DISABLED;
    private String persistenceDir;
    private String persistenceLocation;
    private Duration persistPeriod = Duration.ONE_SECOND;
    // these default values come from config in HighAvailablilityManagerImpl
    private Duration haHeartbeatTimeoutOverride = null;
    private Duration haHeartbeatPeriodOverride = null;
    
    private volatile BrooklynWebServer webServer;
    @SuppressWarnings("unused")
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
     * 
     * @deprecated since 0.9.0; instead use {@link #application(String)} for YAML apps, or {@link #application(EntitySpec)}.
     *             Note that apps are now auto-managed on construction through EntitySpec/YAML.
     */
    @Deprecated
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
        if (this.brooklynProperties!=null && brooklynProperties!=null && this.brooklynProperties!=brooklynProperties)
            LOG.warn("Brooklyn properties being reset in "+this+"; set null first if you wish to clear it", new Throwable("Source of brooklyn properties reset"));
        this.brooklynProperties = brooklynProperties;
        return this;
    }
    
    /**
     * Specifies a property to be added to the brooklyn properties
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
        if (port==null) return webconsolePort((PortRange)null);
        return webconsolePort(PortRanges.fromString(port));
    }

    /**
     * Specifies the port where the web console (and any additional webapps specified) will listen;
     * default (null) means "8081+" being the first available >= 8081 (or "8443+" for https).
     */ 
    public BrooklynLauncher webconsolePort(PortRange port) {
        this.port = port;
        return this;
    }

    /**
     * Specifies whether the webconsole should use https.
     */ 
    public BrooklynLauncher webconsoleHttps(Boolean useHttps) {
        this.useHttps = useHttps;
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

    public BrooklynLauncher ignoreCatalogErrors(boolean ignoreCatalogErrors) {
        this.ignoreCatalogErrors = ignoreCatalogErrors;
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

    public BrooklynLauncher customizeManagement(Function<ManagementContext,Void> customizeManagement) {
        this.customizeManagement = customizeManagement;
        return this;
    }

    @Beta
    public BrooklynLauncher catalogInitialization(CatalogInitialization catInit) {
        if (this.catalogInitialization!=null)
            throw new IllegalStateException("Initial catalog customization already set.");
        this.catalogInitialization = catInit;
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
        this.haHeartbeatTimeoutOverride = val;
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
        this.haHeartbeatPeriodOverride = val;
        return this;
    }

    /**
     * @param destinationDir Directory for state to be copied to
     */
    public void copyPersistedState(String destinationDir) {
        copyPersistedState(destinationDir, null, null);
    }

    /**
     * A listener to call when the user requests a shutdown (i.e. through the REST API)
     */
    public BrooklynLauncher shutdownHandler(ShutdownHandler shutdownHandler) {
        this.shutdownHandler = shutdownHandler;
        return this;
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
            
            ManagementPlaneSyncRecord planeState = managementContext.getHighAvailabilityManager().loadManagementPlaneSyncRecord(true);
            
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
     * @param destinationLocationSpec Optional location if target for copied state is a blob store.
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

        // Create the management context
        initManagementContext();

        // Inform catalog initialization that it is starting up
        CatalogInitialization catInit = ((ManagementContextInternal)managementContext).getCatalogInitialization();
        catInit.setStartingUp(true);

        // Start webapps as soon as mgmt context available -- can use them to detect progress of other processes
        if (startWebApps) {
            try {
                startWebApps();
            } catch (Exception e) {
                handleSubsystemStartupError(ignoreWebErrors, "core web apps", e);
            }
        }
        
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

        try {
            // run cat init now if it hasn't yet been run; 
            // will also run if there was an ignored error in catalog above, allowing it to fail startup here if requested
            if (catInit!=null && !catInit.hasRunOfficialInitialization()) {
                if (persistMode==PersistMode.DISABLED) {
                    LOG.debug("Loading catalog as part of launch sequence (it was not loaded as part of any rebind sequence)");
                    catInit.populateCatalog(ManagementNodeState.MASTER, true, true, null);
                } else {
                    // should have loaded during rebind
                    ManagementNodeState state = managementContext.getHighAvailabilityManager().getNodeState();
                    LOG.warn("Loading catalog for "+state+" as part of launch sequence (it was not loaded as part of the rebind sequence)");
                    catInit.populateCatalog(state, true, true, null);
                }
            }
        } catch (Exception e) {
            handleSubsystemStartupError(ignoreCatalogErrors, "initial catalog", e);
        }
        catInit.setStartingUp(false);

        // Create the locations. Must happen after persistence is started in case the
        // management context's catalog is loaded from persisted state. (Location
        // resolution uses the catalog's classpath to scan for resolvers.)
        locations.addAll(managementContext.getLocationRegistry().resolve(locationSpecs));

        // Already rebinded successfully, so previous apps are now available.
        // Allow the startup to be visible in console for newly created apps.
        ((LocalManagementContext)managementContext).noteStartupComplete();

        // TODO create apps only after becoming master, analogously to catalog initialization
        try {
            createApps();
            startApps();
        } catch (Exception e) {
            handleSubsystemStartupError(ignoreAppErrors, "brooklyn autostart apps", e);
        }

        if (startBrooklynNode) {
            try {
                startBrooklynNode();
            } catch (Exception e) {
                handleSubsystemStartupError(ignoreAppErrors, "brooklyn node / self entity", e);
            }
        }
        
        if (persistMode != PersistMode.DISABLED) {
            // Make sure the new apps are persisted in case process exits immediately.
            managementContext.getRebindManager().forcePersistNow(false, null);
        }
        return this;
    }

    private void initManagementContext() {
        // Create the management context
        if (managementContext == null) {
            if (brooklynProperties == null) {
                BrooklynProperties.Factory.Builder builder = BrooklynProperties.Factory.builderDefault();

                if (globalBrooklynPropertiesFile != null) {
                    File globalProperties = new File(Os.tidyPath(globalBrooklynPropertiesFile));
                    if (globalProperties.exists()) {
                        globalProperties = resolveSymbolicLink(globalProperties);
                        checkFileReadable(globalProperties);
                        // brooklyn.properties stores passwords (web-console and cloud credentials),
                        // so ensure it has sensible permissions
                        checkFilePermissionsX00(globalProperties);
                        LOG.debug("Using global properties file " + globalProperties);
                    } else {
                        LOG.debug("Global properties file " + globalProperties + " does not exist, will ignore");
                    }
                    builder.globalPropertiesFile(globalProperties.getAbsolutePath());
                } else {
                    LOG.debug("Global properties file disabled");
                    builder.globalPropertiesFile(null);
                }
                
                if (localBrooklynPropertiesFile != null) {
                    File localProperties = new File(Os.tidyPath(localBrooklynPropertiesFile));
                    localProperties = resolveSymbolicLink(localProperties);
                    checkFileReadable(localProperties);
                    checkFilePermissionsX00(localProperties);
                    builder.localPropertiesFile(localProperties.getAbsolutePath());
                }

                managementContext = new LocalManagementContext(builder, brooklynAdditionalProperties);

            } else {
                if (globalBrooklynPropertiesFile != null)
                    LOG.warn("Ignoring globalBrooklynPropertiesFile "+globalBrooklynPropertiesFile+" because explicit brooklynProperties supplied");
                if (localBrooklynPropertiesFile != null)
                    LOG.warn("Ignoring localBrooklynPropertiesFile "+localBrooklynPropertiesFile+" because explicit brooklynProperties supplied");
                managementContext = new LocalManagementContext(brooklynProperties, brooklynAdditionalProperties);
            }

            brooklynProperties = ((ManagementContextInternal)managementContext).getBrooklynProperties();
            
            // We created the management context, so we are responsible for terminating it
            BrooklynShutdownHooks.invokeTerminateOnShutdown(managementContext);
            
        } else if (brooklynProperties == null) {
            brooklynProperties = ((ManagementContextInternal)managementContext).getBrooklynProperties();
            brooklynProperties.addFromMap(brooklynAdditionalProperties);
        }
        
        if (catalogInitialization!=null) {
            ((ManagementContextInternal)managementContext).setCatalogInitialization(catalogInitialization);
        }
        
        if (customizeManagement!=null) {
            customizeManagement.apply(managementContext);
        }
    }

    /**
     * @return The canonical path of the argument.
     */
    private File resolveSymbolicLink(File f) {
        File f2 = f;
        try {
            f2 = f.getCanonicalFile();
            if (Files.isSymbolicLink(f.toPath())) {
                LOG.debug("Resolved symbolic link: {} -> {}", f, f2);
            }
        } catch (IOException e) {
            LOG.warn("Could not determine canonical name of file "+f+"; returning original file", e);
        }
        return f2;
    }

    private void checkFileReadable(File f) {
        if (!f.exists()) {
            throw new FatalRuntimeException("File " + f + " does not exist");
        }
        if (!f.isFile()) {
            throw new FatalRuntimeException(f + " is not a file");
        }
        if (!f.canRead()) {
            throw new FatalRuntimeException(f + " is not readable");
        }
    }
    
    private void checkFilePermissionsX00(File f) {

        Maybe<String> permission = FileUtil.getFilePermissions(f);
        if (permission.isAbsent()) {
            LOG.debug("Could not determine permissions of file; assuming ok: "+f);
        } else {
            if (!permission.get().subSequence(4, 10).equals("------")) {
                throw new FatalRuntimeException("Invalid permissions for file " + f + "; expected ?00 but was " + permission.get());
            }
        }
    }
    
    private void handleSubsystemStartupError(boolean ignoreSuchErrors, String system, Exception e) {
        Exceptions.propagateIfFatal(e);
        if (ignoreSuchErrors) {
            LOG.error("Subsystem for "+system+" had startup error (continuing with startup): "+e, e);
            if (managementContext!=null)
                ((ManagementContextInternal)managementContext).errors().add(e);
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
            LOG.info("No security provider options specified. Define a security provider or users to prevent a random password being created and logged.");
            
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
                    BrooklynWebConfig.SECURITY_PROVIDER_INSTANCE,
                    new BrooklynUserWithRandomPasswordSecurityProvider(managementContext));
        } else {
            LOG.debug("Starting Brooklyn using security properties: "+brooklynProperties.submap(ConfigPredicates.startingWith(BrooklynWebConfig.BASE_NAME_SECURITY)).asMapWithStringKeys());
        }
        if (bindAddress == null) bindAddress = Networking.ANY_NIC;

        LOG.debug("Starting Brooklyn web-console with bindAddress "+bindAddress+" and properties "+brooklynProperties);
        try {
            webServer = new BrooklynWebServer(webconsoleFlags, managementContext);
            webServer.setBindAddress(bindAddress);
            webServer.setPublicAddress(publicAddress);
            if (port!=null) webServer.setPort(port);
            if (useHttps!=null) webServer.setHttpsEnabled(useHttps);
            webServer.setShutdownHandler(shutdownHandler);
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
                persistenceDir = BrooklynServerPaths.newMainPersistencePathResolver(brooklynProperties).location(persistenceLocation).dir(persistenceDir).resolve();
                objectStore = BrooklynPersistenceUtils.newPersistenceObjectStore(managementContext, persistenceLocation, persistenceDir, 
                    persistMode, highAvailabilityMode);
                    
                RebindManager rebindManager = managementContext.getRebindManager();
                
                BrooklynMementoPersisterToObjectStore persister = new BrooklynMementoPersisterToObjectStore(
                    objectStore,
                    ((ManagementContextInternal)managementContext).getBrooklynProperties(),
                    managementContext.getCatalogClassLoader());
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
                    objectStore,
                    managementContext.getCatalogClassLoader());
            ((HighAvailabilityManagerImpl)haManager).setHeartbeatTimeout(haHeartbeatTimeoutOverride);
            ((HighAvailabilityManagerImpl)haManager).setPollPeriod(haHeartbeatPeriodOverride);
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
            
            HighAvailabilityMode startMode=null;
            switch (highAvailabilityMode) {
                case AUTO:
                case MASTER:
                case STANDBY:
                case HOT_STANDBY:
                case HOT_BACKUP:
                    startMode = highAvailabilityMode;
                    break;
                case DISABLED:
                    throw new IllegalStateException("Unexpected code-branch for high availability mode "+highAvailabilityMode);
            }
            if (startMode==null)
                throw new IllegalStateException("Unexpected high availability mode "+highAvailabilityMode);
            
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

        ClassLoader classLoader = managementContext.getCatalogClassLoader();
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
            Application app = EntityManagementUtils.createUnstarted(managementContext, blueprint);
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
                        .displayName("Brooklyn Console"));
            }
        };
        LocationSpec<?> spec = LocationSpec.create(LocalhostMachine.class).displayName("Local Brooklyn");
        Location localhost = managementContext.getLocationManager().createLocation(spec);
        brooklyn.appDisplayName("Brooklyn")
                .manage(managementContext)
                .start(ImmutableList.of(localhost));
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
                managementContext.getRebindManager().waitForPendingComplete(Duration.TEN_SECONDS, true);
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
