package brooklyn.entity.basic;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Effector;
import brooklyn.entity.Entity;
import brooklyn.entity.EntityType;
import brooklyn.entity.ParameterType;

import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableMap;

/**
 * The abstract {@link Effector} implementation.
 * 
 * The concrete subclass (often anonymous) will supply the {@link #call(EntityType, Map)} implementation,
 * and the fields in the constructor.
 */
public abstract class AbstractEffector<T> implements Effector<T> {
    private static final long serialVersionUID = 1832435915652457843L;
    
    public static final Logger LOG = LoggerFactory.getLogger(AbstractEffector.class);

    private final String name;
    private final Class<T> returnType;
    private final List<ParameterType<?>> parameters;
    private final String description;

    public AbstractEffector(String name, Class<T> returnType, List<ParameterType<?>> parameters, String description) {
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

    public abstract T call(Entity entity, Map parameters);

    /** Convenience for named-parameter syntax (needs map in first argument) */
    public T call(Entity entity) { return call(ImmutableMap.of(), entity); }

    /** Convenience for named-parameter syntax (needs map in first argument) */
    public T call(Map parameters, Entity entity) { return call(entity, parameters); }

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
