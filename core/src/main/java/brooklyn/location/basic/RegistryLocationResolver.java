package brooklyn.location.basic;

import java.util.Map;

import brooklyn.location.Location;
import brooklyn.location.LocationResolver;

/**
 * Extension to LocationResolver which can take a registry.
 * 
 * @deprecated since 0.6; the LocationResolver always takes the LocationRegistry now
 */
@Deprecated
public interface RegistryLocationResolver extends LocationResolver {

    @Override
    @SuppressWarnings("rawtypes")
    Location newLocationFromString(Map locationFlags, String spec, brooklyn.location.LocationRegistry registry);

    @Override
    boolean accepts(String spec, brooklyn.location.LocationRegistry registry);

}
