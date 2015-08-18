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
package brooklyn.event.feed.jmx;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import javax.management.Notification;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectName;

import org.apache.brooklyn.api.internal.EntityLocal;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.sensor.feed.AbstractFeed;
import org.apache.brooklyn.sensor.feed.AttributePollHandler;
import org.apache.brooklyn.sensor.feed.DelegatingPollHandler;
import org.apache.brooklyn.sensor.feed.PollHandler;
import org.apache.brooklyn.sensor.feed.Poller;
import org.apache.brooklyn.util.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.SoftwareProcessImpl;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import com.google.common.reflect.TypeToken;


/**
 * Provides a feed of attribute values, by polling or subscribing over jmx.
 * 
 * Example usage (e.g. in an entity that extends {@link SoftwareProcessImpl}):
 * <pre>
 * {@code
 * private JmxFeed feed;
 * 
 * //@Override
 * protected void connectSensors() {
 *   super.connectSensors();
 *   
 *   feed = JmxFeed.builder()
 *       .entity(this)
 *       .period(500, TimeUnit.MILLISECONDS)
 *       .pollAttribute(new JmxAttributePollConfig<Integer>(ERROR_COUNT)
 *           .objectName(requestProcessorMbeanName)
 *           .attributeName("errorCount"))
 *       .pollAttribute(new JmxAttributePollConfig<Boolean>(SERVICE_UP)
 *           .objectName(serverMbeanName)
 *           .attributeName("Started")
 *           .onError(Functions.constant(false)))
 *       .build();
 * }
 * 
 * {@literal @}Override
 * protected void disconnectSensors() {
 *   super.disconnectSensors();
 *   if (feed != null) feed.stop();
 * }
 * }
 * </pre>
 * 
 * @author aled
 */
public class JmxFeed extends AbstractFeed {

    public static final Logger log = LoggerFactory.getLogger(JmxFeed.class);

    public static final long JMX_CONNECTION_TIMEOUT_MS = 120*1000;

    public static final ConfigKey<JmxHelper> HELPER = ConfigKeys.newConfigKey(JmxHelper.class, "helper");
    public static final ConfigKey<Boolean> OWN_HELPER = ConfigKeys.newBooleanConfigKey("ownHelper");
    public static final ConfigKey<String> JMX_URI = ConfigKeys.newStringConfigKey("jmxUri");
    public static final ConfigKey<Long> JMX_CONNECTION_TIMEOUT = ConfigKeys.newLongConfigKey("jmxConnectionTimeout");
    
    @SuppressWarnings("serial")
    public static final ConfigKey<SetMultimap<String, JmxAttributePollConfig<?>>> ATTRIBUTE_POLLS = ConfigKeys.newConfigKey(
            new TypeToken<SetMultimap<String, JmxAttributePollConfig<?>>>() {},
            "attributePolls");

    @SuppressWarnings("serial")
    public static final ConfigKey<SetMultimap<List<?>, JmxOperationPollConfig<?>>> OPERATION_POLLS = ConfigKeys.newConfigKey(
            new TypeToken<SetMultimap<List<?>, JmxOperationPollConfig<?>>>() {},
            "operationPolls");

    @SuppressWarnings("serial")
    public static final ConfigKey<SetMultimap<NotificationFilter, JmxNotificationSubscriptionConfig<?>>> NOTIFICATION_SUBSCRIPTIONS = ConfigKeys.newConfigKey(
            new TypeToken<SetMultimap<NotificationFilter, JmxNotificationSubscriptionConfig<?>>>() {},
            "notificationPolls");

    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private EntityLocal entity;
        private JmxHelper helper;
        private long jmxConnectionTimeout = JMX_CONNECTION_TIMEOUT_MS;
        private long period = 500;
        private TimeUnit periodUnits = TimeUnit.MILLISECONDS;
        private List<JmxAttributePollConfig<?>> attributePolls = Lists.newArrayList();
        private List<JmxOperationPollConfig<?>> operationPolls = Lists.newArrayList();
        private List<JmxNotificationSubscriptionConfig<?>> notificationSubscriptions = Lists.newArrayList();
        private String uniqueTag;
        private volatile boolean built;
        
