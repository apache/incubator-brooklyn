package brooklyn.location.cloud;

import java.util.List;

import brooklyn.entity.group.DynamicCluster;
import brooklyn.location.Location;
import brooklyn.location.basic.MultiLocation;

import com.google.common.annotations.Beta;
import com.google.common.base.Predicate;

/**
 * For a location that has sub-zones within it (e.g. an AWS region has availability zones that can be
 * mapped as sub-locations), this extension interface allows those to be accessed and used.
 * For some well-known clouds, the availability zones are automatically set, although for others they may
 * have to be configured explicitly. The "multi:(locs,...)" location descriptor (cf {@link MultiLocation}) allows
 * this to be down at runtime.
 * <p>
 * Note that only entities which are explicitly aware of the {@link AvailabilityZoneExtension}
 * will use availability zone information. For example {@link DynamicCluster} 
 * <p>
 * Implementers are strongly encouraged to extend {@link AbstractAvailabilityZoneExtension}
 * which has useful behaviour, rather than attempt to implement this interface directly.
 * 
 * @since 0.6.0
 */
@Beta
public interface AvailabilityZoneExtension {

    List<Location> getAllSubLocations();

    List<Location> getSubLocations(int max);

    List<Location> getSubLocationsByName(Predicate<? super String> namePredicate, int max);

}
