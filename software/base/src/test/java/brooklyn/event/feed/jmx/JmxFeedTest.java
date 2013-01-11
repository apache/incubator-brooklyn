package brooklyn.event.feed.jmx;

import static brooklyn.test.TestUtils.executeUntilSucceeds;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import javax.management.MBeanOperationInfo;
import javax.management.MBeanParameterInfo;
import javax.management.Notification;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.management.StandardEmitterMBean;
import javax.management.openmbean.CompositeDataSupport;
import javax.management.openmbean.CompositeType;
import javax.management.openmbean.OpenType;
import javax.management.openmbean.SimpleType;
import javax.management.openmbean.TabularDataSupport;
import javax.management.openmbean.TabularType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.testng.collections.Lists;

import brooklyn.entity.Entity;
import brooklyn.entity.basic.AbstractApplication;
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.Entities;
import brooklyn.event.AttributeSensor;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.basic.BasicNotificationSensor;
import brooklyn.location.Location;
import brooklyn.location.basic.SimulatedLocation;
import brooklyn.test.GeneralisedDynamicMBean;
import brooklyn.test.JmxService;
import brooklyn.test.TestUtils;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestEntity;
import brooklyn.test.entity.TestEntityImpl;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/**
 * Test the operation of the {@link OldJmxSensorAdapter} class.
 * 
 * TODO clarify test purpose
 */
public class JmxFeedTest {
    
    // FIXME Move out the JmxHelper tests into the JmxHelperTest class
    
    // FIXME Also test that setting poll period takes effect
    
    private static final Logger log = LoggerFactory.getLogger(JmxFeedTest.class);

    private static final int TIMEOUT_MS = 5000;
    private static final int SHORT_WAIT_MS = 250;
    
    private JmxService jmxService;
    private AbstractApplication app;
    private TestEntity entity;
    private JmxFeed feed;
    private JmxHelper jmxHelper;
    
    private AttributeSensor<Integer> intAttribute = new BasicAttributeSensor<Integer>(Integer.class, "brooklyn.test.intAttribute", "Brooklyn testing int attribute");
    private BasicAttributeSensor<String> stringAttribute = new BasicAttributeSensor<String>(String.class, "brooklyn.test.stringAttribute", "Brooklyn testing string attribute");
    private BasicAttributeSensor<Map> mapAttribute = new BasicAttributeSensor<Map>(Map.class, "brooklyn.test.mapAttribute", "Brooklyn testing map attribute");
    private String objectName = "Brooklyn:type=MyTestMBean,name=myname";
    private ObjectName jmxObjectName;
    private String attributeName = "myattrib";
    private String opName = "myop";
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        jmxObjectName = new ObjectName(objectName);
        
        // Create an entity and configure it with the above JMX service
        app = new AbstractApplication() {};
        entity = new TestEntityImpl(app) {
            @Override public void start(Collection<? extends Location> locs) {
                        super.start(locs);
                        setAttribute(Attributes.HOSTNAME, "localhost");
                        setAttribute(Attributes.JMX_PORT, 40123);
                        setAttribute(Attributes.RMI_PORT, 40124);
                        setAttribute(Attributes.JMX_CONTEXT);
                    }
        };
        Entities.startManagement(app);
        app.start(ImmutableList.of(new SimulatedLocation()));

        jmxHelper = new JmxHelper(entity);
        
        jmxService = new JmxService(entity);
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() {
        if (feed != null) feed.stop();
        if (jmxHelper != null) jmxHelper.disconnect();
        if (jmxService != null) jmxService.shutdown();
        if (app != null) Entities.destroyAll(app);
        feed = null;
    }

    @Test
    public void testJmxAttributePollerReturnsMBeanAttribute() throws Exception {
        GeneralisedDynamicMBean mbean = jmxService.registerMBean(ImmutableMap.of(attributeName, 42), objectName);

        feed = JmxFeed.builder()
                .entity(entity)
                .pollAttribute(new JmxAttributePollConfig<Integer>(intAttribute)
                        .objectName(objectName)
                        .period(50)
                        .attributeName(attributeName))
                .build();
        
        // Starts with value defined when registering...
        assertSensorEventually(intAttribute, 42, TIMEOUT_MS);

        // Change the value and check it updates
        mbean.updateAttributeValue(attributeName, 64);
        assertSensorEventually(intAttribute, 64, TIMEOUT_MS);
    }

