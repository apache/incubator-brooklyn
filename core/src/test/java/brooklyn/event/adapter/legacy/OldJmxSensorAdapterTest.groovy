package brooklyn.event.adapter.legacy

import static org.testng.Assert.*

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

import javax.management.MBeanOperationInfo
import javax.management.MBeanParameterInfo
import javax.management.MBeanServerConnection
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
import javax.management.remote.JMXConnector
import javax.management.remote.JMXConnectorFactory
import javax.management.remote.JMXServiceURL

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.AfterMethod
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test

import brooklyn.entity.LocallyManagedEntity
import brooklyn.entity.basic.Attributes
import brooklyn.event.adapter.legacy.OldJmxSensorAdapter;
import brooklyn.event.adapter.legacy.ValueProvider;
import brooklyn.test.GeneralisedDynamicMBean
import brooklyn.test.JmxService

/**
 * Test the operation of the {@link OldJmxSensorAdapter} class.
 * 
 * TODO clarify test purpose
 */
public class OldJmxSensorAdapterTest {
    private static final Logger log = LoggerFactory.getLogger(OldJmxSensorAdapterTest.class)

    JmxService jmxService;
    LocallyManagedEntity entity;
    
    @BeforeMethod
    public void setUp() {
        jmxService = new JmxService()
        
        // Create an entity and configure it with the above JMX service
        entity = new LocallyManagedEntity()
        entity.setAttribute(Attributes.HOSTNAME, jmxService.jmxHost)
        entity.setAttribute(Attributes.JMX_PORT, jmxService.jmxPort)
        entity.setAttribute(Attributes.RMI_PORT)
        entity.setAttribute(Attributes.JMX_CONTEXT)
    }
    
    @AfterMethod
    public void tearDown() {
        if (jmxService != null) jmxService.shutdown();
    }

    @Test
    public void jmxValueProviderReturnsMBeanAttribute() {
        // Create a JMX service and configure an MBean with an attribute
        GeneralisedDynamicMBean mbean = jmxService.registerMBean('Catalina:type=GlobalRequestProcessor,name=http-8080', errorCount: 42)

        // Create an entity and configure it with the above JMX service

        // Create a JMX adapter, and register a sensor for the JMX attribute
        OldJmxSensorAdapter jmxAdapter = new OldJmxSensorAdapter(entity)
        jmxAdapter.connect()
        ValueProvider valueProvider = jmxAdapter.newAttributeProvider("Catalina:type=GlobalRequestProcessor,name=http-*", "errorCount")

        // Starts with value defined when registering...
        assertEquals 42, valueProvider.compute()

        // Change the value and check it updates
        mbean.updateAttributeValue('errorCount', 64)
        assertEquals 64, valueProvider.compute()
    }

    @Test
    public void tabularDataProviderReturnsMap() {
        // Create the CompositeType and TabularData
        CompositeType compositeType = new CompositeType(
                "typeName",
                "description",
                ["pid", "state", "started"] as String[],    // item names
                ["pid", "state", "started"] as String[],    // item descriptions, can't be null or empty string
                [SimpleType.INTEGER, SimpleType.STRING, SimpleType.BOOLEAN] as OpenType<?>[]
        )
        TabularType tt = new TabularType(
                "typeName",
                "description",
                compositeType,
                ["pid"] as String[]
        )
        TabularDataSupport tds = new TabularDataSupport(tt)
        tds.put(new CompositeDataSupport(
                compositeType,
                ["started", "pid", "state"] as String[],
                [true, 1234, "on"] as Object[]
        ))

        // Create MBean
        GeneralisedDynamicMBean mbean = jmxService.registerMBean(data: tds,
                'Catalina:type=GlobalRequestProcessor,name=tables')

        // Create a JMX adapter, and register a sensor for the JMX attribute
        OldJmxSensorAdapter jmxAdapter = new OldJmxSensorAdapter(entity)
        jmxAdapter.connect()
        ValueProvider valueProvider = jmxAdapter.newTabularDataProvider(
                "Catalina:type=GlobalRequestProcessor,name=tables",
                "data")

        Map<String, Object> map = valueProvider.compute()
        assertEquals 3, map.size()
        assertEquals 1234, map.get("pid")
        assertEquals "on", map.get("state")
        assertTrue map.get("started")
    }

