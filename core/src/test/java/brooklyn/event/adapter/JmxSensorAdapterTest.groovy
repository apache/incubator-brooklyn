package brooklyn.event.adapter

import static org.testng.Assert.*

import java.util.Map
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

import javax.management.DynamicMBean
import javax.management.MBeanOperationInfo
import javax.management.MBeanParameterInfo
import javax.management.Notification
import javax.management.NotificationListener
import javax.management.ObjectName
import javax.management.StandardEmitterMBean
import javax.management.openmbean.CompositeDataSupport
import javax.management.openmbean.CompositeType
import javax.management.openmbean.OpenType
import javax.management.openmbean.SimpleType
import javax.management.openmbean.TabularDataSupport
import javax.management.openmbean.TabularType

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.Entity
import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.basic.AbstractEntity
import brooklyn.entity.basic.Attributes
import brooklyn.event.SensorEvent
import brooklyn.event.SensorEventListener
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.event.basic.BasicNotificationSensor
import brooklyn.location.basic.SimulatedLocation
import brooklyn.test.GeneralisedDynamicMBean
import brooklyn.test.JmxService
import brooklyn.test.TestUtils
import brooklyn.test.entity.TestApplication
import brooklyn.test.entity.TestEntity

/**
 * Test the operation of the {@link OldJmxSensorAdapter} class.
 * 
 * TODO clarify test purpose
 */
public class JmxSensorAdapterTest {
    private static final Logger log = LoggerFactory.getLogger(JmxSensorAdapterTest.class)

    private static final int TIMEOUT = 5000
    private static final int SHORT_WAIT = 250
    
    private JmxService jmxService
    private AbstractApplication app
    private TestEntity entity
    SensorRegistry registry
    private JmxSensorAdapter jmxAdapter
    private JmxHelper jmxHelper
    
    private BasicAttributeSensor<Integer> intAttribute = [ Integer, "brooklyn.test.intAttribute", "Brooklyn testing int attribute" ]
    private BasicAttributeSensor<String> stringAttribute = [ String, "brooklyn.test.intAttribute", "Brooklyn testing int attribute" ]
    private BasicAttributeSensor<Map> mapAttribute = [ Map, "brooklyn.test.mapAttribute", "Brooklyn testing map attribute" ]
    private String objectName = 'Brooklyn:type=MyTestMBean,name=myname'
    private ObjectName jmxObjectName = new ObjectName('Brooklyn:type=MyTestMBean,name=myname')
    private String attributeName = 'myattrib'
    private String opName = 'myop'
    
