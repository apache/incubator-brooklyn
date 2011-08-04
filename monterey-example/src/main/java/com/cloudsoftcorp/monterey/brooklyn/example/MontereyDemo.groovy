package com.cloudsoftcorp.monterey.brooklyn.example

import java.util.List

import brooklyn.demo.Locations
import brooklyn.launcher.BrooklynLauncher
import brooklyn.location.Location

@Deprecated // use MontereySpringTravelDemo instead
class MontereyDemo {

    public static final List<String> DEFAULT_LOCATIONS = [ Locations.LOCALHOST ]

    public static void main(String[] argv) {
        List<String> ids = argv.length == 0 ? DEFAULT_LOCATIONS : Arrays.asList(argv)
        println "Starting in locations: "+ids
        List<Location> locations = Locations.getLocationsById(ids)

        MontereySpringTravel app = new MontereySpringTravel(name:'brooklyn-wide-area-demo',
            displayName:'Brooklyn Wide-Area Spring Travel Demo Application')

        BrooklynLauncher.manage(app)
        app.start(locations)
    }
}
