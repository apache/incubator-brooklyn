package brooklyn.entity.dns;

import java.math.BigDecimal;
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
        Object latitude = l.findLocationProperty("latitude");
        Object longitude = l.findLocationProperty("longitude");

        if (address == null) return null;
        if (latitude == null || longitude == null) {
            //TODO attempt to discover it from the IP address
            return null;
        }
        if (latitude instanceof BigDecimal) latitude = ((BigDecimal) latitude).doubleValue();
        if (longitude instanceof BigDecimal) longitude = ((BigDecimal) longitude).doubleValue();
        if (!(latitude instanceof Double) || !(longitude instanceof Double))
            throw new IllegalArgumentException("Passed location specifies invalid type of lat/long");
        
        return new HostGeoInfo(address.getHostAddress(), l.getName(), (Double) latitude, (Double) longitude);
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
