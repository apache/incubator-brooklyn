package brooklyn.event;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class Sensor<T> {
    private final Map<String, Object> properties = new HashMap<String, Object>();
    private final String name;
    private final Class<T> type;
    
    public String getName() { return name; }
    public Class<T> getType() { return type; }
    public Map<String, Object> getProperties() { return properties; }
    
    public Sensor(String name, Class<T> type) {
        this(name, type, Collections.<String, Object>emptyMap());
    }
 
    public Sensor(String name, Class<T> type, Map<String, Object> properties) {
        this.name = name;
        this.type = type;
        this.properties.putAll(properties);
    }
}