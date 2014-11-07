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
package brooklyn.entity.rebind;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import brooklyn.entity.Application;
import brooklyn.management.ha.ManagementNodeState;
import brooklyn.mementos.BrooklynMementoPersister;
import brooklyn.mementos.BrooklynMementoRawData;
import brooklyn.util.time.Duration;

import com.google.common.annotations.Beta;
import com.google.common.annotations.VisibleForTesting;

/**
 * Manages the persisting of brooklyn's state, and recreating that state, e.g. on
 * brooklyn restart.
 * 
 * Users are not expected to implement this class, or to call methods on it directly.
 */
public interface RebindManager {
    
    // FIXME Should we be calling managementContext.getRebindManager().rebind, using a
    // new empty instance of managementContext?
    //
    // Or is that a risky API because you could call it on a non-empty managementContext?
    
    public enum RebindFailureMode {
        FAIL_FAST,
        FAIL_AT_END,
        CONTINUE;
    }
    
    public void setPersister(BrooklynMementoPersister persister);

    public void setPersister(BrooklynMementoPersister persister, PersistenceExceptionHandler exceptionHandler);

    @VisibleForTesting
    public BrooklynMementoPersister getPersister();

    /** @deprecated since 0.7; use {@link #rebind(ClassLoader, RebindExceptionHandler, ManagementNodeState)} */ @Deprecated
    public List<Application> rebind();
    
    /** @deprecated since 0.7; use {@link #rebind(ClassLoader, RebindExceptionHandler, ManagementNodeState)} */ @Deprecated
    public List<Application> rebind(ClassLoader classLoader);
    /** @deprecated since 0.7; use {@link #rebind(ClassLoader, RebindExceptionHandler, ManagementNodeState)} */ @Deprecated
    public List<Application> rebind(ClassLoader classLoader, RebindExceptionHandler exceptionHandler);
    /** Causes this management context to rebind, loading data from the given backing store.
     * use wisely, as this can cause local entities to be completely lost, or will throw in many other situations.
     * in general it may be invoked for a new node becoming {@link ManagementNodeState#MASTER} 
     * or periodically for a node in {@link ManagementNodeState#HOT_STANDBY}. */
    @Beta
    public List<Application> rebind(ClassLoader classLoader, RebindExceptionHandler exceptionHandler, ManagementNodeState mode);

    public BrooklynMementoRawData retrieveMementoRawData();

    public ChangeListener getChangeListener();

    /**
     * Starts the background persisting of state
     * (if persister is set; otherwise will start persisting as soon as persister is set). 
     * Until this is called, no data will be persisted although entities can be rebinded.
     */
    public void startPersistence();

    /** Stops the background persistence of state. 
     * Waits for any current persistence to complete. */
    public void stopPersistence();

    /**
     * Perform an initial load of state read-only and starts a background process 
     * reading (mirroring) state periodically.
     */
    public void startReadOnly();
    /** Stops the background reading (mirroring) of state. 
     * Interrupts any current activity and waits for it to cease. */
    public void stopReadOnly();
    
    /** Starts the appropriate background processes, {@link #startPersistence()} if {@link ManagementNodeState#MASTER},
     * {@link #startReadOnly()} if {@link ManagementNodeState#HOT_STANDBY} */
    public void start();
    /** Stops the appropriate background processes, {@link #stopPersistence()} or {@link #stopReadOnly()},
     * waiting for activity there to cease (interrupting in the case of {@link #stopReadOnly()}). */
    public void stop();
    
    /** @deprecated since 0.7.0; use {@link #waitForPendingComplete(Duration)} */
    @VisibleForTesting
    @Deprecated
    public void waitForPendingComplete(long timeout, TimeUnit unit) throws InterruptedException, TimeoutException;
    /** waits for any needed or pending writes to complete */
    @VisibleForTesting
    public void waitForPendingComplete(Duration duration) throws InterruptedException, TimeoutException;
    /** Forcibly performs persistence, in the foreground */
    @VisibleForTesting
    public void forcePersistNow();
}
