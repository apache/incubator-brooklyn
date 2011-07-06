package com.cloudsoftcorp.monterey.brooklyn.entity

import brooklyn.location.basic.AbstractLocation

import com.cloudsoftcorp.monterey.location.api.MontereyActiveLocation
import com.google.common.base.Preconditions

// FIXME Better name, and better implementation!
class FooLocation extends AbstractLocation {

    private final MontereyActiveLocation montereyLocation;
    
    FooLocation(MontereyActiveLocation montereyLocation) {
        this.montereyLocation = Preconditions.checkNotNull(montereyLocation, "montereyLocation");
    }
    
    @Override
    public String toString() {
        return montereyLocation.toString();
    }
}
