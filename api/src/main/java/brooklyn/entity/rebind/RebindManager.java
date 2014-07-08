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
import brooklyn.mementos.BrooklynMementoPersister;
import brooklyn.util.time.Duration;

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

    /**
     * @deprecated since 0.7; use {@link #rebind(ClassLoader)}
     */
    @Deprecated
    public List<Application> rebind() throws IOException;
    
    public List<Application> rebind(ClassLoader classLoader) throws IOException;

    public List<Application> rebind(ClassLoader classLoader, RebindExceptionHandler exceptionHandler) throws IOException;

    public ChangeListener getChangeListener();

    /**
     * Starts the persisting of state (if persister is set; otherwise will start persisting as soon as
     * persister is set). Until {@link #start()} is called, no data will be persisted but entities can 
     * rebind.
     */
    public void start();

    public void stop();

    /** @deprecated since 0.7.0; use {@link #waitForPendingComplete(Duration)} */
    @VisibleForTesting
    @Deprecated
    public void waitForPendingComplete(long timeout, TimeUnit unit) throws InterruptedException, TimeoutException;
    /** waits for any needed or pending writes to complete */
    @VisibleForTesting
    public void waitForPendingComplete(Duration duration) throws InterruptedException, TimeoutException;
    /** forcibly performs persistence, in the foreground */
    @VisibleForTesting
    public void forcePersistNow();
    
}
