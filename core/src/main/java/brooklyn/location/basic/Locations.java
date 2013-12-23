package brooklyn.location.basic;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

import brooklyn.entity.Entity;
import brooklyn.location.Location;
import brooklyn.location.MachineLocation;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

public class Locations {

    public static final LocationsFilter USE_FIRST_LOCATION = new LocationsFilter() {
        private static final long serialVersionUID = 3100091615409115890L;

        @Override
        public List<Location> filterForContext(List<Location> locations, Object context) {
            if (locations.size()<=1) return locations;
            return ImmutableList.of(locations.get(0));
        }
    };

    public interface LocationsFilter extends Serializable {
        public List<Location> filterForContext(List<Location> locations, Object context);
    }
    
    /** as {@link Machines#findUniqueMachineLocation(Iterable)} */
    public static Optional<MachineLocation> findUniqueMachineLocation(Iterable<? extends Location> locations) {
        return Machines.findUniqueMachineLocation(locations);
    }
    
    /** as {@link Machines#findUniqueSshMachineLocation(Iterable)} */
    public static Optional<SshMachineLocation> findUniqueSshMachineLocation(Iterable<? extends Location> locations) {
        return Machines.findUniqueSshMachineLocation(locations);
    }

    /** if no locations are supplied, returns locations on the entity, or in the ancestors, until it finds a non-empty set,
     * or ultimately the empty set if no locations are anywhere */ 
    public static Collection<? extends Location> getLocationsCheckingAncestors(Collection<? extends Location> locations, Entity entity) {
        // look in ancestors if location not set here
        Entity ancestor = entity;
        while ((locations==null || locations.isEmpty()) && ancestor!=null) {
            locations = ancestor.getLocations();
            ancestor = ancestor.getParent();
        }
        return locations;
    }
    
}
