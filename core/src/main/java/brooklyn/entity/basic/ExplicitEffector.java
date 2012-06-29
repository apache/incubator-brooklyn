package brooklyn.entity.basic;

import groovy.lang.Closure;

import java.util.List;
import java.util.Map;

import brooklyn.entity.Entity;
import brooklyn.entity.ParameterType;

import com.google.common.collect.ImmutableList;

public abstract class ExplicitEffector<I,T> extends AbstractEffector<T> {
    public ExplicitEffector(String name, Class<T> type, String description) {
        this(name, type, ImmutableList.<ParameterType<?>>of(), description);
    }
    public ExplicitEffector(String name, Class<T> type, List<ParameterType<?>> parameters, String description) {
        super(name, type, parameters, description);
    }

    public T call(Entity entity, Map parameters) {
        return invokeEffector((I) entity, parameters );
    }

    public abstract T invokeEffector(I trait, Map<String,?> parameters);
    
    /** convenience to create an effector supplying a closure; annotations are preferred,
     * and subclass here would be failback, but this is offered as 
     * workaround for bug GROOVY-5122, as discussed in test class CanSayHi 
     */
    public static <I,T> ExplicitEffector<I,T> create(String name, Class<T> type, List<ParameterType<?>> parameters, String description, Closure body) {
        return new ExplicitEffectorFromClosure(name, type, parameters, description, body);
    }
    
    private static class ExplicitEffectorFromClosure<I,T> extends ExplicitEffector<I,T> {
        final Closure<T> body;
        public ExplicitEffectorFromClosure(String name, Class<T> type, List<ParameterType<?>> parameters, String description, Closure<T> body) {
            super(name, type, parameters, description);
            this.body = body;
        }
        public T invokeEffector(I trait, Map<String,?> parameters) { return body.call(trait, parameters); }
    }
}
