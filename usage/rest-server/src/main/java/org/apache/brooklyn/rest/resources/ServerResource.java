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
package org.apache.brooklyn.rest.resources;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.collect.FluentIterable;

import brooklyn.BrooklynVersion;
import brooklyn.config.ConfigKey;
import brooklyn.entity.Application;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.basic.Lifecycle;
import brooklyn.entity.basic.StartableApplication;
import brooklyn.entity.rebind.persister.BrooklynPersistenceUtils;
import brooklyn.entity.rebind.persister.FileBasedObjectStore;
import brooklyn.entity.rebind.persister.PersistenceObjectStore;
import brooklyn.management.entitlement.Entitlements;
import brooklyn.management.internal.ManagementContextInternal;

import org.apache.brooklyn.management.ManagementContext;
import org.apache.brooklyn.management.Task;
import org.apache.brooklyn.management.entitlement.EntitlementContext;
import org.apache.brooklyn.management.ha.HighAvailabilityManager;
import org.apache.brooklyn.management.ha.HighAvailabilityMode;
import org.apache.brooklyn.management.ha.ManagementNodeState;
import org.apache.brooklyn.management.ha.ManagementPlaneSyncRecord;
import org.apache.brooklyn.management.ha.MementoCopyMode;
import org.apache.brooklyn.rest.api.ServerApi;
import org.apache.brooklyn.rest.domain.BrooklynFeatureSummary;
import org.apache.brooklyn.rest.domain.HighAvailabilitySummary;
import org.apache.brooklyn.rest.domain.VersionSummary;
import org.apache.brooklyn.rest.transform.BrooklynFeatureTransformer;
import org.apache.brooklyn.rest.transform.HighAvailabilityTransformer;
import org.apache.brooklyn.rest.util.ShutdownHandler;
import org.apache.brooklyn.rest.util.WebResourceUtils;

import brooklyn.util.ResourceUtils;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.file.ArchiveBuilder;
import brooklyn.util.flags.TypeCoercions;
import brooklyn.util.guava.Maybe;
import brooklyn.util.os.Os;
import brooklyn.util.text.Identifiers;
import brooklyn.util.text.Strings;
import brooklyn.util.time.CountdownTimer;
import brooklyn.util.time.Duration;
import brooklyn.util.time.Time;

public class ServerResource extends AbstractBrooklynRestResource implements ServerApi {

    private static final int SHUTDOWN_TIMEOUT_CHECK_INTERVAL = 200;

    private static final Logger log = LoggerFactory.getLogger(ServerResource.class);

    private static final String BUILD_SHA_1_PROPERTY = "git-sha-1";
    private static final String BUILD_BRANCH_PROPERTY = "git-branch-name";
    
    @Context
    private ShutdownHandler shutdownHandler;

    @Override
    public void reloadBrooklynProperties() {
        brooklyn().reloadBrooklynProperties();
    }

    private boolean isMaster() {
        return ManagementNodeState.MASTER.equals(mgmt().getHighAvailabilityManager().getNodeState());
    }

