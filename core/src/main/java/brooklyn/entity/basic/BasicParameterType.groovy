package brooklyn.entity.basic;

import brooklyn.entity.ParameterType

/**
 * TODO javadoc
 */
public class BasicParameterType<T> implements ParameterType<T> {
    private static final long serialVersionUID = -5521879180483663919L
    
    private String name
    private Class<T> type
    private String description
    private T defaultValue = NONE

	public BasicParameterType(Map<?, ?> arguments = [:]) {
        arguments.each { key, value -> this."$key" = value }
    }

    public BasicParameterType(String name, Class<T> type, String description=null, T defaultValue = NONE) {
        this.name = name
        this.type = type
        this.description = description
        this.defaultValue = defaultValue
    }

	private static Object NONE = new Object()
	
    public String getName() { name }

    public Class<T> getParameterClass() { type }
    
    public String getParameterClassName() { type.canonicalName }

    public String getDescription() { description }

	public T getDefaultValue() {
		hasDefaultValue() ? defaultValue : null;
	}

	public boolean hasDefaultValue() {
		defaultValue != NONE
	}
}
