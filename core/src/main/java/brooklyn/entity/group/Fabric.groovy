package brooklyn.entity.group

import java.util.Map

import brooklyn.entity.Entity
import brooklyn.entity.Group;
import brooklyn.entity.trait.Resizable;
import brooklyn.entity.trait.Startable
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.location.Location
import brooklyn.util.internal.EntityStartUtils

/**
 * Intended to represent a {@link Tier} of entities over multiple locations.
 */
public interface Fabric extends Tier {
}
