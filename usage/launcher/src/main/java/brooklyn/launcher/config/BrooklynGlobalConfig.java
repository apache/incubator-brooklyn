package brooklyn.launcher.config;

import brooklyn.config.ConfigKey;
import brooklyn.launcher.config.BrooklynDevelopmentModes.BrooklynDevelopmentMode;
import brooklyn.management.internal.BrooklynGarbageCollector;

/**
 * Convenience collection of global configuration values.
 */
public class BrooklynGlobalConfig {

    public static final ConfigKey<BrooklynDevelopmentMode> BROOKLYN_DEV_MODE = BrooklynDevelopmentModes.BROOKLYN_DEV_MODE;

    public static final ConfigKey<Long> GC_PERIOD = BrooklynGarbageCollector.GC_PERIOD;
    public static final ConfigKey<Boolean> DO_SYSTEM_GC = BrooklynGarbageCollector.DO_SYSTEM_GC;
    public static final ConfigKey<Integer> MAX_TASKS_PER_TAG = BrooklynGarbageCollector.MAX_TASKS_PER_TAG;
    public static final ConfigKey<Long> MAX_TASK_AGE = BrooklynGarbageCollector.MAX_TASK_AGE;

    // TODO other constants from elsewhere
    
}
