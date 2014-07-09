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
package brooklyn.event.feed;

import static com.google.common.base.Preconditions.checkNotNull;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.EntityLocal;

/** 
 * Captures common fields and processes for sensor feeds.
 * These generally poll or subscribe to get sensor values for an entity.
 * They make it easy to poll over http, jmx, etc.
 * 
 * Assumes:
 *   <ul>
 *     <li>There will not be concurrent calls to start and stop.
 *     <li>There will only be one call to start and that will be done immediately after construction,
 *         in the same thread.
 *     <li>Once stopped, the feed will not be re-started.
 *   </ul>
 */
public abstract class AbstractFeed {

    private static final Logger log = LoggerFactory.getLogger(AbstractFeed.class);
    
    protected final EntityLocal entity;
    protected final Poller<?> poller;
    private volatile boolean activated, suspended;
    private final Object pollerStateMutex = new Object(); 

    public AbstractFeed(EntityLocal entity) {
        this(entity, false);
    }
    
    public AbstractFeed(EntityLocal entity, boolean onlyIfServiceUp) {
        this.entity = checkNotNull(entity, "entity");
        this.poller = new Poller<Object>(entity, onlyIfServiceUp);
    }
    
    /** true if everything has been _started_ (or it is starting) but not stopped,
     * even if it is suspended; see also {@link #isActive()} */
    public boolean isActivated() {
        return activated;
    }
    
    /** true iff the feed is running */
    public boolean isActive() {
        return activated && !suspended;
    }
    
    public EntityLocal getEntity() {
        return entity;
    }
    
    protected boolean isConnected() {
        // TODO Default impl will result in multiple logs for same error if becomes unreachable
        // (e.g. if ssh gets NoRouteToHostException, then every AttributePollHandler for that
        // feed will log.warn - so if polling for 10 sensors/attributes will get 10 log messages).
        // Would be nice if reduced this logging duplication.
        // (You can reduce it by providing a better 'isConnected' implementation of course.)
        return isActivated();
    }

    protected void start() {
        if (log.isDebugEnabled()) log.debug("Starting feed {} for {}", this, entity);
        if (activated) { 
            throw new IllegalStateException(String.format("Attempt to start feed %s of entity %s when already running", 
                    this, entity));
        }
        
        activated = true;
        preStart();
        synchronized (pollerStateMutex) {
            // don't start poller if we are suspended
            if (!suspended) {
                poller.start();
            }
        }
    }

    /** suspends this feed (stops the poller, or indicates that the feed should start in a state where the poller is stopped) */
    public void suspend() {
        synchronized (pollerStateMutex) {
            if (activated && !suspended) {
                poller.stop();
            }
            suspended = true;
        }
    }
    
    /** resumes this feed if it has been suspended and not stopped */
    public void resume() {
        synchronized (pollerStateMutex) {
            if (activated && suspended) {
                poller.start();
            }
            suspended = false;
        }
    }
    
    public void stop() {
        if (!activated) { 
            log.debug("Ignoring attempt to stop feed {} of entity {} when not running", this, entity);
            return;
        }
        if (log.isDebugEnabled()) log.debug("stopping feed {} for {}", this, entity);
        
        activated = false;
        preStop();
        synchronized (pollerStateMutex) {
            if (!suspended) {
                poller.stop();
            }
        }
        postStop();
    }

    /**
     * For overriding.
     */
    protected void preStart() {
    }
    
    /**
     * For overriding.
     */
    protected void preStop() {
    }
    
    /**
     * For overriding.
     */
    protected void postStop() {
    }
}
