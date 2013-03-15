package brooklyn.entity;

/** @deprecated since 0.4.0 use {@link brooklyn.config.ConfigKey} instead */
@Deprecated
public interface ConfigKey<T> extends brooklyn.config.ConfigKey<T> {
    /** @deprecated since 0.4.0 use {@link brooklyn.config.ConfigKey.HasConfigKey} instead */
    public interface HasConfigKey<T> extends brooklyn.config.ConfigKey.HasConfigKey<T> {}
}
