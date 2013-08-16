package brooklyn.entity.basic;

import java.util.Map;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.Beta;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Effector;
import brooklyn.entity.Entity;
import brooklyn.entity.ParameterType;
import brooklyn.management.Task;
import brooklyn.management.internal.EffectorUtils;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.task.DynamicSequentialTask;
import brooklyn.util.task.DynamicTasks;
import brooklyn.util.task.TaskBuilder;
import brooklyn.util.task.Tasks;

@Beta // added in 0.6.0
public class EffectorTasks {

    @SuppressWarnings("unused")
    private static final Logger log = LoggerFactory.getLogger(EffectorTasks.class);
    
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
                                DynamicTasks.setTaskQueueingContext(dst[0]);
                                return effectorBody.main(parameters);
                            } finally {
                                DynamicTasks.removeTaskQueueingContext();
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
    
    public static <T> ConfigKey<T> asConfigKey(ParameterType<T> t) {
        return ConfigKeys.newConfigKey(t.getParameterClass(), t.getName());
    }
    
    public static <T> ParameterTask<T> parameter(ParameterType<T> t) {
        return new ParameterTask<T>(asConfigKey(t)).
                name("parameter "+t);
    }
    public static <T> ParameterTask<T> parameter(Class<T> type, String name) {
        return new ParameterTask<T>(ConfigKeys.newConfigKey(type, name)).
                name("parameter "+name+" ("+type+")");
    }
    public static <T> ParameterTask<T> parameter(final ConfigKey<T> p) {
        return new ParameterTask<T>(p);
    }
    public static class ParameterTask<T> implements EffectorTaskFactory<T> {
        final ConfigKey<T> p;
        private TaskBuilder<T> builder;
        public ParameterTask(ConfigKey<T> p) {
            this.p = p;
            this.builder = Tasks.<T>builder().name("parameter "+p);
        }
        public ParameterTask<T> name(String taskName) {
            builder.name(taskName);
            return this;
        }
        @Override
        public Task<T> newTask(Entity entity, Effector<T> effector, final ConfigBag parameters) {
            return builder.body(new Callable<T>() {
                @Override
                public T call() throws Exception {
                    return parameters.get(p);
                }
                
            }).build();
        }
        
    }

    public static <T> EffectorTaskFactory<T> of(final Task<T> task) {
        return new EffectorTaskFactory<T>() {
            @Override
            public Task<T> newTask(Entity entity, Effector<T> effector, ConfigBag parameters) {
                return task;
            }
        };
    }

}
