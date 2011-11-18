package brooklyn.event.adapter

import javax.management.openmbean.CompositeData
import javax.management.openmbean.TabularDataSupport

import org.slf4j.Logger
import org.slf4j.LoggerFactory

public class JmxPostProcessors {

    private static final Logger log = LoggerFactory.getLogger(JmxPostProcessors.class);
    
    /**
     * @return a closure that converts a TabularDataSupport to a map.
     */
    public static Closure tabularDataToMap() {
        return { TabularDataSupport table ->
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
    }
}
