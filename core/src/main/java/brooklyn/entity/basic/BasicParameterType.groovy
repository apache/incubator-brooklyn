package brooklyn.entity.basic;

import brooklyn.entity.ParameterType;

public class BasicParameterType<T> implements ParameterType<T> {

    private static final long serialVersionUID = -5521879180483663919L;
    
    private String name;
    private Class<T> type;
    private String description;
    private T defaultValue = NONE;

	public BasicParameterType(Map m=[:]) { m.each { this."$it.key" = it.value } }
    public BasicParameterType(String name, Class<T> type, String description=null, T defaultValue=NONE) {
        this.name = name;
        this.type = type;
        this.description = description;
        this.defaultValue = defaultValue;
    }

	private static Object NONE = new Object()
	
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

	public T getDefaultValue() {
		return defaultValue!=NONE ? defaultValue : null;
	}
	public boolean hasDefaultValue() {
		return defaultValue!=NONE
	}
	
}
