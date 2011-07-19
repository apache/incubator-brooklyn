package brooklyn.entity.dns;

/**
 * Encapsulates geo-IP information for a given server.
 */
public class ServerGeoInfo {
    public final String address;
    public final String displayName;
    public final double latitude;
    public final double longitude;

    
    public ServerGeoInfo(String address, String displayName, double latitude, double longitude) {
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
        return (o instanceof ServerGeoInfo) && address.equals(((ServerGeoInfo) o).address);
    }
    
    @Override
    public int hashCode() {
        // Slight cheat: only includes the address field.
        return address.hashCode();
    }
    
}
