package brooklyn.entity.trait;

import brooklyn.config.ConfigKey;

/**
 * Something that has mutable config, such as an entity or policy.
 * 
 * @author aled
 */
public interface Configurable {

    // FIXME Moved from core project to api project, as part of moving EntityLocal.

    public <T> T setConfig(ConfigKey<T> key, T val);

}
