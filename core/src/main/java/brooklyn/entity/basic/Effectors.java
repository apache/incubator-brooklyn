package brooklyn.entity.basic;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import brooklyn.entity.Effector;
import brooklyn.entity.Entity;
import brooklyn.entity.ParameterType;
import brooklyn.entity.basic.EffectorTasks.EffectorBodyTaskFactory;
import brooklyn.entity.basic.EffectorTasks.EffectorTaskFactory;
import brooklyn.management.Task;
import brooklyn.util.config.ConfigBag;

import com.google.common.base.Preconditions;

public class Effectors {
    
    public static class EffectorBuilder<T> {
        private Class<T> returnType;
        private String effectorName;
        private String description;
        private List<ParameterType<?>> parameters = new ArrayList<ParameterType<?>>();
        private EffectorTaskFactory<T> impl;
        
        private EffectorBuilder(Class<T> returnType, String effectorName) {
            this.returnType = returnType;
            this.effectorName = effectorName;
        }
        public EffectorBuilder<T> description(String description) {
            this.description = description;
            return this;                
        }
        public EffectorBuilder<T> parameter(Class<?> paramType, String paramName) {
            return parameter(paramType, paramName, null, null);
        }
        public EffectorBuilder<T> parameter(Class<?> paramType, String paramName, String paramDescription) {
            return parameter(paramType, paramName, paramDescription, null);                
        }
        public <V> EffectorBuilder<T> parameter(Class<V> paramType, String paramName, String paramDescription, V defaultValue) {
            return parameter(new BasicParameterType<V>(paramName, paramType, paramDescription, defaultValue));
        }
        public EffectorBuilder<T> parameter(ParameterType<?> p) {
            parameters.add(p);
            return this;
        }
        public EffectorBuilder<T> impl(EffectorTaskFactory<T> taskFactory) {
            this.impl = taskFactory;
            return this;
        }
        public EffectorBuilder<T> impl(EffectorBody<T> effectorBody) {
            this.impl = new EffectorBodyTaskFactory<T>(effectorBody);
            return this;
        }
        /** returns the effector, with an implementation (required); @see {@link #buildAbstract()} */
        public Effector<T> build() {
             Preconditions.checkNotNull(impl, "Cannot create effector {} with no impl (did you forget impl? or did you mean to buildAbstract?)", effectorName);
             return new EffectorAndBody<T>(effectorName, returnType, parameters, description, impl);
        }
        
        /** returns an abstract effector, where the body will be defined later/elsewhere 
         * (impl must not be set) */
        public Effector<T> buildAbstract() {
            Preconditions.checkArgument(impl==null, "Cannot create abstract effector {} as an impl is defined", effectorName);
            return new EffectorBase<T>(effectorName, returnType, parameters, description);
        }
    }

    /** creates a new effector builder with the given name and return type */
    public static <T> EffectorBuilder<T> effector(Class<T> returnType, String effectorName) {
        return new EffectorBuilder<T>(returnType, effectorName);
    }

    /** creates a new effector builder to _override_ the given effector */
    public static <T> EffectorBuilder<T> effector(Effector<T> base) {
        EffectorBuilder<T> builder = new EffectorBuilder<T>(base.getReturnType(), base.getName());
        for (ParameterType<?> p: base.getParameters())
            builder.parameter(p);
        builder.description(base.getDescription());
        if (builder instanceof EffectorWithBody)
            builder.impl(((EffectorWithBody<T>) base).getBody());
        return builder;
    }

    /** returns an unsubmitted task which invokes the given effector */
    public static <T> Task<T> invocation(Entity entity, Effector<T> eff, @SuppressWarnings("rawtypes") Map parameters) {
        if (eff instanceof EffectorWithBody) {
            return ((EffectorWithBody<T>)eff).getBody().newTask(entity, eff, ConfigBag.newInstance().putAll(parameters));
        }
        @SuppressWarnings("unchecked")
        Effector<T> eff2 = (Effector<T>) ((EntityInternal)entity).getEffector(eff.getName());
        if (eff2 instanceof EffectorWithBody) {
            return ((EffectorWithBody<T>)eff2).getBody().newTask(entity, eff2, ConfigBag.newInstance().putAll(parameters));
        }
        
        throw new UnsupportedOperationException("No implementation registered for effector "+eff+" on "+entity);
    }    

}
