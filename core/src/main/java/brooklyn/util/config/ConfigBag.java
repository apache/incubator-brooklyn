package brooklyn.util.config;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@SuppressWarnings({ "rawtypes", "unchecked" })
public class ConfigBag {

    private Map config = new LinkedHashMap();
    private Map unusedConfig = new LinkedHashMap();

    /** current values for all entries which have put into the map */
    public Map getAllConfig() {
        return Collections.unmodifiableMap(config);
    }

    /** current values for all entries which have not yet been used */
    public Map getUnusedConfig() {
        return Collections.unmodifiableMap(unusedConfig);
    }
    
    public ConfigBag putAll(Map flags) {
        for (Object eo: flags.entrySet()) {
            Map.Entry e = (Map.Entry)eo;
            boolean isNew = !config.containsKey(e.getKey());
            boolean isUsed = !isNew && !unusedConfig.containsKey(e.getKey());
            config.put(e.getKey(), e.getValue());
            if (!isUsed) 
                unusedConfig.put(e.getKey(), e.getValue());
            //if (!isNew && !isUsed) log.debug("updating config value which has already been used");
        }
        return this;
    }
    
    public void markUsed(Object key) {
        unusedConfig.remove(key);
    }
    
}
