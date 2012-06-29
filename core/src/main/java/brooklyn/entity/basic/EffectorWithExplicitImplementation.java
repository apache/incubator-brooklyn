package brooklyn.entity.basic;

import java.util.List;

import brooklyn.entity.ParameterType;

import com.google.common.collect.ImmutableList;

/**
 * @deprecated will be deleted in 0.5. Now called ExplicitEffector.
 */
@Deprecated
public abstract class EffectorWithExplicitImplementation<I,T> extends ExplicitEffector<I,T> {
    public EffectorWithExplicitImplementation(String name, Class<T> type, String description) {
        super(name, type, ImmutableList.<ParameterType<?>>of(), description);
    }
    
    public EffectorWithExplicitImplementation(String name, Class<T> type, List<ParameterType<?>> parameters, String description) {
        super(name, type, parameters, description);
    }
}
