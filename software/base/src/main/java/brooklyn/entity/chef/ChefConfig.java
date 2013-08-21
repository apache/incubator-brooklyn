package brooklyn.entity.chef;

import com.google.common.annotations.Beta;

import brooklyn.config.ConfigKey;
import brooklyn.event.basic.MapConfigKey;
import brooklyn.event.basic.SetConfigKey;

/** {@link ConfigKey}s used to configure the chef driver */
@Beta
public interface ChefConfig {

    public static final MapConfigKey<String> CHEF_COOKBOOKS = new MapConfigKey<String>(String.class, "brooklyn.chef.cookbooksUrls");
    public static final SetConfigKey<String> CHEF_RUN_LIST = new SetConfigKey<String>(String.class, "brooklyn.chef.task.driver.runList");
    public static final MapConfigKey<Object> CHEF_LAUNCH_ATTRIBUTES = new MapConfigKey<Object>(Object.class, "brooklyn.chef.task.driver.launchAttributes");

}
