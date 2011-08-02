package brooklyn.web.console.entity;

import brooklyn.entity.Entity
import brooklyn.entity.EntityClass
import brooklyn.entity.Group
import brooklyn.entity.Effector
import brooklyn.entity.basic.AbstractEntity
import brooklyn.event.AttributeSensor
import brooklyn.event.Sensor
import brooklyn.location.Location
import brooklyn.location.basic.AbstractLocation

/** Summary of a Brookln Entity Location   */
public class LocationSummary {

    final String name;
    final String displayName;
    final String description;
    final String streetAddress;
    final String longitude;
    final String latitude;
    final String iso3166Code;

    public LocationSummary(Location location) {
        this.name = location.getName();
        this.description = location.findLocationProperty('description');
        this.streetAddress = location.findLocationProperty('streetAddress');
        this.displayName = location.findLocationProperty('displayName') ?: location.getName();
        this.latitude = location.findLocationProperty('latitude');
        this.longitude = location.findLocationProperty('longitude');
        this.iso3166Code = location.findLocationProperty('iso');
    }
}