    @BeforeMethod(alwaysRun=true)
    public void setUp() {
        // Create an entity and configure it with the above JMX service
        app = new AbstractApplication() {}
        entity = new TestEntity(owner:app) {
            void start(Collection locs) {
                        super.start(locs);
                        entity.setAttribute(Attributes.HOSTNAME, "localhost");
                        entity.setAttribute(Attributes.JMX_PORT)
                        entity.setAttribute(Attributes.RMI_PORT)
                        entity.setAttribute(Attributes.JMX_CONTEXT)
                    }
        };
        app.start([new SimulatedLocation()])
        
        registry = new SensorRegistry(entity);
        jmxAdapter = registry.register(new JmxSensorAdapter(period: 50*TimeUnit.MILLISECONDS));
        jmxHelper = new JmxHelper(entity)
        
        jmxService = new JmxService(entity)
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() {
        if (jmxHelper != null) jmxHelper.disconnect();
        if (jmxAdapter != null) registry.deactivateAdapters();
        if (jmxService != null) jmxService.shutdown();
    }

    @Test
    public void jmxAttributePollerReturnsMBeanAttribute() {
        GeneralisedDynamicMBean mbean = jmxService.registerMBean(objectName, (attributeName): 42)

        jmxAdapter.objectName(objectName).with {
            attribute(attributeName).subscribe(intAttribute)
        }
        registry.activateAdapters()
        
        // Starts with value defined when registering...
        TestUtils.executeUntilSucceeds(timeout:TIMEOUT) {
            assertEquals entity.getAttribute(intAttribute), 42
        }

        // Change the value and check it updates
        mbean.updateAttributeValue(attributeName, 64)
        TestUtils.executeUntilSucceeds(timeout:TIMEOUT) {
            assertEquals entity.getAttribute(intAttribute), 64
        }
    }

    @Test(expectedExceptions=[IllegalStateException.class])
    public void jmxCheckInstanceExistsEventuallyThrowsIfNotFound() {
        jmxHelper.connect(TIMEOUT)
        
        jmxHelper.checkMBeanExistsEventually(new ObjectName('Brooklyn:type=DoesNotExist,name=doesNotExist'), 1*TimeUnit.MILLISECONDS)
    }

    @Test
    public void jmxObjectCheckExistsEventuallyReturnsIfFoundImmediately() {
        GeneralisedDynamicMBean mbean = jmxService.registerMBean(objectName)
        jmxHelper.connect(TIMEOUT)
        
        jmxHelper.checkMBeanExistsEventually(jmxObjectName, 1*TimeUnit.MILLISECONDS)
    }

    @Test
    public void jmxObjectCheckExistsEventuallyTakingLongReturnsIfFoundImmediately() {
        GeneralisedDynamicMBean mbean = jmxService.registerMBean(objectName)
        jmxHelper.connect(TIMEOUT)
        
        jmxHelper.checkMBeanExistsEventually(jmxObjectName, 1L)
    }

    @Test
    public void jmxObjectCheckExistsEventuallyReturnsIfCreatedDuringPolling() {
        jmxHelper.connect(TIMEOUT)
        
        Thread t = new Thread(new Runnable() {
                public void run() {
                    Thread.sleep(SHORT_WAIT)
                    GeneralisedDynamicMBean mbean = jmxService.registerMBean(objectName)
                }})
        try {
            t.start()
            
            jmxHelper.checkMBeanExistsEventually(jmxObjectName, TIMEOUT)
        } finally {
            t.interrupt()
            t.join(TIMEOUT)
            assertFalse(t.isAlive())
        }        
    }

    @Test
    public void jmxAttributeOfTypeTabularDataProviderConvertedToMap() {
        // Create the CompositeType and TabularData
        CompositeType compositeType = new CompositeType(
                "typeName",
                "description",
                ["myint", "mystring", "mybool"] as String[],    // item names
                ["myint", "mystring", "mybool"] as String[],    // item descriptions, can't be null or empty string
                [SimpleType.INTEGER, SimpleType.STRING, SimpleType.BOOLEAN] as OpenType<?>[]
        )
        TabularType tt = new TabularType(
                "typeName",
                "description",
                compositeType,
                ["myint"] as String[]
        )
        TabularDataSupport tds = new TabularDataSupport(tt)
        tds.put(new CompositeDataSupport(
                compositeType,
                ["mybool", "myint", "mystring"] as String[],
                [true, 1234, "on"] as Object[]
        ))

        // Create MBean
        GeneralisedDynamicMBean mbean = jmxService.registerMBean(objectName, (attributeName): tds)

        jmxAdapter.objectName(objectName).with {
            attribute(attributeName).subscribe(mapAttribute, JmxPostProcessors.tabularDataToMap())
        }
        registry.activateAdapters()
        
        // Starts with value defined when registering...
        TestUtils.executeUntilSucceeds {
            Map<String, Object> map = entity.getAttribute(mapAttribute)
            assertNotNull map
            assertEquals 3, map.size()
            assertEquals 1234, map.get("myint")
            assertEquals "on", map.get("mystring")
            assertTrue map.get("mybool")
        }
    }

    @Test
    public void jmxOperationPolledForSensor() {
        // This is awful syntax...
        final int opReturnVal = 123
        final AtomicInteger invocationCount = new AtomicInteger()
        MBeanOperationInfo opInfo = new MBeanOperationInfo(opName, "my descr", new MBeanParameterInfo[0], String.class.name, MBeanOperationInfo.ACTION)
        GeneralisedDynamicMBean mbean = jmxService.registerMBean([:], [ (opInfo):{ Object[] args -> invocationCount.incrementAndGet(); opReturnVal } ], objectName)
        
        jmxAdapter.objectName(objectName).with {
            operation(opName).poll(intAttribute)
        }
        registry.activateAdapters()
        
        TestUtils.executeUntilSucceeds {
            assertTrue invocationCount.get() > 0, "invocationCount=$invocationCount"
            assertEquals entity.getAttribute(intAttribute), opReturnVal
        }
    }

    @Test
    public void jmxOperationWithArgPolledForSensor() {
        // This is awful syntax...
        MBeanParameterInfo paramInfo = new MBeanParameterInfo('param1', String.class.name, 'my param1')
        MBeanParameterInfo[] paramInfos = [ paramInfo ].toArray(new MBeanParameterInfo[1])
        MBeanOperationInfo opInfo = new MBeanOperationInfo(opName, "my descr", paramInfos, String.class.name, MBeanOperationInfo.ACTION)
        GeneralisedDynamicMBean mbean = jmxService.registerMBean([:], [ (opInfo):{ Object[] args -> args[0]+'suffix' } ], objectName)
        
        jmxAdapter.objectName(objectName).with {
            operation(opName, "myprefix").poll(stringAttribute)
        }
        registry.activateAdapters()
        
        TestUtils.executeUntilSucceeds {
            assertEquals entity.getAttribute(stringAttribute), "myprefix"+"suffix"
        }
    }

    @Test
    public void jmxNotificationSubscriptionForSensor() {
        String one = 'notification.one', two = 'notification.two'
        StandardEmitterMBean mbean = jmxService.registerMBean([ one, two ], objectName)
        int sequence = 0

        jmxAdapter.objectName(objectName).with {
            notification(one).subscribe(intAttribute)
        }
        registry.activateAdapters()
        
        // Notification updates the sensor
        sendNotification(mbean, one, sequence++, 123)

        TestUtils.executeUntilSucceeds(timeout:TIMEOUT) {
            assertEquals entity.getAttribute(intAttribute), 123
        }
        
        // But other notification types are ignored
        sendNotification(mbean, two, sequence++, -1)
            
        Thread.sleep(SHORT_WAIT)
        assertEquals entity.getAttribute(intAttribute), 123
    }
    
    @Test
    public void jmxNotificationSubscriptionUsingListener() {
        String one = 'notification.one', two = 'notification.two'
        StandardEmitterMBean mbean = jmxService.registerMBean([ one, two ], objectName)
        int sequence = 0
        List<Notification> received = []
        
        jmxAdapter.objectName(objectName).with {
            notification(one).subscribe({Notification notif, Object callback -> 
                    received.add(notif) } as NotificationListener)
        }
        registry.activateAdapters()
        
        Notification notif = sendNotification(mbean, one, sequence++, 123)
        
        TestUtils.executeUntilSucceeds(timeout:TIMEOUT) {
            assertEquals received.size(), 1
            assertNotificationsEqual received.get(0), notif
        }
    }

    @Test
    public void jmxNotificationWildcardSubscriptionUsingListener() {
        String one = 'notification.one', two = 'notification.two'
        StandardEmitterMBean mbean = jmxService.registerMBean([ one, two ], objectName)
        int sequence = 0
        List<Notification> received = []
        
        jmxAdapter.objectName(objectName).with {
            notification(".*").subscribe({Notification notif, Object callback -> 
                    received.add(notif) } as NotificationListener)
        }
        registry.activateAdapters()
        
        Notification notif = sendNotification(mbean, one, sequence++, 123)
        Notification notif2 = sendNotification(mbean, two, sequence++, 456)
        
        TestUtils.executeUntilSucceeds(timeout:TIMEOUT) {
            assertEquals received.size(), 2
            assertNotificationsEqual received.get(0), notif
            assertNotificationsEqual received.get(1), notif2
        }
    }

    @Test
    public void jmxNotificationSubscriptionUsingNoFilterForListener() {
        String one = 'notification.one', two = 'notification.two'
        StandardEmitterMBean mbean = jmxService.registerMBean([ one, two ], objectName)
        int sequence = 0
        List<Notification> received = []
        
        jmxAdapter.objectName(objectName).with {
            notification(".*").subscribe({Notification notif, Object callback ->
                    received.add(notif) } as NotificationListener)
        }
        registry.activateAdapters()
        
        Notification notif = sendNotification(mbean, one, sequence++, 123)
        Notification notif2 = sendNotification(mbean, one, sequence++, 456)
        
        TestUtils.executeUntilSucceeds(timeout:TIMEOUT) {
            assertEquals received.size(), 2
            assertNotificationsEqual received.get(0), notif
            assertNotificationsEqual received.get(1), notif2
        }
    }

    @Test
    public void testSubscribeToJmxNotificationsDirectlyWithJmxHelper() {
        StandardEmitterMBean mbean = jmxService.registerMBean(["one"], objectName)
        int sequence = 0
        List<Notification> received = []

        jmxHelper.connect(TIMEOUT)
        jmxHelper.addNotificationListener(jmxObjectName, {Notification notif, Object callback ->
                    received.add(notif) } as NotificationListener)

        Notification notif = sendNotification(mbean, "one", sequence++, "abc")

        TestUtils.executeUntilSucceeds(timeout:TIMEOUT) {
            assertEquals received.size(), 1
            assertNotificationsEqual(received.getAt(0), notif)
        }
    }

    @Test
    public void testSetAttribute() {
        DynamicMBean mbean = jmxService.registerMBean(["myattr":"myval"], objectName)

        jmxHelper.connect(TIMEOUT)
        jmxHelper.setAttribute(jmxObjectName, "myattr", "abc")
        Object actual = jmxHelper.getAttribute(jmxObjectName, "myattr")
        assertEquals(actual, "abc")
    }

    // Test reproduces functionality used in Monterey, for Venue entity being told of requestActor
    @Test
    public void testSubscribeToJmxNotificationAndEmitCorrespondingNotificationSensor() {
        TestApplication app = new TestApplication();
        EntityWithEmitter entity = new EntityWithEmitter(owner:app);
        app.start([new SimulatedLocation()])
        
        List<SensorEvent> received = []
        app.subscribe(null, EntityWithEmitter.MY_NOTIF, { received.add(it) } as SensorEventListener)

        StandardEmitterMBean mbean = jmxService.registerMBean(["one"], objectName)
        int sequence = 0
        
        jmxHelper.connect(TIMEOUT)
        jmxHelper.addNotificationListener(jmxObjectName, {Notification notif, Object callback ->
                if (notif.type.equals("one")) {
                    entity.emit(EntityWithEmitter.MY_NOTIF, notif.userData)
                } } as NotificationListener)
        
        Notification notif = sendNotification(mbean, "one", sequence++, "abc")

        TestUtils.executeUntilSucceeds(timeout:TIMEOUT) {
            assertEquals received.size(), 1
            assertEquals received.getAt(0).value, "abc"
        }
    }
    
    // Visual-inspection test that LOG.warn happens only once; TODO setup a listener to the logging output
    @Test
    public void testMBeanNotFoundLoggedOnlyOncePerUrl() {
        ObjectName wrongObjectName = new ObjectName('DoesNotExist:type=DoesNotExist')
        jmxHelper.connect();

        // Expect just one log message about:
        //     JMX object DoesNotExist:type=DoesNotExist not found at service:jmx:rmi://localhost:1099/jndi/rmi://localhost:9001/jmxrmi"
        for (int i = 0; i < 10; i++) {
            jmxHelper.findMBean(wrongObjectName)
        }

        jmxService.shutdown();
        jmxHelper.disconnect()
        
        jmxService = new JmxService("127.0.0.1", 9001)
        jmxHelper = new JmxHelper(jmxService.getUrl())
        jmxHelper.connect()
        
        // Expect just one log message about:
        //     JMX object DoesNotExist:type=DoesNotExist not found at service:jmx:rmi://127.0.0.1:1099/jndi/rmi://localhost:9001/jmxrmi"
        for (int i = 0; i < 10; i++) {
            jmxHelper.findMBean(wrongObjectName)
        }
    }

    static class EntityWithEmitter extends AbstractEntity {
        public EntityWithEmitter(Map flags=[:], Entity owner=null) {
            super(flags, owner)
        }
        public static final BasicNotificationSensor<String> MY_NOTIF = [ String, "test.myNotif", "My notif" ]
    }
    
    private Notification sendNotification(StandardEmitterMBean mbean, String type, long seq, Object userData) {
        Notification notif = new Notification(type, mbean, seq)
        notif.setUserData(userData)
        mbean.sendNotification(notif);
        return notif
    }
    
    private void assertNotificationsEqual(Notification n1, Notification n2) {
        assertEquals(n1.type, n2.type)
        assertEquals(n1.sequenceNumber, n2.sequenceNumber)
        assertEquals(n1.userData, n2.userData)
        assertEquals(n1.timeStamp, n2.timeStamp)
        assertEquals(n1.message, n2.message)
    }
}
