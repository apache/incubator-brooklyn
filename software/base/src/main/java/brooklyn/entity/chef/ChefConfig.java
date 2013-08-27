package brooklyn.entity.chef;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.event.basic.MapConfigKey;
import brooklyn.event.basic.SetConfigKey;

import com.google.common.annotations.Beta;

/** {@link ConfigKey}s used to configure the chef driver */
@Beta
public interface ChefConfig {

    public static final MapConfigKey<String> CHEF_COOKBOOKS = new MapConfigKey<String>(String.class, "brooklyn.chef.cookbooksUrls");
    public static final SetConfigKey<String> CHEF_RUN_LIST = new SetConfigKey<String>(String.class, "brooklyn.chef.runList");
    public static final MapConfigKey<Object> CHEF_LAUNCH_ATTRIBUTES = new MapConfigKey<Object>(Object.class, "brooklyn.chef.launch.attributes");
    
    public static final ConfigKey<Boolean> CHEF_RUN_CONVERGE_TWICE = ConfigKeys.newBooleanConfigKey("brooklyn.chef.converge.twice",
            "Whether to run converge commands twice if the first one fails; needed in some contexts, e.g. when switching between chef-server and chef-solo mode");

    public static enum ChefModes {
        /** Force use of Chef Solo */
        SOLO, 
        /** Force use of Knife; knife must be installed, and either 
         *  {@link ChefConfig#KNIFE_EXECUTABLE} and {@link ChefConfig#KNIFE_CONFIG_FILE} must be set 
         *  or knife on the path with valid global config set up */
        KNIFE,
        /** Tries {@link #KNIFE} if valid, else {@link #SOLO} */
        AUTODETECT
    };

    public static final ConfigKey<ChefModes> CHEF_MODE = ConfigKeys.newConfigKey(ChefModes.class, "brooklyn.chef.mode",
            "Whether Chef should run in solo mode, knife mode, or auto-detect", ChefModes.AUTODETECT);
    
    public static final ConfigKey<String> KNIFE_EXECUTABLE = ConfigKeys.newStringConfigKey("brooklyn.chef.knife.executableFile",
            "Knife command to run on the Brooklyn machine, including full path; defaults to scanning the path");
    public static final ConfigKey<String> KNIFE_CONFIG_FILE = ConfigKeys.newStringConfigKey("brooklyn.chef.knife.configFile",
            "Knife config file (typically knife.rb) to use, including full path; defaults to knife default/global config");
    
}