    @Test
    public void testJmxAttributeOfTypeTabularDataProviderConvertedToMap() throws Exception {
        // Create the CompositeType and TabularData
        CompositeType compositeType = new CompositeType(
                "typeName",
                "description",
                new String[] {"myint", "mystring", "mybool"},    // item names
                new String[] {"myint", "mystring", "mybool"},    // item descriptions, can't be null or empty string
                new OpenType<?>[] {SimpleType.INTEGER, SimpleType.STRING, SimpleType.BOOLEAN}
        );
        TabularType tt = new TabularType(
                "typeName",
                "description",
                compositeType,
                new String[] {"myint"}
        );
        TabularDataSupport tds = new TabularDataSupport(tt);
        tds.put(new CompositeDataSupport(
                compositeType,
                new String[] {"mybool", "myint", "mystring"},
                new Object[] {true, 1234, "on"}
        ));

        // Create MBean
        GeneralisedDynamicMBean mbean = jmxService.registerMBean(ImmutableMap.of(attributeName, tds), objectName);

        feed = JmxFeed.builder()
                .entity(entity)
                .pollAttribute(new JmxAttributePollConfig<Map>(mapAttribute)
                        .objectName(objectName)
                        .attributeName(attributeName)
                        .onSuccess((Function)JmxValueFunctions.tabularDataToMap()))
                .build();

        // Starts with value defined when registering...
        assertSensorEventually(
                mapAttribute, 
                ImmutableMap.of("myint", 1234, "mystring", "on", "mybool", Boolean.TRUE), 
                TIMEOUT_MS);
    }

    @Test
    public void testJmxOperationPolledForSensor() throws Exception {
        // This is awful syntax...
        final int opReturnVal = 123;
        final AtomicInteger invocationCount = new AtomicInteger();
        MBeanOperationInfo opInfo = new MBeanOperationInfo(opName, "my descr", new MBeanParameterInfo[0], Integer.class.getName(), MBeanOperationInfo.ACTION);
        GeneralisedDynamicMBean mbean = jmxService.registerMBean(
                Collections.emptyMap(), 
                ImmutableMap.of(opInfo, new Function<Object[], Integer>() {
                        public Integer apply(Object[] args) {
                            invocationCount.incrementAndGet(); return opReturnVal;
                        }}),
                objectName);

        feed = JmxFeed.builder()
                .entity(entity)
                .pollOperation(new JmxOperationPollConfig<Integer>(intAttribute)
                        .objectName(objectName)
                        .operationName(opName))
                .build();
        
        TestUtils.executeUntilSucceeds(ImmutableMap.of("timeout", TIMEOUT_MS), new Runnable() {
            public void run() {
                assertTrue(invocationCount.get() > 0, "invocationCount="+invocationCount);
                assertEquals(entity.getAttribute(intAttribute), (Integer)opReturnVal);
            }});
    }

    @Test
    public void testJmxOperationWithArgPolledForSensor() throws Exception {
        // This is awful syntax...
        MBeanParameterInfo paramInfo = new MBeanParameterInfo("param1", String.class.getName(), "my param1");
        MBeanParameterInfo[] paramInfos = new MBeanParameterInfo[] {paramInfo};
        MBeanOperationInfo opInfo = new MBeanOperationInfo(opName, "my descr", paramInfos, String.class.getName(), MBeanOperationInfo.ACTION);
        GeneralisedDynamicMBean mbean = jmxService.registerMBean(
                Collections.emptyMap(), 
                ImmutableMap.of(opInfo, new Function<Object[], String>() {
                        public String apply(Object[] args) {
                            return args[0]+"suffix";
                        }}),
                objectName);
        
        feed = JmxFeed.builder()
                .entity(entity)
                .pollOperation(new JmxOperationPollConfig<String>(stringAttribute)
                        .objectName(objectName)
                        .operationName(opName)
                        .operationParams(ImmutableList.of("myprefix")))
                .build();
        
        assertSensorEventually(stringAttribute, "myprefix"+"suffix", TIMEOUT_MS);
    }

    @Test
    public void testJmxNotificationSubscriptionForSensor() throws Exception {
        String one = "notification.one", two = "notification.two";
        StandardEmitterMBean mbean = jmxService.registerMBean(ImmutableList.of(one, two), objectName);
        int sequence = 0;

        feed = JmxFeed.builder()
                .entity(entity)
                .subscribeToNotification(new JmxNotificationSubscriptionConfig<Integer>(intAttribute)
                        .objectName(objectName)
                        .notificationFilterByTypeRegex(one))
                .build();        
        
        // Notification updates the sensor
        sendNotification(mbean, one, sequence++, 123);

        TestUtils.executeUntilSucceeds(ImmutableMap.of("timeout", TIMEOUT_MS), new Runnable() {
            public void run() {
                assertEquals(entity.getAttribute(intAttribute), (Integer)123);
            }});
        
        // But other notification types are ignored
        sendNotification(mbean, two, sequence++, -1);
            
        TestUtils.executeUntilSucceeds(ImmutableMap.of("timeout", TIMEOUT_MS), new Runnable() {
            public void run() {
                assertEquals(entity.getAttribute(intAttribute), (Integer)123);
            }});
    }
    
