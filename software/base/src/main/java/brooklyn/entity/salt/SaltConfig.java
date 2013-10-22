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
            "salt.formulaUrls", "Map of Salt formulaURLs (normally GutHub repositories from the salt-formulas user)");

    @SetFromFlag("master")
    ConfigKey<SaltStackMaster> MASTER = ConfigKeys.newConfigKey(SaltStackMaster.class,
            "salt.master", "The Salt master server");

    AttributeSensor<String> MINION_ID = new BasicAttributeSensor<String>(String.class,
            "salt.minion.id", "The ID for a Salt minion");

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @SetFromFlag("minionIdFunction")
    ConfigKey<Function<Entity, String>> MINION_ID_FUNCTION = new BasicConfigKey(Function.class,
            "salt.minion.id.function", "Function to generate the ID of a Salt minion for an entity", Functions.toStringFunction());

    /**
     * The {@link SaltStackMaster salt-master} entity.
     */
    SaltStackMaster getMaster();

}
