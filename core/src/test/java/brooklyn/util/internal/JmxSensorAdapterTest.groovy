package brooklyn.util.internal

import static org.testng.Assert.*

import javax.management.MBeanServerConnection
import javax.management.ObjectName
import javax.management.remote.JMXConnector
import javax.management.remote.JMXConnectorFactory
import javax.management.remote.JMXServiceURL

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testng.annotations.Test

import brooklyn.entity.LocallyManagedEntity
import brooklyn.entity.basic.AbstractEntity
import brooklyn.entity.basic.Attributes
import brooklyn.event.adapter.JmxSensorAdapter
import brooklyn.event.adapter.ValueProvider
import brooklyn.test.GeneralisedDynamicMBean
import brooklyn.test.JmxService
import javax.management.openmbean.CompositeData
import javax.management.openmbean.CompositeType
import javax.management.openmbean.TabularDataSupport
import javax.management.openmbean.TabularType
import javax.management.openmbean.SimpleType
import javax.management.openmbean.OpenType
import javax.management.openmbean.CompositeDataSupport

/**
 * Test the operation of the {@link JmxSensorAdapter} class.
 * 
 * TODO clarify test purpose
 */
public class JmxSensorAdapterTest {
    private static final Logger log = LoggerFactory.getLogger(JmxSensorAdapterTest.class)

    @Test
    public void jmxValueProviderReturnsMBeanAttribute() {
        // Create a JMX service and configure an MBean with an attribute
        JmxService jmxService = new JmxService()
        GeneralisedDynamicMBean mbean = jmxService.registerMBean('Catalina:type=GlobalRequestProcessor,name=http-8080', errorCount: 42)

        // Create an entity and configure it with the above JMX service
        AbstractEntity entity = new LocallyManagedEntity()
        entity.setAttribute(Attributes.HOSTNAME, jmxService.jmxHost)
        entity.setAttribute(Attributes.JMX_PORT, jmxService.jmxPort)
        entity.setAttribute(Attributes.JMX_CONTEXT)

        // Create a JMX adapter, and register a sensor for the JMX attribute
        JmxSensorAdapter jmxAdapter = new JmxSensorAdapter(entity)
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
        JmxService jmxService = new JmxService()
        GeneralisedDynamicMBean mbean = jmxService.registerMBean(data: tds,
                'Catalina:type=GlobalRequestProcessor,name=tables')

        // Create an entity and configure it with the above JMX service
        AbstractEntity entity = new LocallyManagedEntity()
        entity.setAttribute(Attributes.HOSTNAME, jmxService.jmxHost)
        entity.setAttribute(Attributes.JMX_PORT, jmxService.jmxPort)
        entity.setAttribute(Attributes.RMI_PORT)
        entity.setAttribute(Attributes.JMX_CONTEXT)

        // Create a JMX adapter, and register a sensor for the JMX attribute
        JmxSensorAdapter jmxAdapter = new JmxSensorAdapter(entity)
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
        
        JmxSensorAdapter adapter = new JmxSensorAdapter(urlS)
        adapter.connect()
        
        def r1 = adapter.getAttributes "Catalina:type=GlobalRequestProcessor,name=http-bio-*"
        def rN = adapter.getAttribute "Catalina:type=GlobalRequestProcessor,name=http-bio-*", "requestCount"
        // TODO add assertions

        adapter.disconnect()
    }
}
