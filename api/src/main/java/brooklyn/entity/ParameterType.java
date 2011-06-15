package brooklyn.entity;

import java.io.Serializable;

//Modeled on concepts in MBeanParameterInfo
public class ParameterType implements Serializable {
    private static final long serialVersionUID = -5521879180483663919L;
    
    private final String name;
    private final String type;
    private final String description;
    
    ParameterType(String name, String type, String description) {
        this.name = name;
        this.type = type;
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public String getDescription() {
        return description;
    }
}