        public Builder entity(EntityLocal val) {
            this.entity = val;
            return this;
        }
        public Builder helper(JmxHelper val) {
            this.helper = val;
            return this;
        }
        public Builder period(Duration duration) {
            return period(duration.toMilliseconds(), TimeUnit.MILLISECONDS);
        }
        public Builder period(long millis) {
            return period(millis, TimeUnit.MILLISECONDS);
        }
        public Builder period(long val, TimeUnit units) {
            this.period = val;
            this.periodUnits = units;
            return this;
        }
        public Builder pollAttribute(JmxAttributePollConfig<?> config) {
            attributePolls.add(config);
            return this;
        }
        public Builder pollOperation(JmxOperationPollConfig<?> config) {
            operationPolls.add(config);
            return this;
        }
        public Builder subscribeToNotification(JmxNotificationSubscriptionConfig<?> config) {
            notificationSubscriptions.add(config);
            return this;
        }
        public Builder uniqueTag(String uniqueTag) {
            this.uniqueTag = uniqueTag;
            return this;
        }
        public JmxFeed build() {
            built = true;
            JmxFeed result = new JmxFeed(this);
            result.setEntity(checkNotNull(entity, "entity"));
            result.start();
            return result;
        }
        @Override
        protected void finalize() {
            if (!built) log.warn("JmxFeed.Builder created, but build() never called");
        }
    }

    private final SetMultimap<ObjectName, NotificationListener> notificationListeners = HashMultimap.create();

    /**
     * For rebind; do not call directly; use builder
     */
    public JmxFeed() {
    }

    protected JmxFeed(Builder builder) {
        super();
        if (builder.helper != null) {
            JmxHelper helper = builder.helper;
            setConfig(HELPER, helper);
            setConfig(OWN_HELPER, false);
            setConfig(JMX_URI, helper.getUrl());
        }
        setConfig(JMX_CONNECTION_TIMEOUT, builder.jmxConnectionTimeout);
        
        SetMultimap<String, JmxAttributePollConfig<?>> attributePolls = HashMultimap.<String,JmxAttributePollConfig<?>>create();
        for (JmxAttributePollConfig<?> config : builder.attributePolls) {
            if (!config.isEnabled()) continue;
            @SuppressWarnings({ "rawtypes", "unchecked" })
            JmxAttributePollConfig<?> configCopy = new JmxAttributePollConfig(config);
            if (configCopy.getPeriod() < 0) configCopy.period(builder.period, builder.periodUnits);
            attributePolls.put(configCopy.getObjectName().getCanonicalName() + configCopy.getAttributeName(), configCopy);
        }
        setConfig(ATTRIBUTE_POLLS, attributePolls);
        
        SetMultimap<List<?>, JmxOperationPollConfig<?>> operationPolls = HashMultimap.<List<?>,JmxOperationPollConfig<?>>create();
        for (JmxOperationPollConfig<?> config : builder.operationPolls) {
            if (!config.isEnabled()) continue;
            @SuppressWarnings({ "rawtypes", "unchecked" })
            JmxOperationPollConfig<?> configCopy = new JmxOperationPollConfig(config);
            if (configCopy.getPeriod() < 0) configCopy.period(builder.period, builder.periodUnits);
            operationPolls.put(configCopy.buildOperationIdentity(), configCopy);
        }
        setConfig(OPERATION_POLLS, operationPolls);
        
        SetMultimap<NotificationFilter, JmxNotificationSubscriptionConfig<?>> notificationSubscriptions = HashMultimap.create();
        for (JmxNotificationSubscriptionConfig<?> config : builder.notificationSubscriptions) {
            if (!config.isEnabled()) continue;
            notificationSubscriptions.put(config.getNotificationFilter(), config);
        }
        setConfig(NOTIFICATION_SUBSCRIPTIONS, notificationSubscriptions);
        initUniqueTag(builder.uniqueTag, attributePolls, operationPolls, notificationSubscriptions);
    }

    @Override
    public void setEntity(EntityLocal entity) {
        if (getConfig(HELPER) == null) {
            JmxHelper helper = new JmxHelper(entity);
            setConfig(HELPER, helper);
            setConfig(OWN_HELPER, true);
            setConfig(JMX_URI, helper.getUrl());
        }
        super.setEntity(entity);
    }
    
    public String getJmxUri() {
        return getConfig(JMX_URI);
    }
    
    protected JmxHelper getHelper() {
        return getConfig(HELPER);
    }
    
    @SuppressWarnings("unchecked")
    protected Poller<Object> getPoller() {
        return (Poller<Object>) super.getPoller();
    }
    
    @Override
    protected boolean isConnected() {
        return super.isConnected() && getHelper().isConnected();
    }
    
    @Override
    protected void preStart() {
        /*
         * All actions on the JmxHelper are done async (through the poller's threading) so we don't 
         * block on start for a long time (e.g. if the entity is not contactable and doing a rebind 
         * on restart of brooklyn). Without that, one gets a 120 second pause with it stuck in a 
         * stack trace like:
         * 
         *      at brooklyn.event.feed.jmx.JmxHelper.sleep(JmxHelper.java:640)
         *      at brooklyn.event.feed.jmx.JmxHelper.connect(JmxHelper.java:320)
         *      at brooklyn.event.feed.jmx.JmxFeed.preStart(JmxFeed.java:172)
         *      at brooklyn.event.feed.AbstractFeed.start(AbstractFeed.java:68)
         *      at brooklyn.event.feed.jmx.JmxFeed$Builder.build(JmxFeed.java:119)
         *      at brooklyn.entity.java.JavaAppUtils.connectMXBeanSensors(JavaAppUtils.java:109)
         *      at brooklyn.entity.java.VanillaJavaApp.connectSensors(VanillaJavaApp.java:97)
         *      at brooklyn.entity.basic.SoftwareProcessImpl.callRebindHooks(SoftwareProcessImpl.java:189)
         *      at brooklyn.entity.basic.SoftwareProcessImpl.rebind(SoftwareProcessImpl.java:235)
         *      ...
         *      at brooklyn.entity.rebind.RebindManagerImpl.rebind(RebindManagerImpl.java:184)
         */
        final SetMultimap<NotificationFilter, JmxNotificationSubscriptionConfig<?>> notificationSubscriptions = getConfig(NOTIFICATION_SUBSCRIPTIONS);
        final SetMultimap<List<?>, JmxOperationPollConfig<?>> operationPolls = getConfig(OPERATION_POLLS);
        final SetMultimap<String, JmxAttributePollConfig<?>> attributePolls = getConfig(ATTRIBUTE_POLLS);
        
        getPoller().submit(new Callable<Void>() {
               public Void call() {
                   getHelper().connect(getConfig(JMX_CONNECTION_TIMEOUT));
                   return null;
               }
               @Override public String toString() { return "Connect JMX "+getHelper().getUrl(); }
           });
        
        for (final NotificationFilter filter : notificationSubscriptions.keySet()) {
            getPoller().submit(new Callable<Void>() {
                public Void call() {
                    // TODO Could config.getObjectName have wildcards? Is this code safe?
                    Set<JmxNotificationSubscriptionConfig<?>> configs = notificationSubscriptions.get(filter);
                    NotificationListener listener = registerNotificationListener(configs);
                    ObjectName objectName = Iterables.get(configs, 0).getObjectName();
                    notificationListeners.put(objectName, listener);
                    return null;
                }
                @Override public String toString() { return "Register JMX notifications: "+notificationSubscriptions.get(filter); }
            });
        }
        
        // Setup polling of sensors
        for (final String jmxAttributeName : attributePolls.keys()) {
            registerAttributePoller(attributePolls.get(jmxAttributeName));
        }
        
        // Setup polling of operations
        for (final List<?> operationIdentifier : operationPolls.keys()) {
            registerOperationPoller(operationPolls.get(operationIdentifier));
        }
    }
    
    @Override
    protected void preStop() {
        super.preStop();

        for (Map.Entry<ObjectName, NotificationListener> entry : notificationListeners.entries()) {
            unregisterNotificationListener(entry.getKey(), entry.getValue());
        }
        notificationListeners.clear();
    }
    
    @Override
    protected void postStop() {
        super.postStop();
        JmxHelper helper = getHelper();
        Boolean ownHelper = getConfig(OWN_HELPER);
        if (helper != null && ownHelper) helper.terminate();
    }
    
    /**
     * Registers to poll a jmx-operation for an ObjectName, where all the given configs are for the same ObjectName + operation + parameters.
     */
    private void registerOperationPoller(Set<JmxOperationPollConfig<?>> configs) {
        Set<AttributePollHandler<? super Object>> handlers = Sets.newLinkedHashSet();
        long minPeriod = Integer.MAX_VALUE;
        
        final ObjectName objectName = Iterables.get(configs, 0).getObjectName();
        final String operationName = Iterables.get(configs, 0).getOperationName();
        final List<String> signature = Iterables.get(configs, 0).getSignature();
        final List<?> params = Iterables.get(configs, 0).getParams();
        
        for (JmxOperationPollConfig<?> config : configs) {
            handlers.add(new AttributePollHandler<Object>(config, getEntity(), this));
            if (config.getPeriod() > 0) minPeriod = Math.min(minPeriod, config.getPeriod());
        }
        
        getPoller().scheduleAtFixedRate(
                new Callable<Object>() {
                    public Object call() throws Exception {
                        if (log.isDebugEnabled()) log.debug("jmx operation polling for {} sensors at {} -> {}", new Object[] {getEntity(), getJmxUri(), operationName});
                        if (signature.size() == params.size()) {
                            return getHelper().operation(objectName, operationName, signature, params);
                        } else {
                            return getHelper().operation(objectName, operationName, params.toArray());
                        }
                    }
                }, 
                new DelegatingPollHandler<Object>(handlers), minPeriod);
    }

    /**
     * Registers to poll a jmx-attribute for an ObjectName, where all the given configs are for that same ObjectName + attribute.
     */
    private void registerAttributePoller(Set<JmxAttributePollConfig<?>> configs) {
        Set<AttributePollHandler<? super Object>> handlers = Sets.newLinkedHashSet();
        long minPeriod = Integer.MAX_VALUE;
        
        final ObjectName objectName = Iterables.get(configs, 0).getObjectName();
        final String jmxAttributeName = Iterables.get(configs, 0).getAttributeName();
        
        for (JmxAttributePollConfig<?> config : configs) {
            handlers.add(new AttributePollHandler<Object>(config, getEntity(), this));
            if (config.getPeriod() > 0) minPeriod = Math.min(minPeriod, config.getPeriod());
        }
        
        // TODO Not good calling this holding the synchronization lock
        getPoller().scheduleAtFixedRate(
                new Callable<Object>() {
                    public Object call() throws Exception {
                        if (log.isTraceEnabled()) log.trace("jmx attribute polling for {} sensors at {} -> {}", new Object[] {getEntity(), getJmxUri(), jmxAttributeName});
                        return getHelper().getAttribute(objectName, jmxAttributeName);
                    }
                }, 
                new DelegatingPollHandler<Object>(handlers), minPeriod);
    }

    /**
     * Registers to subscribe to notifications for an ObjectName, where all the given configs are for that same ObjectName + filter.
     */
    private NotificationListener registerNotificationListener(Set<JmxNotificationSubscriptionConfig<?>> configs) {
        final List<AttributePollHandler<? super javax.management.Notification>> handlers = Lists.newArrayList();

        final ObjectName objectName = Iterables.get(configs, 0).getObjectName();
        final NotificationFilter filter = Iterables.get(configs, 0).getNotificationFilter();

        for (final JmxNotificationSubscriptionConfig<?> config : configs) {
            AttributePollHandler<javax.management.Notification> handler = new AttributePollHandler<javax.management.Notification>(config, getEntity(), this) {
                @Override protected Object transformValueOnSuccess(javax.management.Notification val) {
                    if (config.getOnNotification() != null) {
                        return config.getOnNotification().apply(val);
                    } else {
                        Object result = super.transformValueOnSuccess(val);
                        if (result instanceof javax.management.Notification)
                            return ((javax.management.Notification)result).getUserData();
                        return result;
                    }
                }
            };
            handlers.add(handler);
        }
        final PollHandler<javax.management.Notification> compoundHandler = new DelegatingPollHandler<javax.management.Notification>(handlers);
        
        NotificationListener listener = new NotificationListener() {
            @Override public void handleNotification(Notification notification, Object handback) {
                compoundHandler.onSuccess(notification);
            }
        };
        getHelper().addNotificationListener(objectName, listener, filter);
        
        return listener;
    }
    
    private void unregisterNotificationListener(ObjectName objectName, NotificationListener listener) {
        try {
            getHelper().removeNotificationListener(objectName, listener);
        } catch (RuntimeException e) {
            log.warn("Failed to unregister listener: "+objectName+", "+listener+"; continuing...", e);
        }
    }
    
    @Override
    public String toString() {
        return "JmxFeed["+(getManagementContext()!=null&&getManagementContext().isRunning()?getJmxUri():"mgmt-not-running")+"]";
    }
}
