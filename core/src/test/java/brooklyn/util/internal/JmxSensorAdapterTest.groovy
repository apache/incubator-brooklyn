package brooklyn.util.internal

import brooklyn.entity.basic.AbstractEntity
import brooklyn.entity.basic.AttributeDictionary
import brooklyn.event.adapter.JmxSensorAdapter
import brooklyn.event.basic.BasicAttributeSensor
import brooklyn.test.GeneralisedDynamicMBean
import brooklyn.test.JmxService
import javax.management.MBeanServerConnection
import javax.management.ObjectName
import javax.management.remote.JMXConnector
import javax.management.remote.JMXConnectorFactory
import javax.management.remote.JMXServiceURL
import org.junit.Ignore
import org.junit.Test
import static org.junit.Assert.*

class JmxSensorAdapterTest {

    @Test
    public void addASensorAndCheckItDetectsValuesOfAJmxAttribute() {
        // Create a JMX service and configure an MBean with an attribute
        JmxService jmxService = new JmxService()
        GeneralisedDynamicMBean mbean = jmxService.registerMBean('Catalina:type=GlobalRequestProcessor,name=http-8080', errorCount: 42)

        // Create an entity and configure it with the above JMX service
        AbstractEntity entity = new AbstractEntity(){}
        entity.updateAttribute(AttributeDictionary.JMX_HOST, jmxService.jmxHost)
        entity.updateAttribute(AttributeDictionary.JMX_PORT, jmxService.jmxPort)

        // Create a JMX adapter, and register a sensor for the JMX attribute
        JmxSensorAdapter jmxAdapter = new JmxSensorAdapter(entity, 5)
        BasicAttributeSensor<Integer> ERROR_COUNT = [ Integer, "webapp.reqs.errors", "Request errors" ]
        jmxAdapter.addSensor(ERROR_COUNT, "Catalina:type=GlobalRequestProcessor,name=http-*", "errorCount")

        // Sleep to allow the periodic update to happen, and then query the sensor for the test message
        Thread.sleep 550
        assertEquals 42, entity.getAttribute(ERROR_COUNT)

        // Change the message and check it updates
        mbean.updateAttributeValue('errorCount', 64)
        Thread.sleep 550
        assertEquals 64, entity.getAttribute(ERROR_COUNT)
    }

    @Test
    @Ignore
    public void testJmxSensorTool() {
        String urlS = "service:jmx:rmi:///jndi/rmi://localhost:10100/jmxrmi";
        JMXServiceURL url = new JMXServiceURL(urlS);
        JMXConnector jmxc = JMXConnectorFactory.connect(url, null);
        MBeanServerConnection mbsc = jmxc.getMBeanServerConnection();
        
        println("\nDomains:");
        String[] domains = mbsc.getDomains();
        Arrays.sort(domains);
        for (String domain : domains) {
            println("\tDomain = " + domain);
        }
    
        println("\nMBeanServer default domain = " + mbsc.getDefaultDomain());

        println("\nMBean count = " + mbsc.getMBeanCount());
        println("\nQuery MBeanServer MBeans:");
        Set names =
            new TreeSet(mbsc.queryNames(null, null));
        for (ObjectName name : names) {
            println("\tObjectName = " + name);
        }
        
        JmxSensorAdapter adapter = new JmxSensorAdapter(urlS)
        adapter.connect()
        
        def r1 = adapter.getAttributes "Catalina:type=GlobalRequestProcessor,name=\"http-bio-8080\""
        println r1

        def rN = adapter.getChildrenAttributesWithTotal "Catalina:type=GlobalRequestProcessor,name=\"*\""
        println rN

        adapter.disconnect()
        
//      ObjectName mxbeanName = new ObjectName("Catalina:type=GlobalRequestProcessor,name=\"*\"");
////        ObjectName mxbeanName = new ObjectName("Catalina:type=GlobalRequestProcessor,name=\"http-bio-8080\"");
//      Set<ObjectInstance> matchingBeans = mbsc.queryMBeans mxbeanName, null
//      println "\nfound "+matchingBeans.size()+" GlobalRequestProcessors"
//      Expando r = []
//      r.totals = [:]
//      matchingBeans.each {
//          ObjectInstance bean = it
//          println "bean $it";
//          if (!r.children) r.children=new Expando()
//          def c = r.children[it.toString()] = [:]
//          MBeanInfo info = mbsc.getMBeanInfo(it.getObjectName())
//          c.attributes = [:]
//          info.getAttributes().each {
//              println "  attr $it"
//              c.attributes[it.getName()] = null
//          }
//          AttributeList attrs = mbsc.getAttributes it.getObjectName(), c.attributes.keySet() as String[]
//          attrs.asList().each {
//              println "  attr value "+it.getName()+" = "+it.getValue()+"  ("+it.getValue().getClass()+")"
//              c.attributes[it.getName()] = it.getValue();
//              if (it.getValue() in Number)
//                  r.totals[it.getName()] = (r.totals[it.getName()]?:0) + it.getValue()
//          }
//          info.getNotifications().each { println "  notf $it" }
//          info.getOperations().each { println "  oper $it" }
//      }
//      println r
        
    }
}
