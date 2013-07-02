package brooklyn.location.geo;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.Serializable;
import java.math.BigDecimal;
import java.net.InetAddress;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.location.AddressableLocation;
import brooklyn.location.Location;
import brooklyn.location.basic.AbstractLocation;
import brooklyn.util.internal.BrooklynSystemProperties;

/**
 * Encapsulates geo-IP information for a given host.
 */
public class HostGeoInfo implements Serializable {
    
	private static final long serialVersionUID = -5866759901535266181L;

    public static final Logger log = LoggerFactory.getLogger(HostGeoInfo.class);

	/** the IP address */
    public final String address;
    
    public final String displayName;
    
    public final double latitude;
    public final double longitude;

    public static HostGeoInfo create(String address, String displayName, double latitude, double longitude) {
        return new HostGeoInfo(address, displayName, latitude, longitude);
    }
    
    public static HostGeoInfo fromIpAddress(InetAddress address) {
        try {
            HostGeoLookup lookup = findHostGeoLookupImpl();
            if (lookup!=null) {
                return lookup.getHostGeoInfo(address);
            }
        } catch (Exception e) {
            if (log.isDebugEnabled())
                log.debug("unable to look up geo DNS info for "+address, e);
        }
        return null;
    }
    
    public static HostGeoInfo fromLocation(Location l) {
        if (l instanceof HasHostGeoInfo) {
            HostGeoInfo result = ((HasHostGeoInfo)l).getHostGeoInfo();
            if (result!=null) return result;
        }
        InetAddress address = findIpAddress(l);
        Object latitude = l.findLocationProperty("latitude");
        Object longitude = l.findLocationProperty("longitude");

        if (address == null) return null;
        if (latitude == null || longitude == null) {
            HostGeoInfo geo = fromIpAddress(address);
            if (geo==null) return null;
            latitude = geo.latitude;
            longitude = geo.longitude;
        }
        if (latitude instanceof BigDecimal) latitude = ((BigDecimal) latitude).doubleValue();
        if (longitude instanceof BigDecimal) longitude = ((BigDecimal) longitude).doubleValue();
        if (!(latitude instanceof Double) || !(longitude instanceof Double))
            throw new IllegalArgumentException("Passed location specifies invalid type of lat/long");
        
        HostGeoInfo result = new HostGeoInfo(address.getHostAddress(), l.getDisplayName(), (Double) latitude, (Double) longitude);
        if (l instanceof AbstractLocation) {
            ((AbstractLocation)l).setHostGeoInfo(result);
        }
        return result;
    }
    
    private static HostGeoLookup findHostGeoLookupImpl() throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        String type = BrooklynSystemProperties.HOST_GEO_LOOKUP_IMPL.getValue();
        //like utrace because it seems more accurate than geobytes and gives a report of how many tokens are left
        //but maxmind free is even better
        if (type==null) return new MaxMindHostGeoLookup();
        if (type.isEmpty()) return null;
        return (HostGeoLookup) Class.forName(type).newInstance();
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
        if (l instanceof AddressableLocation)
            return ((AddressableLocation) l).getAddress();
        return findIpAddress(l.getParent());
    }
    
    
    public HostGeoInfo(String address, String displayName, double latitude, double longitude) {
        this.address = checkNotNull(address, "address");
        this.displayName = displayName==null ? "" : displayName;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public String getAddress() {
        return address;
    }
    
    @Override
    public String toString() {
        return "HostGeoInfo["+displayName+": "+address+" at ("+latitude+","+longitude+")]";
    }
    
    @Override
    public boolean equals(Object o) {
        // Slight cheat: only includes the address + displayName field (displayName to allow overloading localhost etc)
        return (o instanceof HostGeoInfo) && address.equals(((HostGeoInfo) o).address)
                && displayName.equals(((HostGeoInfo) o).displayName);
    }
    
    @Override
    public int hashCode() {
        // Slight cheat: only includes the address + displayName field (displayName to allow overloading localhost etc)
        return address.hashCode() * 31 + displayName.hashCode();
    }
    
}
