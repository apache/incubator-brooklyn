package brooklyn.entity.basic;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import brooklyn.entity.Effector;
import brooklyn.entity.Entity;
import brooklyn.entity.ParameterType;
import brooklyn.management.CanAddTask;
import brooklyn.management.Task;
import brooklyn.management.internal.EffectorUtils;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.task.DynamicSequentialTask;
import brooklyn.util.task.DynamicTasks;

import com.google.common.base.Preconditions;

public class Effectors {
    
    public interface EffectorTaskFactory<T> {
        public abstract Task<T> newTask(Entity entity, Effector<T> effector, ConfigBag parameters);
    }

    public static class EffectorBodyTaskFactory<T> implements EffectorTaskFactory<T> {
        private final EffectorBody<T> effectorBody;
        public EffectorBodyTaskFactory(EffectorBody<T> effectorBody) {
            this.effectorBody = effectorBody;
        }
        
        @SuppressWarnings("unchecked")
        public Task<T> newTask(Entity entity, brooklyn.entity.Effector<T> effector, final ConfigBag parameters) {
            @SuppressWarnings("rawtypes")
            final DynamicSequentialTask[] dst = new DynamicSequentialTask[1];

            dst[0] = new DynamicSequentialTask<T>(
                    getFlagsForTaskInvocationAt(entity, effector), 
                    new Callable<T>() {
                        @Override
                        public T call() throws Exception {
                            try {
                                DynamicTasks.setTaskAdditionContext(new CanAddTask() {
                                    @Override
                                    public void addTask(Task<?> t) {
                                        dst[0].addTask(t);
                                    }
                                });
                                return effectorBody.main(parameters);
                            } finally {
                                DynamicTasks.removeTaskAdditionContext();
                            }
                        }
                    });
            return (DynamicSequentialTask<T>)dst[0];
        }
        
        /** subclasses may override to add additional flags, but they should include the flags returned here 
         * unless there is very good reason not to; default impl returns a MutableMap */
        protected Map<Object,Object> getFlagsForTaskInvocationAt(Entity entity, Effector<?> effector) {
            return EffectorUtils.getTaskFlagsForEffectorInvocation(entity, effector);
        }
    }
    
    public static class EffectorTaskBuilder<T> {
        private Class<T> returnType;
        private String effectorName;
        private String description;
        private List<ParameterType<?>> parameters = null;
        private EffectorTaskFactory<T> impl;
        
        private EffectorTaskBuilder(Class<T> returnType, String effectorName) {
            this.returnType = returnType;
            this.effectorName = effectorName;
        }
        public EffectorTaskBuilder<T> description(String description) {
            this.description = description;
            return this;                
        }
        public EffectorTaskBuilder<T> parameter(Class<?> paramType, String paramName) {
            return parameter(paramType, paramName, null, null);
        }
        public EffectorTaskBuilder<T> parameter(Class<?> paramType, String paramName, String paramDescription) {
            return parameter(paramType, paramName, paramDescription, null);                
        }
        public <V> EffectorTaskBuilder<T> parameter(Class<V> paramType, String paramName, String paramDescription, V defaultValue) {
            if (parameters==null) 
                parameters = new ArrayList<ParameterType<?>>();
            parameters.add(new BasicParameterType<V>(paramName, paramType, paramDescription, defaultValue));
            return this;                
        }
        public EffectorTaskBuilder<T> impl(EffectorTaskFactory<T> taskFactory) {
            this.impl = taskFactory;
            return this;
        }
        public EffectorTaskBuilder<T> impl(EffectorBody<T> effectorBody) {
            this.impl = new EffectorBodyTaskFactory<T>(effectorBody);
            return this;
        }
        public Effector<T> build() {
             Preconditions.checkNotNull(impl, "Cannot create task {} with no impl", effectorName);
             return new EffectorAndBody<T>(effectorName, returnType, parameters, description, impl);
        }
    }

    public static <T> EffectorTaskBuilder<T> effector(Class<T> returnType, String effectorName) {
        return new EffectorTaskBuilder<T>(returnType, effectorName);
    }

    /** returns an unsubmitted task which invokes the given effector */
    public static <T> Task<T> invocation(Entity entity, Effector<T> eff, @SuppressWarnings("rawtypes") Map parameters) {
        if (eff instanceof EffectorWithBody) {
            return ((EffectorWithBody<T>)eff).getBody().newTask(entity, eff, ConfigBag.newInstance().putAll(parameters));
        }
        
        // TODO in future we may wish to support looking up the implementation on the entity, 
        // cf sensors, and/or on the static/effectors registered, cf config default value
        throw new UnsupportedOperationException("No implementation registered for effector "+eff+" on "+entity);
    }    

}
