package brooklyn.entity.dns;

import java.net.InetAddress;

import brooklyn.entity.Entity;
import brooklyn.location.Location;
import brooklyn.location.MachineLocation;

public class LocationUtils {
    
    public static InetAddress findIpAddress(Entity e) {
        for (Location l : e.getLocations()) {
            InetAddress address = findIpAddress(l);
            if (address != null) return address;
        }
        return null;
    }
    
    public static Double findLatitude(Entity e) {
        for (Location l : e.getLocations()) {
            Double latitude = findLatitude(l);
            if (latitude != null) return latitude;
        }
        return null;
    }
    
    public static Double findLongitude(Entity e) {
        for (Location l : e.getLocations()) {
            Double longitude = findLongitude(l);
            if (longitude != null) return longitude;
        }
        return null;
    }
    
    public static InetAddress findIpAddress(Location l) {
        if (l == null)
            return null;
        if (l instanceof MachineLocation)
            return ((MachineLocation) l).getAddress();
        return findIpAddress(l.getParentLocation());
    }
    
    public static Double findLatitude(Location l) {
        if (l == null)
            return null;
        if (l instanceof MachineLocation.WithCoordinates)
            return ((MachineLocation.WithCoordinates) l).getLatitude();
        return findLatitude(l.getParentLocation());
    }
    
    public static Double findLongitude(Location l) {
        if (l == null)
            return null;
        if (l instanceof MachineLocation.WithCoordinates)
            return ((MachineLocation.WithCoordinates) l).getLongitude();
        return findLatitude(l.getParentLocation());
    }
    
}
