package brooklyn.entity.basic;

import java.util.List;

import brooklyn.config.ConfigKey;
import brooklyn.entity.Entity;
import brooklyn.entity.proxying.ImplementedBy;
import brooklyn.entity.trait.Startable;
import brooklyn.location.Location;
import brooklyn.location.basic.Locations;

import com.google.common.collect.ImmutableList;

/**
 * Provides a pass-through Startable entity used for keeping hierarchies tidy. 
 */
@ImplementedBy(BasicStartableImpl.class)
public interface BasicStartable extends Entity, Startable {

    /** @deprecated since 0.7.0; use {@link Locations#LocationFilter} */
    @Deprecated
    public interface LocationsFilter extends Locations.LocationsFilter {
        /** @deprecated since 0.7.0; use {@link Locations#USE_FIRST_LOCATION} */
        public static final LocationsFilter USE_FIRST_LOCATION = new LocationsFilter() {
            private static final long serialVersionUID = 3100091615409115890L;

            @Override
            public List<Location> filterForContext(List<Location> locations, Object context) {
                if (locations.size()<=1) return locations;
                return ImmutableList.of(locations.get(0));
            }
        };
    }

    public static final ConfigKey<brooklyn.location.basic.Locations.LocationsFilter> LOCATIONS_FILTER = ConfigKeys.newConfigKey(brooklyn.location.basic.Locations.LocationsFilter.class,
            "brooklyn.locationsFilter", "Provides a hook for customizing locations to be used for a given context");
}