    @Override
    public void shutdown(final boolean stopAppsFirst, final boolean forceShutdownOnError,
            String shutdownTimeoutRaw, String requestTimeoutRaw, String delayForHttpReturnRaw,
            Long delayMillis) {
        
        if (!Entitlements.isEntitled(mgmt().getEntitlementManager(), Entitlements.SEE_ALL_SERVER_INFO, null))
            throw WebResourceUtils.unauthorized("User '%s' is not authorized for this operation", Entitlements.getEntitlementContext().user());
        
        log.info("REST call to shutdown server, stopAppsFirst="+stopAppsFirst+", delayForHttpReturn="+shutdownTimeoutRaw);

        if (stopAppsFirst && !isMaster()) {
            log.warn("REST call to shutdown non-master server while stopping apps is disallowed");
            throw WebResourceUtils.forbidden("Not allowed to stop all apps when server is not master");
        }
        final Duration shutdownTimeout = parseDuration(shutdownTimeoutRaw, Duration.of(20, TimeUnit.SECONDS));
        Duration requestTimeout = parseDuration(requestTimeoutRaw, Duration.of(20, TimeUnit.SECONDS));
        final Duration delayForHttpReturn;
        if (delayMillis == null) {
            delayForHttpReturn = parseDuration(delayForHttpReturnRaw, Duration.FIVE_SECONDS);
        } else {
            log.warn("'delayMillis' is deprecated, use 'delayForHttpReturn' instead.");
            delayForHttpReturn = Duration.of(delayMillis, TimeUnit.MILLISECONDS);
        }

        Preconditions.checkState(delayForHttpReturn.nanos() >= 0, "Only positive or 0 delay allowed for delayForHttpReturn");

        boolean isSingleTimeout = shutdownTimeout.equals(requestTimeout);
        final AtomicBoolean completed = new AtomicBoolean();
        final AtomicBoolean hasAppErrorsOrTimeout = new AtomicBoolean();

        new Thread("shutdown") {
            @Override
            public void run() {
                boolean terminateTried = false;
                ManagementContext mgmt = mgmt();
                try {
                    if (stopAppsFirst) {
                        CountdownTimer shutdownTimeoutTimer = null;
                        if (!shutdownTimeout.equals(Duration.ZERO)) {
                            shutdownTimeoutTimer = shutdownTimeout.countdownTimer();
                        }

                        log.debug("Stopping applications");
                        List<Task<?>> stoppers = new ArrayList<Task<?>>();
                        int allStoppableApps = 0;
                        for (Application app: mgmt.getApplications()) {
                            allStoppableApps++;
                            Lifecycle appState = app.getAttribute(Attributes.SERVICE_STATE_ACTUAL);
                            if (app instanceof StartableApplication &&
                                    // Don't try to stop an already stopping app. Subsequent stops will complete faster
                                    // cancelling the first stop task.
                                    appState != Lifecycle.STOPPING) {
                                stoppers.add(Entities.invokeEffector((EntityLocal)app, app, StartableApplication.STOP));
                            } else {
                                log.debug("App " + app + " is already stopping, will not stop second time. Will wait for original stop to complete.");
                            }
                        }

                        log.debug("Waiting for " + allStoppableApps + " apps to stop, of which " + stoppers.size() + " stopped explicitly.");
                        for (Task<?> t: stoppers) {
                            if (!waitAppShutdown(shutdownTimeoutTimer, t)) {
                                //app stop error
                                hasAppErrorsOrTimeout.set(true);
                            }
                        }

                        // Wait for apps which were already stopping when we tried to shut down.
                        if (hasStoppableApps(mgmt)) {
                            log.debug("Apps are still stopping, wait for proper unmanage.");
                            while (hasStoppableApps(mgmt) && (shutdownTimeoutTimer == null || !shutdownTimeoutTimer.isExpired())) {
                                Duration wait;
                                if (shutdownTimeoutTimer != null) {
                                    wait = Duration.min(shutdownTimeoutTimer.getDurationRemaining(), Duration.ONE_SECOND);
                                } else {
                                    wait = Duration.ONE_SECOND;
                                }
                                Time.sleep(wait);
                            }
                            if (hasStoppableApps(mgmt)) {
                                hasAppErrorsOrTimeout.set(true);
                            }
                        }
                    }

                    terminateTried = true;
                    ((ManagementContextInternal)mgmt).terminate(); 

                } catch (Throwable e) {
                    Throwable interesting = Exceptions.getFirstInteresting(e);
                    if (interesting instanceof TimeoutException) {
                        //timeout while waiting for apps to stop
                        log.warn("Timeout shutting down: "+Exceptions.collapseText(e));
                        log.debug("Timeout shutting down: "+e, e);
                        hasAppErrorsOrTimeout.set(true);
                        
                    } else {
                        // swallow fatal, so we notify the outer loop to continue with shutdown
                        log.error("Unexpected error shutting down: "+Exceptions.collapseText(e), e);
                        
                    }
                    hasAppErrorsOrTimeout.set(true);
                    
                    if (!terminateTried) {
                        ((ManagementContextInternal)mgmt).terminate(); 
                    }
                } finally {

                    complete();
                
                    if (!hasAppErrorsOrTimeout.get() || forceShutdownOnError) {
                        //give the http request a chance to complete gracefully, the server will be stopped in a shutdown hook
                        Time.sleep(delayForHttpReturn);

                        if (shutdownHandler != null) {
                            shutdownHandler.onShutdownRequest();
                        } else {
                            log.warn("ShutdownHandler not set, exiting process");
                            System.exit(0);
                        }
                        
                    } else {
                        // There are app errors, don't exit the process, allowing any exception to continue throwing
                        log.warn("Abandoning shutdown because there were errors and shutdown was not forced.");
                        
                    }
                }
            }

            private boolean hasStoppableApps(ManagementContext mgmt) {
                for (Application app : mgmt.getApplications()) {
                    if (app instanceof StartableApplication) {
                        Lifecycle state = app.getAttribute(Attributes.SERVICE_STATE_ACTUAL);
                        if (state != Lifecycle.STOPPING && state != Lifecycle.STOPPED) {
                            log.warn("Shutting down, expecting all apps to be in stopping state, but found application " + app + " to be in state " + state + ". Just started?");
                        }
                        return true;
                    }
                }
                return false;
            }

            private void complete() {
                synchronized (completed) {
                    completed.set(true);
                    completed.notifyAll();
                }
            }

            private boolean waitAppShutdown(CountdownTimer shutdownTimeoutTimer, Task<?> t) throws TimeoutException {
                Duration waitInterval = null;
                //wait indefinitely if no shutdownTimeoutTimer (shutdownTimeout == 0)
                if (shutdownTimeoutTimer != null) {
                    waitInterval = Duration.of(SHUTDOWN_TIMEOUT_CHECK_INTERVAL, TimeUnit.MILLISECONDS);
                }
                // waitInterval == null - blocks indefinitely
                while(!t.blockUntilEnded(waitInterval)) {
                    if (shutdownTimeoutTimer.isExpired()) {
                        log.warn("Timeout while waiting for applications to stop at "+t+".\n"+t.getStatusDetail(true));
                        throw new TimeoutException();
                    }
                }
                if (t.isError()) {
                    log.warn("Error stopping application "+t+" during shutdown (ignoring)\n"+t.getStatusDetail(true));
                    return false;
                } else {
                    return true;
                }
            }
        }.start();

        synchronized (completed) {
            if (!completed.get()) {
                try {
                    long waitTimeout = 0;
                    //If the timeout for both shutdownTimeout and requestTimeout is equal
                    //then better wait until the 'completed' flag is set, rather than timing out
                    //at just about the same time (i.e. always wait for the shutdownTimeout in this case).
                    //This will prevent undefined behaviour where either one of shutdownTimeout or requestTimeout
                    //will be first to expire and the error flag won't be set predictably, it will
                    //toggle depending on which expires first.
                    //Note: shutdownTimeout is checked at SHUTDOWN_TIMEOUT_CHECK_INTERVAL interval, meaning it is
                    //practically rounded up to the nearest SHUTDOWN_TIMEOUT_CHECK_INTERVAL.
                    if (!isSingleTimeout) {
                        waitTimeout = requestTimeout.toMilliseconds();
                    }
                    completed.wait(waitTimeout);
                } catch (InterruptedException e) {
                    throw Exceptions.propagate(e);
                }
            }
        }

        if (hasAppErrorsOrTimeout.get()) {
            WebResourceUtils.badRequest("Error or timeout while stopping applications. See log for details.");
        }
    }

