package brooklyn.entity.trait;

import brooklyn.entity.ConfigKey;
import brooklyn.entity.Effector;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.EffectorInferredFromAnnotatedMethod;
import brooklyn.event.Sensor;
import brooklyn.event.basic.BasicAttributeSensor;

/**
 * This interface describes an {@link Entity} that can be configured externally, usually a {@link Startable} service.
 * 
 * The {@link Effector} available is {@link #CONFIGURE}, and the {@link Sensor} is {@link #SERVICE_CONFIGURED}. An entity
 * should implement the {@link #configure()} method to inspect its {@link Sensor}s and {@link ConfigKey}s and then
 * perform any actions such as generating configuration files that are required before starting.
 * 
 * @deprecated externally-driven configuration should be done in entity-specific ways; a generic pattern is not well-enough understood
 * (and there was ambiguity in how it was being done)
 */
@Deprecated
public interface Configurable {
    Sensor<Boolean> SERVICE_CONFIGURED = new BasicAttributeSensor<Boolean>(Boolean.class, "service.isConfigured", "Service configured");

    Effector<Void> CONFIGURE = new EffectorInferredFromAnnotatedMethod<Void>(Configurable.class, "configure", "Configure an entity");

    /**
     * Configure the entity before it starts.
     */
    public abstract void configure();
}
