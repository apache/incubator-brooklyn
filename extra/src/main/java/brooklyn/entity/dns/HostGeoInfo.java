package brooklyn.entity.dns;

import java.net.InetAddress;

import brooklyn.entity.Entity;
import brooklyn.location.Location;
import brooklyn.location.MachineLocation;

/**
 * Encapsulates geo-IP information for a given host.
 */
public class HostGeoInfo {
    public final String address;
    public final String displayName;
    public final double latitude;
    public final double longitude;

    
    public static HostGeoInfo fromLocation(Location l) {
        InetAddress address = findIpAddress(l);
        Double latitude = (Double) l.findLocationProperty("latitude");
        Double longitude = (Double) l.findLocationProperty("longitude");

        if (address == null || latitude == null || longitude == null)
            return null;
        
        return new HostGeoInfo(address.toString(), l.getName(), latitude, longitude);
    }
    
    public static HostGeoInfo fromEntity(Entity e) {
        for (Location l : e.getLocations()) {
            HostGeoInfo hgi = fromLocation(l);
            if (hgi != null)
                return hgi;
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
    
    
    public HostGeoInfo(String address, String displayName, double latitude, double longitude) {
        this.address = address;
        this.displayName = displayName;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    @Override
    public String toString() {
        return "ServerGeoInfo["+displayName+": "+address+" at ("+latitude+","+longitude+")]";
    }
    
    @Override
    public boolean equals(Object o) {
        // Slight cheat: only tests the address field.
        return (o instanceof HostGeoInfo) && address.equals(((HostGeoInfo) o).address);
    }
    
    @Override
    public int hashCode() {
        // Slight cheat: only includes the address field.
        return address.hashCode();
    }
    
}