    private Duration parseDuration(String str, Duration defaultValue) {
        if (Strings.isEmpty(str)) {
            return defaultValue;
        } else {
            return Duration.parse(str);
        }
    }

    @Override
    public VersionSummary getVersion() {
        if (!Entitlements.isEntitled(mgmt().getEntitlementManager(), Entitlements.SERVER_STATUS, null))
            throw WebResourceUtils.unauthorized("User '%s' is not authorized for this operation", Entitlements.getEntitlementContext().user());
        
        // TODO
        // * "build-metadata.properties" is probably the wrong name
        // * we should include brooklyn.version and a build timestamp in this file
        // * the authority for brooklyn should probably be core rather than brooklyn-rest-server
        InputStream input = ResourceUtils.create().getResourceFromUrl("classpath://build-metadata.properties");
        Properties properties = new Properties();
        String gitSha1 = null, gitBranch = null;
        try {
            properties.load(input);
            gitSha1 = properties.getProperty(BUILD_SHA_1_PROPERTY);
            gitBranch = properties.getProperty(BUILD_BRANCH_PROPERTY);
        } catch (IOException e) {
            log.error("Failed to load build-metadata.properties", e);
        }
        gitSha1 = BrooklynVersion.INSTANCE.getSha1FromOsgiManifest();

        FluentIterable<BrooklynFeatureSummary> features = FluentIterable.from(BrooklynVersion.getFeatures(mgmt()))
                .transform(BrooklynFeatureTransformer.FROM_FEATURE);

        return new VersionSummary(BrooklynVersion.get(), gitSha1, gitBranch, features.toList());
    }

