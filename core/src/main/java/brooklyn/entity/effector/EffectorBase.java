package brooklyn.entity.effector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Effector;
import brooklyn.entity.ParameterType;
import brooklyn.entity.effector.EffectorTasks.EffectorTaskFactory;

import com.google.common.base.Joiner;

/** concrete implementation of Effector interface, 
 * but not (at this level of the hirarchy) defining an implementation 
 * (see {@link EffectorTaskFactory} and {@link EffectorWithBody}) */
public class EffectorBase<T> implements Effector<T> {

    @SuppressWarnings("unused")
    private static final Logger log = LoggerFactory.getLogger(EffectorBase.class);
    
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

}
