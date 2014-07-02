package brooklyn.location.dynamic;

import com.google.common.annotations.Beta;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.location.Location;
import brooklyn.util.flags.SetFromFlag;

/**
 * A location that is created and owned by an entity at runtime.
 * <p>
 * The lifecycle of the location is managed by the owning entity.
 *
 * @param E the entity type
 * @param L the location type
 */
@Beta
public interface DynamicLocation<E extends Entity & LocationOwner<L, E>, L extends Location & DynamicLocation<E, L>> {

    @SetFromFlag("owner")
    ConfigKey<Entity> OWNER =
            ConfigKeys.newConfigKey(Entity.class, "owner", "The entity owning this location");

    @SetFromFlag("maxLocations")
    ConfigKey<Integer> MAX_SUB_LOCATIONS =
            ConfigKeys.newIntegerConfigKey("maxLocations", "The maximum number of sub-locations that can be created; 0 for unlimited", 0);

    E getOwner();

}
