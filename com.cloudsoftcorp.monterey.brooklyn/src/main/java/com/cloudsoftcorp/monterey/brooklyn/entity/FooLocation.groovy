package com.cloudsoftcorp.monterey.brooklyn.entity

import brooklyn.location.Location

import com.cloudsoftcorp.monterey.location.api.MontereyActiveLocation

// FIXME Better name, and better implementation!
class FooLocation implements Location {

    private final MontereyActiveLocation montereyLocation;
    
    FooLocation(MontereyActiveLocation montereyLocation) {
        this.montereyLocation = montereyLocation;
    }
}
