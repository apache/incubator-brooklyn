package brooklyn.entity.dns;

import java.net.InetAddress;

import brooklyn.location.Location;
import brooklyn.location.MachineLocation;

public class LocationUtils {
    
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
