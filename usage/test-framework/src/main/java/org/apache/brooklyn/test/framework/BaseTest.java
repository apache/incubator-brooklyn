package org.apache.brooklyn.test.framework;

import com.google.common.collect.Maps;
import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.entity.trait.Startable;
import org.apache.brooklyn.util.time.Duration;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * A base interface for all tests.
 */
public interface BaseTest extends Entity, Startable {

    /**
     * The target entity to test (optional, use either this or targetId).
     */
    ConfigKey<Entity> TARGET_ENTITY = ConfigKeys.newConfigKey(Entity.class, "target", "Entity under test");

    /**
     * Id of the target entity to test (optional, use either this or target).
     */
    ConfigKey<String> TARGET_ID = ConfigKeys.newStringConfigKey("targetId", "Id of the entity under test");

    /**
     * The assertions to be made
     */
    ConfigKey<Map> ASSERTIONS = ConfigKeys.newConfigKey(Map.class, "assert", "Assertions to be evaluated", Maps.newHashMap());

    /**
     * THe duration to wait
     */
    ConfigKey<Duration> TIMEOUT = ConfigKeys.newConfigKey(Duration.class, "timeout", "Time to wait on result", new Duration(1L, TimeUnit.SECONDS));

    /**
     * Get the target of the test.
     *
     * @return The target.
     * @throws IllegalArgumentException if the target cannot be found.
     */
    Entity resolveTarget();
}
