package brooklyn.location.cloud;

import java.util.List;

import brooklyn.location.Location;

import com.google.common.annotations.Beta;
import com.google.common.base.Predicate;

/**
 * For a location that has sub-zones within it (e.g. an AWS region has availability zones that can be
 * mapped as sub-locations), this extension interface allows those to be accessed and used.
 * <p>
 * Implementers are strongly encouraged to extend {@link AbstractAvailabilityZoneExtension}.
 * 
 * @since 0.6.0
 */
@Beta
public interface AvailabilityZoneExtension {

    List<Location> getAllSubLocations();

    List<Location> getSubLocations(int max);

    List<Location> getSubLocationsByName(Predicate<? super String> namePredicate, int max);

}
