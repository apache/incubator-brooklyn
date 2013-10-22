package brooklyn.entity.salt;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.event.basic.MapConfigKey;
import brooklyn.event.basic.SetConfigKey;
import brooklyn.management.TaskAdaptable;
import brooklyn.management.TaskFactory;

import com.google.common.annotations.Beta;
import com.google.common.reflect.TypeToken;

/** {@link ConfigKey}s used to configure the salt driver */
@Beta
public interface SaltConfig {

    public static final MapConfigKey<String> SALT_FORMULAS = new MapConfigKey<String>(String.class, "brooklyn.salt.formulaUrls");
    public static final SetConfigKey<String> SALT_RUN_LIST = new SetConfigKey<String>(String.class, "brooklyn.salt.runList");
    public static final MapConfigKey<Object> SALT_LAUNCH_ATTRIBUTES = new MapConfigKey<Object>(Object.class, "brooklyn.salt.launch.attributes");
    
    @SuppressWarnings("serial")
    public static final ConfigKey<TaskFactory<? extends TaskAdaptable<Boolean>>> IS_RUNNING_TASK = ConfigKeys.newConfigKey(
            new TypeToken<TaskFactory<? extends TaskAdaptable<Boolean>>>() {}, 
            "brooklyn.salt.task.driver.isRunningTask");
    
    @SuppressWarnings("serial")
    public static final ConfigKey<TaskFactory<?>> STOP_TASK = ConfigKeys.newConfigKey(
            new TypeToken<TaskFactory<?>>() {}, 
            "brooklyn.salt.task.driver.stopTask");
}
