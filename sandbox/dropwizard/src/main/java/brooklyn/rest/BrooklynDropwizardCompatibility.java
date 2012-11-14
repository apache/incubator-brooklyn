package brooklyn.rest;

import brooklyn.rest.domain.LocationSpec;
import brooklyn.rest.legacy.LocationStore;

public class BrooklynDropwizardCompatibility {

    public static LocationStore newLocationStore(BrooklynConfiguration configuration) {
        return new LocationStore(configuration.getLocations().toArray(new LocationSpec[0]));
    }

    
}
