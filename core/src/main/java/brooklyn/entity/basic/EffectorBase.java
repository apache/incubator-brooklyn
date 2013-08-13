package brooklyn.entity.basic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import brooklyn.entity.Effector;
import brooklyn.entity.ParameterType;
import brooklyn.entity.basic.EffectorTasks.EffectorTaskFactory;

import com.google.common.base.Joiner;
import com.google.common.base.Objects;

/** concrete implementation of Effector interface, 
 * but not (at this level of the hirarchy) defining an implementation 
 * (see {@link EffectorTaskFactory} and {@link EffectorWithBody}) */
public class EffectorBase<T> implements Effector<T> {

    private static final long serialVersionUID = -4153962199078384835L;
    
    private final String name;
    private final Class<T> returnType;
    private final List<ParameterType<?>> parameters;
    private final String description;

    public EffectorBase(String name, Class<T> returnType, List<ParameterType<?>> parameters, String description) {
        this.name = name;
        this.returnType = returnType;
        this.parameters = Collections.unmodifiableList(parameters);
        this.description = description;
        
        //FIXME Is this needed? What does it do?
        //setMetaClass(DefaultGroovyMethods.getMetaClass(getClass()));
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Class<T> getReturnType() {
        return returnType;
    }

    @Override
    public String getReturnTypeName() {
        return returnType.getCanonicalName();
    }

    @Override
    public List<ParameterType<?>> getParameters() {
        return parameters;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        List<String> parameterNames = new ArrayList<String>(parameters.size());
        for (ParameterType<?> parameter: parameters) {
            String parameterName = (parameter.getName() != null) ? parameter.getName() : "<unknown>";
            parameterNames.add(parameterName);
        }
        return name+"["+Joiner.on(",").join(parameterNames)+"]";
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(name, description, parameters, returnType);
    }

    @Override
    public boolean equals(Object obj) {
        return (obj instanceof Effector) &&
                Objects.equal(name, ((Effector<?>)obj).getName()) &&
                Objects.equal(description, ((Effector<?>)obj).getDescription()) &&
                Objects.equal(parameters, ((Effector<?>)obj).getParameters()) &&
                Objects.equal(returnType, ((Effector<?>)obj).getReturnType());
    }
    
}
