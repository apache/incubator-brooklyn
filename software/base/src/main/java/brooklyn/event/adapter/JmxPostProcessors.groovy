package brooklyn.event.adapter

import javax.management.openmbean.CompositeData
import javax.management.openmbean.TabularData

import org.slf4j.Logger
import org.slf4j.LoggerFactory

public class JmxPostProcessors {

    private static final Logger log = LoggerFactory.getLogger(JmxPostProcessors.class);
    
    /**
     * @return a closure that converts a TabularDataSupport to a map.
     */
    public static Closure tabularDataToMap() {
        return { return tabularDataToMap(it) }
    }

    public static Closure tabularDataToMapOfMaps() {
        return { return tabularDataToMapOfMaps(it) }
    }

    public static Closure compositeDataToMap() {
        return { return compositeDataToMap(it) }
    }
    
    public static Map tabularDataToMap(TabularData table) {
        HashMap<String, Object> out = []
        for (Object entry : table.values()) {
            CompositeData data = (CompositeData) entry //.getValue()
            data.getCompositeType().keySet().each { String key ->
                def old = out.put(key, data.get(key))
                if (old) {
                    log.warn "tablularDataToMap has overwritten key {}", key
                }
            }
        }
        return out
    }
    
    public static Map tabularDataToMapOfMaps(TabularData table) {
        HashMap<String, Object> out = []
        table.keySet().each { k ->
            final Object[] kValues = ((List<?>)k).toArray();
            CompositeData v = (CompositeData) table.get(kValues)
            out.put(k, compositeDataToMap(v))
        }
        return out
    }
    
    public static Map compositeDataToMap(CompositeData data) {
        HashMap<String, Object> out = []
        data.getCompositeType().keySet().each { String key ->
            def old = out.put(key, data.get(key))
            if (old) {
                log.warn "compositeDataToMap has overwritten key {}", key
            }
        }
        return out
    }
}
