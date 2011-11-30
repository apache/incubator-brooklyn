package brooklyn.event.adapter

import static org.testng.Assert.*

import javax.management.openmbean.CompositeData
import javax.management.openmbean.CompositeDataSupport
import javax.management.openmbean.CompositeType
import javax.management.openmbean.OpenType
import javax.management.openmbean.SimpleType
import javax.management.openmbean.TabularDataSupport
import javax.management.openmbean.TabularType

import org.testng.annotations.Test

class JmxPostProcessorsTest {

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

    @Test
    public void testCompositeDataToMap() {
        Map expected = [mybool:true, myint:1234, mystring:"on"]
        
        CompositeData data = new CompositeDataSupport(
                compositeType,
                expected.keySet() as String[],
                expected.values() as Object[])
        
        Map mapFromClosure = JmxPostProcessors.compositeDataToMap().call(data)
        Map mapFromDirectCall = JmxPostProcessors.compositeDataToMap(data)
        
        assertEquals(mapFromDirectCall, expected)
        assertEquals(mapFromClosure, expected)
    }
    
    @Test
    public void testTabularDataToMap() {
        Map expected = [mybool:true, myint:1234, mystring:"on"]
        
        TabularDataSupport tds = new TabularDataSupport(tt)
        tds.put(new CompositeDataSupport(
                compositeType,
                expected.keySet() as String[],
                expected.values() as Object[]))

        Map mapFromClosure = JmxPostProcessors.tabularDataToMap().call(tds)
        Map mapFromDirectCall = JmxPostProcessors.tabularDataToMap(tds)
        
        assertEquals(mapFromDirectCall, expected)
        assertEquals(mapFromClosure, expected)
    }
    
    @Test
    public void testTabularDataToMapOfMaps() {
        Map expected = [
                    ([1234]):[mybool:true, myint:1234, mystring:"on"],
                    ([5678]):[mybool:false, myint:5678, mystring:"off"]
                ]
        
        TabularDataSupport tds = new TabularDataSupport(tt)
        expected.values().each {
            tds.put(new CompositeDataSupport(
                    compositeType,
                    it.keySet() as String[],
                    it.values() as Object[]))
        }

        Map mapFromClosure = JmxPostProcessors.tabularDataToMapOfMaps().call(tds)
        Map mapFromDirectCall = JmxPostProcessors.tabularDataToMapOfMaps(tds)
        
        assertEquals(mapFromDirectCall, expected)
        assertEquals(mapFromClosure, expected)
    }
}
