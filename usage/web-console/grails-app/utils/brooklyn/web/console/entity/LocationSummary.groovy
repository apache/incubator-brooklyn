package brooklyn.web.console.entity;

import brooklyn.location.Location
import brooklyn.location.basic.AbstractLocation

/** Summary of a Brookln Entity Location   */
public class LocationSummary {
    final String displayName
    final String description
    final String streetAddress
    final String longitude
    final String latitude
    final String iso3166
    final Set<String> parentLocations

    public LocationSummary(Location location) {
        displayName = getNameOfLoc(location)
        description = location.findLocationProperty('description')
        streetAddress = location.findLocationProperty('streetAddress')
        latitude = location.findLocationProperty('latitude')
        longitude = location.findLocationProperty('longitude')
        iso3166 = location.findLocationProperty('iso3166')
        parentLocations = [displayName] as Set
        addParents(location)
        parentLocations.remove(displayName)
    }

    private String getNameOfLoc(Location loc) {
        return loc.getLocationProperty('displayName') ?:
                loc.getName() ?:
                  loc.findLocationProperty('displayName')
    }

    private void addParents(Location loc) {
        if (loc.parentLocation) {
            parentLocations.add(getNameOfLoc(loc.parentLocation))
            addParents(loc.parentLocation)
        }
    }
}

