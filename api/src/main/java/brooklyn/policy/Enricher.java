package brooklyn.policy;

import java.util.Map;

import brooklyn.config.ConfigKey;
import brooklyn.entity.rebind.RebindSupport;
import brooklyn.entity.rebind.Rebindable;
import brooklyn.entity.trait.Configurable;
import brooklyn.mementos.EnricherMemento;

import com.google.common.annotations.Beta;

/**
 * Publishes metrics for an entity, e.g. aggregating information from other sensors/entities.
 *
 * Has some similarities to {@link Policy}. However, enrichers specifically do not invoke
 * effectors and should only function to publish new metrics.
 */
public interface Enricher extends EntityAdjunct, Rebindable, Configurable {
    /**
     * A unique id for this enricher.
     */
    @Override
    String getId();

    /**
     * Get the name assigned to this enricher.
     */
    @Override
    String getName();

    /**
     * Information about the type of this entity; analogous to Java's object.getClass.
     */
    @Beta
    EnricherType getEnricherType();

    <T> T getConfig(ConfigKey<T> key);

    <T> T setConfig(ConfigKey<T> key, T val);

    Map<ConfigKey<?>, Object> getAllConfig();

    @Override
    RebindSupport<EnricherMemento> getRebindSupport();

}
