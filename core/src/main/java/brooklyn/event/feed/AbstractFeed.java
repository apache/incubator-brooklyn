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

import java.util.Collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Feed;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.entity.basic.EntityInternal;
import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.rebind.BasicFeedRebindSupport;
import brooklyn.entity.rebind.RebindSupport;
import brooklyn.internal.BrooklynFeatureEnablement;
import brooklyn.mementos.FeedMemento;
import brooklyn.policy.basic.AbstractEntityAdjunct;
import brooklyn.util.javalang.JavaClassNames;
import brooklyn.util.text.Strings;

/** 
 * Captures common fields and processes for sensor feeds.
 * These generally poll or subscribe to get sensor values for an entity.
 * They make it easy to poll over http, jmx, etc.
 */
public abstract class AbstractFeed extends AbstractEntityAdjunct implements Feed {

    private static final Logger log = LoggerFactory.getLogger(AbstractFeed.class);

    public static final ConfigKey<Boolean> ONLY_IF_SERVICE_UP = ConfigKeys.newBooleanConfigKey("feed.onlyIfServiceUp", "", false);
    
    private final Object pollerStateMutex = new Object();
    private transient volatile Poller<?> poller;
    private transient volatile boolean activated;
    private transient volatile boolean suspended;

    public AbstractFeed() {
    }
    
    /**
     * @deprecated since 0.7.0; use no-arg constructor; call {@link #setEntity(EntityLocal)}
     */
    @Deprecated
    public AbstractFeed(EntityLocal entity) {
        this(entity, false);
    }
    
    /**
     * @deprecated since 0.7.0; use no-arg constructor; call {@link #setEntity(EntityLocal)} and {@code setConfig(ONLY_IF_SERVICE_UP, onlyIfServiceUp)}
     */
    @Deprecated
    public AbstractFeed(EntityLocal entity, boolean onlyIfServiceUp) {
        this.entity = checkNotNull(entity, "entity");
        setConfig(ONLY_IF_SERVICE_UP, onlyIfServiceUp);
    }

    // Ensure idempotent, as called in builders (in case not registered with entity), and also called
    // when registering with entity
    @Override
    public void setEntity(EntityLocal entity) {
        super.setEntity(entity);
        if (BrooklynFeatureEnablement.isEnabled(BrooklynFeatureEnablement.FEATURE_FEED_REGISTRATION_PROPERTY)) {
            ((EntityInternal)entity).feeds().addFeed(this);
        }
    }

    protected void initUniqueTag(String uniqueTag, Object ...valsForDefault) {
        if (Strings.isNonBlank(uniqueTag)) this.uniqueTag = uniqueTag;
        else this.uniqueTag = getDefaultUniqueTag(valsForDefault);
    }

    protected String getDefaultUniqueTag(Object ...valsForDefault) {
        StringBuilder sb = new StringBuilder();
        sb.append(JavaClassNames.simpleClassName(this));
        if (valsForDefault.length==0) {
            sb.append("@");
            sb.append(hashCode());
        } else if (valsForDefault.length==1 && valsForDefault[0] instanceof Collection){
            sb.append(Strings.toUniqueString(valsForDefault[0], 80));
        } else {
            sb.append("[");
            boolean first = true;
            for (Object x: valsForDefault) {
                if (!first) sb.append(";");
                else first = false;
                sb.append(Strings.toUniqueString(x, 80));
            }
            sb.append("]");
        }
        return sb.toString(); 
    }

    @Override
    public void start() {
        if (log.isDebugEnabled()) log.debug("Starting feed {} for {}", this, entity);
        if (activated) { 
            throw new IllegalStateException(String.format("Attempt to start feed %s of entity %s when already running", 
                    this, entity));
        }
        if (poller != null) {
            throw new IllegalStateException(String.format("Attempt to re-start feed %s of entity %s", this, entity));
        }
        
        poller = new Poller<Object>(entity, getConfig(ONLY_IF_SERVICE_UP));
        activated = true;
        preStart();
        synchronized (pollerStateMutex) {
            // don't start poller if we are suspended
            if (!suspended) {
                poller.start();
            }
        }
    }

    @Override
    public void suspend() {
        synchronized (pollerStateMutex) {
            if (activated && !suspended) {
                poller.stop();
            }
            suspended = true;
        }
    }
    
    @Override
    public void resume() {
        synchronized (pollerStateMutex) {
            if (activated && suspended) {
                poller.start();
            }
            suspended = false;
        }
    }
    
    @Override
    public void destroy() {
        stop();
    }

    @Override
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
        super.destroy();
    }

    @Override
    public boolean isActivated() {
        return activated;
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
        return isRunning() && entity!=null && !((EntityInternal)entity).getManagementSupport().isNoLongerManaged();
    }

    @Override
    public boolean isSuspended() {
        return suspended;
    }

    @Override
    public boolean isRunning() {
        return isActivated() && !isSuspended() && !isDestroyed() && getPoller()!=null && getPoller().isRunning();
    }

    @Override
    public RebindSupport<FeedMemento> getRebindSupport() {
        return new BasicFeedRebindSupport(this);
    }

    @Override
    protected void onChanged() {
        // TODO Auto-generated method stub
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
    
    /**
     * For overriding, where sub-class can change return-type generics!
     */
    protected Poller<?> getPoller() {
        return poller;
    }
}
