package brooklyn.launcher.config;

import brooklyn.config.BrooklynServiceAttributes;
import brooklyn.config.ConfigKey;
import brooklyn.launcher.config.BrooklynDevelopmentModes.BrooklynDevelopmentMode;
import brooklyn.location.cloud.CloudLocationConfig;
import brooklyn.management.internal.BrooklynGarbageCollector;
import brooklyn.rest.BrooklynWebConfig;
import brooklyn.util.internal.BrooklynSystemProperties;
import brooklyn.util.internal.StringSystemProperty;

/**
 * Convenience collection of popular global configuration values.
 * (Also a handy way to recall where config keys are set.)
 * <p>
 * Also see:
 * <li> {@link BrooklynSystemProperties}
 * <li> {@link BrooklynServiceAttributes}
 * <li> {@link CloudLocationConfig} and classes in that hierarchy.
 */
public class BrooklynGlobalConfig {

    public static final ConfigKey<BrooklynDevelopmentMode> BROOKLYN_DEV_MODE = BrooklynDevelopmentModes.BROOKLYN_DEV_MODE;

    public static final ConfigKey<Boolean> REQUIRE_HTTPS = BrooklynWebConfig.HTTPS_REQUIRED;
    
    public static final ConfigKey<Long> GC_PERIOD = BrooklynGarbageCollector.GC_PERIOD;
    public static final ConfigKey<Boolean> DO_SYSTEM_GC = BrooklynGarbageCollector.DO_SYSTEM_GC;
    public static final ConfigKey<Integer> MAX_TASKS_PER_TAG = BrooklynGarbageCollector.MAX_TASKS_PER_TAG;
    public static final ConfigKey<Long> MAX_TASK_AGE = BrooklynGarbageCollector.MAX_TASK_AGE;

    public static final StringSystemProperty LOCALHOST_IP_ADDRESS = BrooklynServiceAttributes.LOCALHOST_IP_ADDRESS;
    
    // TODO other constants from elsewhere
}