    @Override
    public boolean isUp() {
        if (!Entitlements.isEntitled(mgmt().getEntitlementManager(), Entitlements.SERVER_STATUS, null))
            throw WebResourceUtils.unauthorized("User '%s' is not authorized for this operation", Entitlements.getEntitlementContext().user());

        Maybe<ManagementContext> mm = mgmtMaybe();
        return !mm.isAbsent() && mm.get().isStartupComplete() && mm.get().isRunning();
    }
    
    @Override
    public boolean isShuttingDown() {
        if (!Entitlements.isEntitled(mgmt().getEntitlementManager(), Entitlements.SERVER_STATUS, null))
            throw WebResourceUtils.unauthorized("User '%s' is not authorized for this operation", Entitlements.getEntitlementContext().user());
        Maybe<ManagementContext> mm = mgmtMaybe();
        return !mm.isAbsent() && mm.get().isStartupComplete() && !mm.get().isRunning();
    }
    
    @Override
    public boolean isHealthy() {
        return isUp() && ((ManagementContextInternal) mgmt()).errors().isEmpty();
    }
    
    @Override
    public Map<String,Object> getUpExtended() {
        return MutableMap.<String,Object>of(
            "up", isUp(),
            "shuttingDown", isShuttingDown(),
            "healthy", isHealthy(),
            "ha", getHighAvailabilityPlaneStates());
    }
    
    
    @Deprecated
    @Override
    public String getStatus() {
        return getHighAvailabilityNodeState().toString();
    }

    @Override
    public String getConfig(String configKey) {
        if (!Entitlements.isEntitled(mgmt().getEntitlementManager(), Entitlements.SEE_ALL_SERVER_INFO, null)) {
            throw WebResourceUtils.unauthorized("User '%s' is not authorized for this operation", Entitlements.getEntitlementContext().user());
        }
        ConfigKey<String> config = ConfigKeys.newStringConfigKey(configKey);
        return mgmt().getConfig().getConfig(config);
    }

    @Deprecated
    @Override
    public HighAvailabilitySummary getHighAvailability() {
        return getHighAvailabilityPlaneStates();
    }

    @Override
    public ManagementNodeState getHighAvailabilityNodeState() {
        if (!Entitlements.isEntitled(mgmt().getEntitlementManager(), Entitlements.SERVER_STATUS, null))
            throw WebResourceUtils.unauthorized("User '%s' is not authorized for this operation", Entitlements.getEntitlementContext().user());
        
        Maybe<ManagementContext> mm = mgmtMaybe();
        if (mm.isAbsent()) return ManagementNodeState.INITIALIZING;
        return mm.get().getHighAvailabilityManager().getNodeState();
    }

    @Override
    public ManagementNodeState setHighAvailabilityNodeState(HighAvailabilityMode mode) {
        if (mode==null)
            throw new IllegalStateException("Missing parameter: mode");
        
        HighAvailabilityManager haMgr = mgmt().getHighAvailabilityManager();
        ManagementNodeState existingState = haMgr.getNodeState();
        haMgr.changeMode(mode);
        return existingState;
    }

