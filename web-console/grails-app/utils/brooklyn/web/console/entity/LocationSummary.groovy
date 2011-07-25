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
    final String longitude;
    final String latitude;

    public LocationSummary(Location location) {
        this.name = location.getName();
        this.displayName = location.getLocationProperty('displayName');
        this.latitude = location.getLocationProperty('latitude');
        this.longitude = location.getLocationProperty('longitude');
    }
}

