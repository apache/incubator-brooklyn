package brooklyn.entity.basic;

import brooklyn.entity.ParameterType;

public class BasicParameterType<T> implements ParameterType<T> {

    private static final long serialVersionUID = -5521879180483663919L;
    
    private String name;
    private Class<T> type;
    private String defaultValue;
    private String description;

	public BasicParameterType(Map m=[:]) { m.each { this."$it.key" = it.value } }
    public BasicParameterType(String name, Class<T> type, T defaultValue=null, String description=null) {
        this.name = name;
        this.type = type;
        this.defaultValue = defaultValue;
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

	public String getDefaultValue() {
		return defaultValue;
	}
}
