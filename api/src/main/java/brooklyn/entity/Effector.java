package brooklyn.entity;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

//Modeled on concepts in MBeanOperationInfo
public class Effector implements Serializable {
    private static final long serialVersionUID = 1832435915652457843L;
    
    private final String name;
    private final String returnType;
    private final List<ParameterType> parameters;
    private final String description;

    Effector(Method m) {
        name = m.getName();
        returnType = m.getReturnType().getName();
        parameters = new ArrayList<ParameterType>();
        for (Class<?> t : m.getParameterTypes()) {
            parameters.add(new ParameterType("", t.getName(), ""));
        }
        description = "";
    }
    
    Effector(String name, String returnType, List<ParameterType> parameters, String description) {
        this.name = name;
        this.returnType = returnType;
        this.parameters = parameters;
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public String getReturnType() {
        return returnType;
    }

    public List<ParameterType> getParameters() {
        return parameters;
    }
    
    public String getDescription() {
        return description;
    }
}
