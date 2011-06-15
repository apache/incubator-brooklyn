package brooklyn.entity;

import java.io.Serializable;

//Modeled on concepts in MBeanParameterInfo
public class ParameterType implements Serializable {
    private static final long serialVersionUID = -5521879180483663919L;
    
    private String name;
    private String type;
    private String description;

    @SuppressWarnings("unused")
    private ParameterType() { /* for gson */ }

    public ParameterType(String name, String type, String description) {
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
