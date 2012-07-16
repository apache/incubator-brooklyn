package brooklyn.entity.basic;

import brooklyn.entity.Entity;
import brooklyn.location.Location;

/**
 * dispatch interface to allow an EntityFactory to indicate it might be able to discover
 * other factories for specific locations (e.g. if the location implements a custom entity-aware interface)
 */
public interface EntityFactoryForLocation<T extends Entity> {
    ConfigurableEntityFactory<T> newFactoryForLocation(Location l);
}
