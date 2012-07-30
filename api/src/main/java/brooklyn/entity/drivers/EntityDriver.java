package brooklyn.entity.drivers;

import brooklyn.location.Location;

/**
 * The EntityDriver provides an abstraction between the Entity and the environment (the {@link Location} it is running
 * in, so that an entity is not tightly coupled to a specific Location. E.g. you could have a TomcatEntity that uses
 * a TomcatDriver (an interface) and you could have different driver implementations like the
 * TomcatSshDriver/TomcatWindowsDriver and if in the future support for Puppet needs to be added, a TomcatPuppetDriver
 * could be added.
 *
 * @author Peter Veentjer.
 * @see DriverDependentEntity
 * @see EntityDriverFactory
 */
public interface EntityDriver {

    /**
     * The location the entity is running in.
     */
    Location getLocation();
}
