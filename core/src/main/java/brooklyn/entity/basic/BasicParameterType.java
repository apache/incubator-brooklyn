package brooklyn.entity.basic;

import java.util.Collections;
import java.util.Map;

import brooklyn.entity.ParameterType;

/**
 * TODO javadoc
 */
public class BasicParameterType<T> implements ParameterType<T> {
    private static final long serialVersionUID = -5521879180483663919L;
    
    private String name;
    private Class<T> type;
    private String description;
    private T defaultValue = (T) NONE;

    public BasicParameterType() {
        this(Collections.emptyMap());
    }
    
    public BasicParameterType(Map<?, ?> arguments) {
        if (arguments.containsKey("name")) name = (String) arguments.get("name");
        if (arguments.containsKey("type")) type = (Class<T>) arguments.get("type");
        if (arguments.containsKey("description")) description = (String) arguments.get("description");
        if (arguments.containsKey("defaultValue")) defaultValue = (T) arguments.get("defaultValue");
    }

    public BasicParameterType(String name, Class<T> type) {
        this(name, type, null, (T) NONE);
    }
    
    public BasicParameterType(String name, Class<T> type, String description) {
        this(name, type, description, (T) NONE);
    }
    
    public BasicParameterType(String name, Class<T> type, String description, T defaultValue) {
        this.name = name;
        this.type = type;
        this.description = description;
        this.defaultValue = defaultValue;
    }

    private static Object NONE = new Object();
    
    public String getName() { return name; }

    public Class<T> getParameterClass() { return type; }
    
    public String getParameterClassName() { return type.getCanonicalName(); }

    public String getDescription() { return description; }

    public T getDefaultValue() {
        return hasDefaultValue() ? defaultValue : null;
    }

    public boolean hasDefaultValue() {
        return defaultValue != NONE;
    }
}
