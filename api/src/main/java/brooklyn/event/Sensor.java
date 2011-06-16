package brooklyn.event;

import java.io.Serializable;

public class Sensor<T> implements Serializable {
    private static final long serialVersionUID = -3762018534086101323L;
    
    private String description;
    private String name;
    private String type;
    
    public Sensor() { /* for gson */ }

    public Sensor(String name, Class<T> type) {
        this("", name, type);
    }
 
    public Sensor(String description, String name, Class<T> type) {
        this.description = description;
        this.name = name;
        this.type = type.getName();
    }
    
    public String getDescription() { return description; }
    public String getName() { return name; }
    public String getType() { return type; }
}
