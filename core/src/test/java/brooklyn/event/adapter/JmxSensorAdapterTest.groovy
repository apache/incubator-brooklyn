package brooklyn.event.adapter

import static org.testng.Assert.*

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

import javax.management.MBeanOperationInfo
import javax.management.MBeanParameterInfo
import javax.management.Notification
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

import brooklyn.entity.basic.AbstractApplication
import brooklyn.entity.basic.Attributes
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.test.GeneralisedDynamicMBean
import brooklyn.test.JmxService
import brooklyn.test.TestUtils
import brooklyn.test.entity.TestEntity
import brooklyn.test.location.MockLocation

/**
 * Test the operation of the {@link OldJmxSensorAdapter} class.
 * 
 * TODO clarify test purpose
 */
public class JmxSensorAdapterTest {
    private static final Logger log = LoggerFactory.getLogger(JmxSensorAdapterTest.class)

    private static final int TIMEOUT = 1000
    
    private JmxService jmxService
    private AbstractApplication app
    private TestEntity entity
    SensorRegistry registry
    private JmxSensorAdapter jmx
    
    private BasicAttributeSensor<Integer> intAttribute = [ Integer, "brooklyn.test.intAttribute", "Brooklyn testing int attribute" ]
    private BasicAttributeSensor<String> stringAttribute = [ String, "brooklyn.test.intAttribute", "Brooklyn testing int attribute" ]
    private BasicAttributeSensor<Map> mapAttribute = [ Map, "brooklyn.test.mapAttribute", "Brooklyn testing map attribute" ]
    private String objectName = 'Brooklyn:type=MyTestMBean,name=myname'
    private String attributeName = 'myattrib'
    private String opName = 'myop'
    
    @BeforeMethod
    public void setUp() {
        jmxService = new JmxService()
        
        // Create an entity and configure it with the above JMX service
        app = new AbstractApplication() {}
        entity = new TestEntity(owner:app)
        entity.setAttribute(Attributes.HOSTNAME, jmxService.jmxHost)
        entity.setAttribute(Attributes.JMX_PORT, jmxService.jmxPort)
        entity.setAttribute(Attributes.RMI_PORT)
        entity.setAttribute(Attributes.JMX_CONTEXT)
        app.start([new MockLocation()])
        
        registry = new SensorRegistry(entity);
        jmx = registry.register(new JmxSensorAdapter(period: 50*TimeUnit.MILLISECONDS));
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() {
        if (jmxService != null) jmxService.shutdown();
    }

    @Test
    public void jmxAttributePollerReturnsMBeanAttribute() {
        GeneralisedDynamicMBean mbean = jmxService.registerMBean(objectName, (attributeName): 42)

        jmx.objectName(objectName).with {
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
    public void jmxObjectCheckExistsEventuallyThrowsIfNotFound() {
        registry.activateAdapters()
        
        jmx.objectName('Brooklyn:type=DoesNotExist,name=doesNotExist').with {
            checkExistsEventually(1*TimeUnit.MILLISECONDS)
        }
    }

    @Test
    public void jmxObjectCheckExistsEventuallyReturnsIfFoundImmediately() {
        GeneralisedDynamicMBean mbean = jmxService.registerMBean(objectName)
        registry.activateAdapters()
        
        jmx.objectName(objectName).with {
            checkExistsEventually(1*TimeUnit.MILLISECONDS)
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

        jmx.objectName(objectName).with {
            attribute(attributeName).subscribe(mapAttribute, JmxPostProcessors.tabularDataToMap())
        }
        registry.activateAdapters()
        
        // Starts with value defined when registering...
        TestUtils.executeUntilSucceeds {
            Map<String, Object> map = entity.getAttribute(mapAttribute)
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
        
        jmx.objectName(objectName).with {
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
        
        jmx.objectName(objectName).with {
            operation(opName, "myprefix").poll(stringAttribute)
        }
        registry.activateAdapters()
        
        TestUtils.executeUntilSucceeds {
            assertEquals entity.getAttribute(stringAttribute), "myprefix"+"suffix"
        }
    }

    @Test
    public void jmxNotificationSubscriptionForSensor() {
        // Setup
        String one = 'notification.one', two = 'notification.two'
        StandardEmitterMBean mbean = jmxService.registerMBean([ one, two ], objectName)
        int sequence = 0

        jmx.objectName(objectName).with {
            notification(one).subscribe(intAttribute)
        }
        registry.activateAdapters()
        
        Notification notif = new Notification(one, mbean, sequence++)
        notif.setUserData(123)
        mbean.sendNotification(notif);
        
        TestUtils.executeUntilSucceeds(timeout:TIMEOUT) {
            assertEquals entity.getAttribute(intAttribute), 123
        }
    }
}
