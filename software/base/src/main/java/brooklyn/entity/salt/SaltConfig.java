package brooklyn.entity.salt;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.event.AttributeSensor;
import brooklyn.event.basic.BasicAttributeSensor;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.event.basic.MapConfigKey;
import brooklyn.util.flags.SetFromFlag;

import com.google.common.annotations.Beta;
import com.google.common.base.Function;
import com.google.common.base.Functions;

/** {@link ConfigKey}s used to configure the salt driver */
@Beta
public interface SaltConfig {

    MapConfigKey<String> SALT_FORMULAS = new MapConfigKey<String>(String.class,
            "salt.formulaUrls", "Map of Salt formulaURLs (normally GutHub repository archives from the salt-formulas user)");
    SetConfigKey<String> SALT_RUN_LIST = new SetConfigKey<String>(String.class, "salt.runList", "TODO");
    MapConfigKey<Object> SALT_LAUNCH_ATTRIBUTES = new MapConfigKey<Object>(Object.class, "salt.launch.attributes", "TODO");

    @SetFromFlag("master")
    ConfigKey<SaltStackMaster> MASTER = ConfigKeys.newConfigKey(SaltStackMaster.class,
            "salt.master", "The Salt master server");

    AttributeSensor<String> MINION_ID = new BasicAttributeSensor<String>(String.class,
            "salt.minion.id", "The ID for a Salt minion");

    @SetFromFlag("masterConfigUrl")
    ConfigKey<String> MASTER_CONFIGURATION_URL = ConfigKeys.newStringConfigKey(
            "salt.master.config.template.url", "The Salt master configuration file template URL",
            "classpath://brooklyn/entity/salt/master");

    @SetFromFlag("minionConfigUrl")
    ConfigKey<String> MINION_CONFIGURATION_URL = ConfigKeys.newStringConfigKey(
            "salt.minion.config.template.url", "The Salt minion configuration file template URL",
            "classpath://brooklyn/entity/salt/masterless");
    // TODO allow choice between this and "classpath://brooklyn/entity/salt/minion" template

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @SetFromFlag("minionIdFunction")
    ConfigKey<Function<Entity, String>> MINION_ID_FUNCTION = new BasicConfigKey(Function.class,
            "salt.minion.id.function", "Function to generate the ID of a Salt minion for an entity", Functions.toStringFunction());

    /**
     * The {@link SaltStackMaster salt-master} entity.
     */
    SaltStackMaster getMaster();

}