    @Test
    public void jmxOperationInvokesMethod() {
        String objectName = 'JmxEffectorAdapterTest:type=Generic'
        final AtomicInteger invocationCount = new AtomicInteger()
        GeneralisedDynamicMBean mbean = jmxService.registerMBean([:], ["myop":{invocationCount.incrementAndGet()}], objectName)

        // Create a JMX adapter
        OldJmxSensorAdapter jmxAdapter = new OldJmxSensorAdapter(entity)
        jmxAdapter.connect()
        
        // Invoke the operation
        jmxAdapter.operation(objectName, "myop")

        assertEquals invocationCount.get(), 1
    }

    @Test
    public void jmxNotificationReceived() {
        // Setup
        String objectName = 'JmxEffectorAdapterTest:type=Notifier'
        String one = 'notification.one', two = 'notification.two'
        StandardEmitterMBean mbean = jmxService.registerMBean([ one, two ], objectName)
        int sequence = 0

        // Create a JMX adapter
        OldJmxSensorAdapter adapter = new OldJmxSensorAdapter(entity)
        adapter.connect()
        
        // Create a listener
        Listener listener = new Listener(one);
        
        // Add notification listener 
        adapter.addNotification(objectName, listener)

        Notification n = new Notification(one, mbean, sequence++, "test");
        mbean.sendNotification(n);
        
        // Check 
        assertTrue listener.waitForNotification()
    }
    
    @Test
    public void jmxOperationInvokesMethodWithArgsAndReturnsValue() {
        String objectName = 'JmxEffectorAdapterTest:type=Generic'
        MBeanParameterInfo paramInfo = new MBeanParameterInfo('param1', String.class.getName(), 'my param1')
        MBeanParameterInfo[] paramInfos = [ paramInfo ].toArray(new MBeanParameterInfo[0])
        MBeanOperationInfo opInfo = new MBeanOperationInfo('myop', "my descr", paramInfos, String.class.getName(), MBeanOperationInfo.ACTION)

	    // This is awful...
        GeneralisedDynamicMBean mbean = jmxService.registerMBean([:], [ (opInfo):{ Object[] args -> args[0]+'suffix' } ], objectName)

        // Create a JMX adapter
        OldJmxSensorAdapter jmxAdapter = new OldJmxSensorAdapter(entity)
        jmxAdapter.connect()
        
        // Invoke the operation
        String result = jmxAdapter.operation(objectName, 'myop', 'abc')

        assertEquals result, 'abc'+'suffix'
    }

    // TODO Test needs fixed/updated and cleaned up, or deleted
    @Test(enabled = false)
    public void testJmxSensorTool() {
        String urlS = "service:jmx:rmi:///jndi/rmi://localhost:10100/jmxrmi";
        JMXServiceURL url = new JMXServiceURL(urlS);
        JMXConnector jmxc = JMXConnectorFactory.connect(url, null);
        MBeanServerConnection mbsc = jmxc.getMBeanServerConnection();
        
        String[] domains = mbsc.getDomains();
        Arrays.sort(domains);
        for (String domain : domains) {
            log.debug "domain {}", domain
        }
    
        log.debug "default domain {}", mbsc.defaultDomain
        log.debug "mbean count {}", mbsc.mBeanCount

        Set names = new TreeSet(mbsc.queryNames(null, null));
        for (ObjectName name : names) {
            log.debug "object name {}", name
        }
        
        OldJmxSensorAdapter adapter = new OldJmxSensorAdapter(urlS)
        adapter.connect()
        
        def result = adapter.getAttribute "Catalina:type=GlobalRequestProcessor,name=http-bio-*", "requestCount"
        // TODO add assertions

        adapter.disconnect()
    }
}

/** Listener class for JMX {@link Notification} testing. */
public class Listener implements NotificationListener {
    private String waiting;
    private CountDownLatch latch = new CountDownLatch(1);
            
    public Listener(String waiting) {
        this.waiting = waiting
    }
    
    public void handleNotification(Notification notification, Object handback) {
        if (waiting == notification.type) {
            latch.countDown()
        }
    }
            
    public boolean waitForNotification() {
        latch.await(1, TimeUnit.SECONDS)
    }
}
