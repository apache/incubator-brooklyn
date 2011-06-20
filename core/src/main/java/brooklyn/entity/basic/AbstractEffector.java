package brooklyn.entity.basic;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import brooklyn.entity.ParameterType;

public class AbstractEffector {
    private static final long serialVersionUID = 1832435915652457843L;
    
    private String name;
    private Class<?> returnType;
    private List<ParameterType<?>> parameters;
    private String description;

    @SuppressWarnings("unused")
    private AbstractEffector() { /* for gson */ }

    @SuppressWarnings({ "rawtypes", "unchecked" })
	public AbstractEffector(Method m) {
        name = m.getName();
        returnType = m.getReturnType();
        List p = new ArrayList<ParameterType<?>>();
        int i=0;
        for (Class<?> t : m.getParameterTypes()) {
            p.add(new BasicParameterType("param"+(++i), t, ""));
        }
        parameters = Collections.unmodifiableList(p);
        description = "";
    }
    
    public AbstractEffector(String name, Class<?> returnType, List<ParameterType<?>> parameters, String description) {
        this.name = name;
        this.returnType = returnType;
        this.parameters = Collections.unmodifiableList(parameters);
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public Class<?> getReturnType() {
        return returnType;
    }
    public String getReturnTypeName() {
        return returnType.getCanonicalName();
    }

    public List<ParameterType<?>> getParameters() {
        return parameters;
    }
    
    public String getDescription() {
        return description;
    }
}
