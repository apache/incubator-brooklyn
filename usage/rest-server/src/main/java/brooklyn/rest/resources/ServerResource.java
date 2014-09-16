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
package brooklyn.rest.resources;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Timer;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;

import brooklyn.BrooklynVersion;
import brooklyn.entity.Application;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.basic.StartableApplication;
import brooklyn.management.Task;
import brooklyn.management.entitlement.EntitlementContext;
import brooklyn.management.entitlement.Entitlements;
import brooklyn.management.ha.ManagementPlaneSyncRecord;
import brooklyn.management.internal.ManagementContextInternal;
import brooklyn.rest.api.ServerApi;
import brooklyn.rest.domain.HighAvailabilitySummary;
import brooklyn.rest.domain.VersionSummary;
import brooklyn.rest.transform.HighAvailabilityTransformer;
import brooklyn.rest.util.WebResourceUtils;
import brooklyn.util.ResourceUtils;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.time.CountdownTimer;
import brooklyn.util.time.Duration;
import brooklyn.util.time.Time;

public class ServerResource extends AbstractBrooklynRestResource implements ServerApi {

    private static final int SHUTDOWN_TIMEOUT_CHECK_INTERVAL = 200;

    private static final Logger log = LoggerFactory.getLogger(ServerResource.class);

    private static final String BUILD_SHA_1_PROPERTY = "git-sha-1";
    private static final String BUILD_BRANCH_PROPERTY = "git-branch-name";

    @Override
    public void reloadBrooklynProperties() {
        brooklyn().reloadBrooklynProperties();
    }

    @Override
    public void shutdown(final boolean stopAppsFirst, final boolean forceShutdownOnError,
            String shutdownTimeoutRaw, String requestTimeoutRaw, String delayForHttpReturnRaw,
            Long delayMillis) {
        log.info("REST call to shutdown server, stopAppsFirst="+stopAppsFirst+", delayForHttpReturn="+shutdownTimeoutRaw);

        final Duration shutdownTimeout = Duration.parse(shutdownTimeoutRaw);
        Duration requestTimeout = Duration.parse(requestTimeoutRaw);
        final Duration delayForHttpReturn;
        if (delayMillis == null) {
            delayForHttpReturn = Duration.parse(delayForHttpReturnRaw);
        } else {
            log.warn("'delayMillis' is deprecated, use 'delayForHttpReturn' instead.");
            delayForHttpReturn = Duration.of(delayMillis, TimeUnit.MILLISECONDS);
        }

        Preconditions.checkState(delayForHttpReturn.isPositive(), "Only positive delay allowed for delayForHttpReturn");

        boolean isSingleTimeout = shutdownTimeout.equals(requestTimeout);
        final AtomicBoolean completed = new AtomicBoolean();
        final AtomicBoolean hasAppErrorsOrTimeout = new AtomicBoolean();

        new Thread("shutdown") {
            public void run() {
                if (stopAppsFirst) {
                    CountdownTimer shutdownTimeoutTimer = null;
                    if (!shutdownTimeout.equals(Duration.ZERO)) {
                        shutdownTimeoutTimer = shutdownTimeout.countdownTimer();
                    }

                    List<Task<?>> stoppers = new ArrayList<Task<?>>();
                    for (Application app: mgmt().getApplications()) {
                        if (app instanceof StartableApplication)
                            stoppers.add(Entities.invokeEffector((EntityLocal)app, app, StartableApplication.STOP));
                    }

                    try {
                        for (Task<?> t: stoppers) {
                            if (!waitAppShutdown(shutdownTimeoutTimer, t)) {
                                //app stop error
                                hasAppErrorsOrTimeout.set(true);
                            }
                        }
                    } catch (TimeoutException e) {
                        //timeout while waiting for apps to stop
                        hasAppErrorsOrTimeout.set(true);
                    }

                    if (hasAppErrorsOrTimeout.get() && !forceShutdownOnError) {
                        complete();
                        //There are app errors, don't exit the process.
                        return;
                    }
                }

                ((ManagementContextInternal)mgmt()).terminate(); 

                complete();

                //give the http request a chance to complete gracefully
                Time.sleep(delayForHttpReturn);

                System.exit(0);
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
                    //will be first to expire and the error flag won't be set predicably, it will
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

    @Override
    public VersionSummary getVersion() {
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
        return new VersionSummary(BrooklynVersion.get(), gitSha1, gitBranch);
    }

    @Override
    public String getStatus() {
        return mgmt().getHighAvailabilityManager().getNodeState().toString();
    }

    @Override
    public HighAvailabilitySummary getHighAvailability() {
        ManagementPlaneSyncRecord memento = mgmt().getHighAvailabilityManager().getManagementPlaneSyncState();
        return HighAvailabilityTransformer.highAvailabilitySummary(mgmt().getManagementNodeId(), memento);
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
}