    @Test
    public void testJmxNotificationSubscriptionForSensorParsingNotification() throws Exception {
        String one = "notification.one", two = "notification.two";
        StandardEmitterMBean mbean = jmxService.registerMBean(ImmutableList.of(one, two), objectName);
        int sequence = 0;
        List<Notification> received = Lists.newArrayList();
        
        feed = JmxFeed.builder()
                .entity(entity)
                .subscribeToNotification(new JmxNotificationSubscriptionConfig<Integer>(intAttribute)
                        .objectName(objectName)
                        .notificationFilterByTypeRegex(one)
                        .onNotification(new Function<Notification, Integer>() {
                            public Integer apply(Notification notif) {
                                return (Integer) notif.getUserData();
                            }
                        }))
                .build();
        
        Notification notif = sendNotification(mbean, one, sequence++, 123);
        
        TestUtils.executeUntilSucceeds(ImmutableMap.of("timeout", TIMEOUT_MS), new Runnable() {
            public void run() {
                assertEquals(entity.getAttribute(intAttribute), (Integer)123);
            }});
    }

    @Test
    public void testJmxNotificationWildcardSubscriptionUsingListener() throws Exception {
        String one = "notification.one", two = "notification.two";
        StandardEmitterMBean mbean = jmxService.registerMBean(ImmutableList.of(one, two), objectName);
        int sequence = 0;

        feed = JmxFeed.builder()
                .entity(entity)
                .subscribeToNotification(new JmxNotificationSubscriptionConfig<Integer>(intAttribute)
                        .objectName(objectName)
                        .notificationFilterByTypeRegex(".*"))
                .build();
        
        // Notification updates the sensor
        sendNotification(mbean, one, sequence++, 123);

        assertSensorEventually(intAttribute, 123, TIMEOUT_MS);
        
        // But other notification types are ignored
        sendNotification(mbean, two, sequence++, 456);

        // FIXME Is this right, based on comment above?
        assertSensorEventually(intAttribute, 456, TIMEOUT_MS);
    }

    // Test reproduces functionality used in Monterey, for Venue entity being told of requestActor
    @Test
    public void testSubscribeToJmxNotificationAndEmitCorrespondingNotificationSensor() throws Exception {
        TestApplication app = new TestApplication();
        final EntityWithEmitter entity = new EntityWithEmitter(app);
        Entities.startManagement(app);
        try {
            app.start(ImmutableList.of(new SimulatedLocation()));
            
            final List<SensorEvent<String>> received = Lists.newArrayList();
            app.subscribe(null, EntityWithEmitter.MY_NOTIF, new SensorEventListener<String>() {
                public void onEvent(SensorEvent<String> event) {
                    received.add(event);
                }});
    
            StandardEmitterMBean mbean = jmxService.registerMBean(ImmutableList.of("one"), objectName);
            int sequence = 0;
            
            jmxHelper.connect(TIMEOUT_MS);
            jmxHelper.addNotificationListener(jmxObjectName, new NotificationListener() {
                    public void handleNotification(Notification notif, Object callback) {
                        if (notif.getType().equals("one")) {
                            entity.emit(EntityWithEmitter.MY_NOTIF, (String) notif.getUserData());
                        }
                    }});
            
            Notification notif = sendNotification(mbean, "one", sequence++, "abc");
    
            TestUtils.executeUntilSucceeds(ImmutableMap.of("timeout", TIMEOUT_MS), new Runnable() {
                public void run() {
                    assertEquals(received.size(), 1);
                    assertEquals(received.get(0).getValue(), "abc");
                }});
        } finally {
            if (app != null) Entities.destroyAll(app);
        }
    }
    
    public static class EntityWithEmitter extends AbstractEntity {
        public static final BasicNotificationSensor<String> MY_NOTIF = new BasicNotificationSensor<String>(String.class, "test.myNotif", "My notif");
        
        public EntityWithEmitter(Entity owner) {
            super(owner);
        }
        public EntityWithEmitter(Map flags) {
            super(flags);
        }
        public EntityWithEmitter(Map flags, Entity owner) {
            super(flags, owner);
        }
    }
    
    private Notification sendNotification(StandardEmitterMBean mbean, String type, long seq, Object userData) {
        Notification notif = new Notification(type, mbean, seq);
        notif.setUserData(userData);
        mbean.sendNotification(notif);
        return notif;
    }
    
    private <T> void assertSensorEventually(final AttributeSensor<T> sensor, final T expectedVal, long timeout) {
        executeUntilSucceeds(ImmutableMap.of("timeout", timeout), new Callable<Void>() {
            public Void call() {
                assertEquals(entity.getAttribute(sensor), expectedVal);
                return null;
            }});
    }
}
