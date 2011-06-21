package brooklyn.entity.basic;

import brooklyn.entity.ParameterType;

public class BasicParameterType<T> implements ParameterType<T> {

    private static final long serialVersionUID = -5521879180483663919L;
    
    private String name;
    private Class<T> type;
    private String description;

    @SuppressWarnings("unused")
    private BasicParameterType() { /* for gson */ }

    public BasicParameterType(String name, Class<T> type, String description) {
        this.name = name;
        this.type = type;
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public Class<T> getParameterClass() {
        return type;
    }
    
    public String getParameterClassName() {
    	return type.getCanonicalName();
    }

    public String getDescription() {
        return description;
    }

}
