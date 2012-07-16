package brooklyn.entity.basic;

import brooklyn.entity.Entity;

import java.util.Map;

/**
 * A Factory for creating entities.
 *
 * @param <T>
 */
public interface EntityFactory<T extends Entity> {
    T newEntity(Map flags, Entity owner);
}
