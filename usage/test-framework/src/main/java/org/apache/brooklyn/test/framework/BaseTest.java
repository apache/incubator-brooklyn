package org.apache.brooklyn.test.framework;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.entity.trait.Startable;
import org.apache.brooklyn.util.core.flags.SetFromFlag;

/**
 *  A base interface for all tests.
 */
public interface BaseTest extends Entity, Startable {

    /**
     * The target entity to test (optional, use either this or targetId).
     */
    @SetFromFlag(nullable = false)
    ConfigKey<Entity> TARGET_ENTITY = ConfigKeys.newConfigKey(Entity.class, "target", "Entity under test");

    /**
     * Id of the target entity to test (optional, use either this or target).
     */
    @SetFromFlag(nullable = false)
    ConfigKey<String> TARGET_ID = ConfigKeys.newStringConfigKey("targetId", "Id of the entity under test");

    /**
     * Get the target of the test.
     *
     * @return The target.
     *
     * @throws IllegalArgumentException if the target cannot be found.
     */
    Entity resolveTarget();
}
