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
import brooklyn.entity.basic.AbstractEntity;
import brooklyn.entity.basic.ApplicationBuilder;
import brooklyn.entity.basic.Attributes;
import brooklyn.entity.basic.Entities;
import brooklyn.entity.proxying.EntitySpecs;
import brooklyn.event.AttributeSensor;
import brooklyn.event.SensorEvent;
import brooklyn.event.SensorEventListener;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.basic.BasicNotificationSensor;
import brooklyn.event.basic.Sensors;
import brooklyn.event.feed.ConfigToAttributes;
import brooklyn.location.Location;
import brooklyn.location.basic.SimulatedLocation;
import brooklyn.test.Asserts;
import brooklyn.test.GeneralisedDynamicMBean;
import brooklyn.test.JmxService;
import brooklyn.test.TestUtils;
import brooklyn.test.entity.TestApplication;
import brooklyn.test.entity.TestApplicationImpl;
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
    private TestApplication app;
    private TestEntity entity;
    private JmxFeed feed;
    private JmxHelper jmxHelper;
    
    private AttributeSensor<Integer> intAttribute = Sensors.newIntegerSensor("brooklyn.test.intAttribute", "Brooklyn testing int attribute");
    private AttributeSensor<String> stringAttribute = Sensors.newStringSensor("brooklyn.test.stringAttribute", "Brooklyn testing string attribute");
    private BasicAttributeSensor<Map> mapAttribute = new BasicAttributeSensor<Map>(Map.class, "brooklyn.test.mapAttribute", "Brooklyn testing map attribute");
    private String objectName = "Brooklyn:type=MyTestMBean,name=myname";
    private ObjectName jmxObjectName;
    private String attributeName = "myattrib";
    private String opName = "myop";
    
    public static class TestEntityWithJmx extends TestEntityImpl {
        @Override public void start(Collection<? extends Location> locs) {
            super.start(locs);
            setAttribute(Attributes.HOSTNAME, "localhost");
            setAttribute(Attributes.JMX_PORT, 40123);
            setAttribute(Attributes.RMI_SERVER_PORT, 40124);
            ConfigToAttributes.apply(this, Attributes.JMX_CONTEXT);
        }
    }
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        jmxObjectName = new ObjectName(objectName);
        
        // Create an entity and configure it with the above JMX service
        app = ApplicationBuilder.newManagedApp(TestApplication.class);
        entity = app.createAndManageChild(EntitySpecs.spec(TestEntity.class).impl(TestEntityWithJmx.class));
        app.start(ImmutableList.of(new SimulatedLocation()));

        jmxHelper = new JmxHelper(entity);
        
        jmxService = new JmxService(entity);
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() {
        if (feed != null) feed.stop();
        if (jmxHelper != null) jmxHelper.disconnect();
        if (jmxService != null) jmxService.shutdown();
        if (app != null) Entities.destroyAll(app.getManagementContext());
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
        final String one = "notification.one", two = "notification.two";
        final StandardEmitterMBean mbean = jmxService.registerMBean(ImmutableList.of(one, two), objectName);
        final AtomicInteger sequence = new AtomicInteger(0);

        feed = JmxFeed.builder()
                .entity(entity)
                .subscribeToNotification(new JmxNotificationSubscriptionConfig<Integer>(intAttribute)
                        .objectName(objectName)
                        .notificationFilter(JmxNotificationFilters.matchesType(one)))
                .build();        

        // Notification updates the sensor
        // Note that subscription is done async, so can't just send notification immediately during test.
        Asserts.succeedsEventually(ImmutableMap.of("timeout", TIMEOUT_MS), new Runnable() {
            public void run() {
                sendNotification(mbean, one, sequence.getAndIncrement(), 123);
                assertEquals(entity.getAttribute(intAttribute), (Integer)123);
            }});
        
        // But other notification types are ignored
        sendNotification(mbean, two, sequence.getAndIncrement(), -1);
            
        Asserts.succeedsEventually(ImmutableMap.of("timeout", TIMEOUT_MS), new Runnable() {
            public void run() {
                assertEquals(entity.getAttribute(intAttribute), (Integer)123);
            }});
    }
    
    @Test
    public void testJmxNotificationSubscriptionForSensorParsingNotification() throws Exception {
        final String one = "notification.one", two = "notification.two";
        final StandardEmitterMBean mbean = jmxService.registerMBean(ImmutableList.of(one, two), objectName);
        final AtomicInteger sequence = new AtomicInteger(0);
        
        feed = JmxFeed.builder()
                .entity(entity)
                .subscribeToNotification(new JmxNotificationSubscriptionConfig<Integer>(intAttribute)
                        .objectName(objectName)
                        .notificationFilter(JmxNotificationFilters.matchesType(one))
                        .onNotification(new Function<Notification, Integer>() {
                            public Integer apply(Notification notif) {
                                return (Integer) notif.getUserData();
                            }
                        }))
                .build();
        
        
        // Notification updates the sensor
        // Note that subscription is done async, so can't just send notification immediately during test.
        Asserts.succeedsEventually(ImmutableMap.of("timeout", TIMEOUT_MS), new Runnable() {
            public void run() {
                sendNotification(mbean, one, sequence.getAndIncrement(), 123);
                assertEquals(entity.getAttribute(intAttribute), (Integer)123);
            }});
    }

    @Test
    public void testJmxNotificationMultipleSubscriptionUsingListener() throws Exception {
        final String one = "notification.one";
        final String two = "notification.two";
        final StandardEmitterMBean mbean = jmxService.registerMBean(ImmutableList.of(one, two), objectName);
        final AtomicInteger sequence = new AtomicInteger(0);

        feed = JmxFeed.builder()
                .entity(entity)
                .subscribeToNotification(new JmxNotificationSubscriptionConfig<Integer>(intAttribute)
                        .objectName(objectName)
                        .notificationFilter(JmxNotificationFilters.matchesTypes(one, two)))
                .build();
        
        // Notification updates the sensor
        // Note that subscription is done async, so can't just send notification immediately during test.
        Asserts.succeedsEventually(ImmutableMap.of("timeout", TIMEOUT_MS), new Runnable() {
            public void run() {
                sendNotification(mbean, one, sequence.getAndIncrement(), 123);
                assertEquals(entity.getAttribute(intAttribute), (Integer)123);
            }});

        // And wildcard means other notifications also received
        sendNotification(mbean, two, sequence.getAndIncrement(), 456);
        assertSensorEventually(intAttribute, 456, TIMEOUT_MS);
    }

    // Test reproduces functionality used in Monterey, for Venue entity being told of requestActor
    @Test
    public void testSubscribeToJmxNotificationAndEmitCorrespondingNotificationSensor() throws Exception {
        TestApplication app2 = new TestApplicationImpl();
        final EntityWithEmitter entity = new EntityWithEmitter(app2);
        Entities.startManagement(app2);
        try {
            app2.start(ImmutableList.of(new SimulatedLocation()));
            
            final List<SensorEvent<String>> received = Lists.newArrayList();
            app2.subscribe(null, EntityWithEmitter.MY_NOTIF, new SensorEventListener<String>() {
                public void onEvent(SensorEvent<String> event) {
                    received.add(event);
                }});
    
            final StandardEmitterMBean mbean = jmxService.registerMBean(ImmutableList.of("one"), objectName);
            final AtomicInteger sequence = new AtomicInteger(0);
            
            jmxHelper.connect(TIMEOUT_MS);
            jmxHelper.addNotificationListener(jmxObjectName, new NotificationListener() {
                    public void handleNotification(Notification notif, Object callback) {
                        if (notif.getType().equals("one")) {
                            entity.emit(EntityWithEmitter.MY_NOTIF, (String) notif.getUserData());
                        }
                    }});
            

            Asserts.succeedsEventually(ImmutableMap.of("timeout", TIMEOUT_MS), new Runnable() {
                public void run() {
                    sendNotification(mbean, "one", sequence.getAndIncrement(), "abc");
                    assertTrue(received.size() > 0, "received size should be bigger than 0");
                    assertEquals(received.get(0).getValue(), "abc");
                }});
        } finally {
            Entities.destroyAll(app2);
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