    @Override
    public Map<String, Object> getHighAvailabilityMetrics() {
        return mgmt().getHighAvailabilityManager().getMetrics();
    }
    
    @Override
    public long getHighAvailabitlityPriority() {
        return mgmt().getHighAvailabilityManager().getPriority();
    }

    @Override
    public long setHighAvailabilityPriority(long priority) {
        HighAvailabilityManager haMgr = mgmt().getHighAvailabilityManager();
        long oldPrio = haMgr.getPriority();
        haMgr.setPriority(priority);
        return oldPrio;
    }

    @Override
    public HighAvailabilitySummary getHighAvailabilityPlaneStates() {
        if (!Entitlements.isEntitled(mgmt().getEntitlementManager(), Entitlements.SERVER_STATUS, null))
            throw WebResourceUtils.unauthorized("User '%s' is not authorized for this operation", Entitlements.getEntitlementContext().user());
        ManagementPlaneSyncRecord memento = mgmt().getHighAvailabilityManager().getLastManagementPlaneSyncRecord();
        if (memento==null) memento = mgmt().getHighAvailabilityManager().loadManagementPlaneSyncRecord(true);
        if (memento==null) return null;
        return HighAvailabilityTransformer.highAvailabilitySummary(mgmt().getManagementNodeId(), memento);
    }

    @Override
    public Response clearHighAvailabilityPlaneStates() {
        mgmt().getHighAvailabilityManager().publishClearNonMaster();
        return Response.ok().build();
    }

    @Override
    public String getUser() {
        EntitlementContext entitlementContext = Entitlements.getEntitlementContext();
        if (entitlementContext!=null && entitlementContext.user()!=null){
            return entitlementContext.user();
        } else {
            return null; //User can be null if no authentication was requested
        }
    }

    @Override
    public Response exportPersistenceData(String preferredOrigin) {
        return exportPersistenceData(TypeCoercions.coerce(preferredOrigin, MementoCopyMode.class));
    }
    
    protected Response exportPersistenceData(MementoCopyMode preferredOrigin) {
        if (!Entitlements.isEntitled(mgmt().getEntitlementManager(), Entitlements.SEE_ALL_SERVER_INFO, null))
            throw WebResourceUtils.unauthorized("User '%s' is not authorized for this operation", Entitlements.getEntitlementContext().user());

        File dir = null;
        try {
            String label = mgmt().getManagementNodeId()+"-"+Time.makeDateSimpleStampString();
            PersistenceObjectStore targetStore = BrooklynPersistenceUtils.newPersistenceObjectStore(mgmt(), null, 
                "tmp/web-persistence-"+label+"-"+Identifiers.makeRandomId(4));
            dir = ((FileBasedObjectStore)targetStore).getBaseDir();
            // only register the parent dir because that will prevent leaks for the random ID
            Os.deleteOnExitEmptyParentsUpTo(dir.getParentFile(), dir.getParentFile());
            BrooklynPersistenceUtils.writeMemento(mgmt(), targetStore, preferredOrigin);            
            
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ArchiveBuilder.zip().addDirContentsAt( ((FileBasedObjectStore)targetStore).getBaseDir(), ((FileBasedObjectStore)targetStore).getBaseDir().getName() ).stream(baos);
            Os.deleteRecursively(dir);
            String filename = "brooklyn-state-"+label+".zip";
            return Response.ok(baos.toByteArray(), MediaType.APPLICATION_OCTET_STREAM_TYPE)
                .header("Content-Disposition","attachment; filename = "+filename)
                .build();
        } catch (Exception e) {
            log.warn("Unable to serve persistence data (rethrowing): "+e, e);
            if (dir!=null) {
                try {
                    Os.deleteRecursively(dir);
                } catch (Exception e2) {
                    log.warn("Ignoring error deleting '"+dir+"' after another error, throwing original error ("+e+"); ignored error deleting is: "+e2);
                }
            }
            throw Exceptions.propagate(e);
        }
    }

}
