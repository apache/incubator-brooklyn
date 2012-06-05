package brooklyn.entity.group

import java.util.Map

import brooklyn.entity.Entity
import brooklyn.entity.Group
import brooklyn.entity.basic.AbstractGroup
import brooklyn.entity.trait.Startable

/**
 * Intended to represent a "layer" of an application; this could be within a single location
 * or in multiple locations (see {@link Fabric}).
 */
public interface Tier extends Entity {
}
