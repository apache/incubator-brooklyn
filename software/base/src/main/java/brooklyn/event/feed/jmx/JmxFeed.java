package brooklyn.event.feed.jmx;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import javax.management.Notification;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectName;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.basic.EntityLocal;
import brooklyn.entity.basic.SoftwareProcessImpl;
import brooklyn.event.feed.AbstractFeed;
import brooklyn.event.feed.AttributePollHandler;
import brooklyn.event.feed.DelegatingPollHandler;
import brooklyn.event.feed.PollHandler;
import brooklyn.event.feed.Poller;
import brooklyn.util.time.Duration;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;


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
        public JmxFeed build() {
            built = true;
            JmxFeed result = new JmxFeed(this);
            result.start();
            return result;
        }
        @Override
        protected void finalize() {
            if (!built) log.warn("JmxFeed.Builder created, but build() never called");
        }
    }

    private final JmxHelper helper;
    private final boolean ownHelper;
    private final String jmxUri;
    private final long jmxConnectionTimeout;
    
    // Treat as immutable; never modified after constructor
    private final SetMultimap<String, JmxAttributePollConfig<?>> attributePolls = HashMultimap.<String,JmxAttributePollConfig<?>>create();
    private final SetMultimap<List<?>, JmxOperationPollConfig<?>> operationPolls = HashMultimap.<List<?>,JmxOperationPollConfig<?>>create();
    private final SetMultimap<NotificationFilter, JmxNotificationSubscriptionConfig<?>> notificationSubscriptions = HashMultimap.create();
    private final SetMultimap<ObjectName, NotificationListener> notificationListeners = HashMultimap.create();

    protected JmxFeed(Builder builder) {
        super(builder.entity);
        this.helper = (builder.helper != null) ? builder.helper : new JmxHelper(entity);
        this.ownHelper = (builder.helper == null);
        this.jmxUri = helper.getUrl();
        this.jmxConnectionTimeout = builder.jmxConnectionTimeout;
        
        for (JmxAttributePollConfig<?> config : builder.attributePolls) {
            JmxAttributePollConfig<?> configCopy = new JmxAttributePollConfig(config);
            if (configCopy.getPeriod() < 0) configCopy.period(builder.period, builder.periodUnits);
            attributePolls.put(configCopy.getObjectName().getCanonicalName() + configCopy.getAttributeName(), configCopy);
        }
        for (JmxOperationPollConfig<?> config : builder.operationPolls) {
            JmxOperationPollConfig<?> configCopy = new JmxOperationPollConfig(config);
            if (configCopy.getPeriod() < 0) configCopy.period(builder.period, builder.periodUnits);
            operationPolls.put(configCopy.buildOperationIdentity(), configCopy);
        }
        for (JmxNotificationSubscriptionConfig<?> config : builder.notificationSubscriptions) {
            notificationSubscriptions.put(config.getNotificationFilter(), config);
        }
    }

    public String getJmxUri() {
        return jmxUri;
    }
    
    @VisibleForTesting
    @SuppressWarnings("unchecked")
    protected Poller<Object> getPoller() {
        return (Poller<Object>) poller;
    }
    
    protected boolean isConnected() {
        return super.isConnected() && helper.isConnected();
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
        
        getPoller().submit(new Callable<Void>() {
               public Void call() {
                   helper.connect(jmxConnectionTimeout);
                   return null;
               }});
        
        for (final NotificationFilter filter : notificationSubscriptions.keySet()) {
            getPoller().submit(new Callable<Void>() {
                public Void call() {
                    // TODO Could config.getObjectName have wildcards? Is this code safe?
                    Set<JmxNotificationSubscriptionConfig<?>> configs = notificationSubscriptions.get(filter);
                    NotificationListener listener = registerNotificationListener(configs);
                    ObjectName objectName = Iterables.get(configs, 0).getObjectName();
                    notificationListeners.put(objectName, listener);
                    return null;
                }});
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
        if (helper != null && ownHelper) helper.disconnect();
    }
    
    /**
     * Registers to poll a jmx-operation for an ObjectName, where all the given configs are for the same ObjectName + operation + parameters.
     */
    private void registerOperationPoller(Set<JmxOperationPollConfig<?>> configs) {
        Set<AttributePollHandler<Object>> handlers = Sets.newLinkedHashSet();
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
                        if (log.isDebugEnabled()) log.debug("jmx operation polling for {} sensors at {} -> {}", new Object[] {getEntity(), jmxUri, operationName});
                        if (signature.size() == params.size()) {
                            return helper.operation(objectName, operationName, signature, params);
                        } else {
                            return helper.operation(objectName, operationName, params.toArray());
                        }
                    }
                }, 
                new DelegatingPollHandler(handlers), minPeriod);
    }

    /**
     * Registers to poll a jmx-attribute for an ObjectName, where all the given configs are for that same ObjectName + attribute.
     */
    private void registerAttributePoller(Set<JmxAttributePollConfig<?>> configs) {
        Set<AttributePollHandler<Object>> handlers = Sets.newLinkedHashSet();
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
                        if (log.isTraceEnabled()) log.trace("jmx attribute polling for {} sensors at {} -> {}", new Object[] {getEntity(), jmxUri, jmxAttributeName});
                        return helper.getAttribute(objectName, jmxAttributeName);
                    }
                }, 
                new DelegatingPollHandler(handlers), minPeriod);
    }

    /**
     * Registers to subscribe to notifications for an ObjectName, where all the given configs are for that same ObjectName + filter.
     */
    private NotificationListener registerNotificationListener(Set<JmxNotificationSubscriptionConfig<?>> configs) {
        final List<AttributePollHandler<javax.management.Notification>> handlers = Lists.newArrayList();

        final ObjectName objectName = Iterables.get(configs, 0).getObjectName();
        final NotificationFilter filter = Iterables.get(configs, 0).getNotificationFilter();

        for (final JmxNotificationSubscriptionConfig<?> config : configs) {
            AttributePollHandler<javax.management.Notification> handler = new AttributePollHandler<javax.management.Notification>(config, getEntity(), this) {
                @Override protected Object transformValue(Object val) {
                    if (config.getOnNotification() != null) {
                        return config.getOnNotification().apply((javax.management.Notification)val);
                    } else {
                        return super.transformValue(((javax.management.Notification)val).getUserData());
                    }
                }
            };
            handlers.add(handler);
        }
        final PollHandler<javax.management.Notification> compoundHandler = new DelegatingPollHandler(handlers);
        
        NotificationListener listener = new NotificationListener() {
            @Override public void handleNotification(Notification notification, Object handback) {
                compoundHandler.onSuccess(notification);
            }
        };
        helper.addNotificationListener(objectName, listener, filter);
        
        return listener;
    }
    
    private void unregisterNotificationListener(ObjectName objectName, NotificationListener listener) {
        try {
            helper.removeNotificationListener(objectName, listener);
        } catch (RuntimeException e) {
            log.warn("Failed to unregister listener: "+objectName+", "+listener+"; continuing...", e);
        }
    }
}
