package brooklyn.entity;

/** @deprecated since 0.4.0 use brooklyn.config.ConfigKey instead */
public interface ConfigKey<T> extends brooklyn.config.ConfigKey<T> {
    /** @deprecated since 0.4.0 use brooklyn.config.ConfigKey.HasConfigKey instead */
    public interface HasConfigKey<T> extends brooklyn.config.ConfigKey.HasConfigKey<T> {}
}
