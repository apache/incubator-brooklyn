package brooklyn.location.basic;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

import brooklyn.location.Location;
import brooklyn.location.MachineProvisioningLocation;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;

public class LocationFunctions {

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
    
    public static Predicate<Location> isOfType(final Class<? extends Location> type) {
        return new Predicate<Location>() {
            @Override
            public boolean apply(Location input) {
                return type!=null && type.isInstance(input);
            }
        };
    }
    
    public static Predicate<Location> isNotOfType(final Class<? extends Location> type) {
        return Predicates.not(isOfType(type));
    }

    @SuppressWarnings("unchecked")
    public static Collection<Location> filter(Collection<? extends Location> locations, Predicate<Location> notOfType) {
        return (Collection<Location>) Collections2.filter(locations, LocationFunctions.isNotOfType(MachineProvisioningLocation.class));
    }
    
}
