package brooklyn.location.geo;

import java.io.IOException;
import java.net.InetAddress;
import java.net.MalformedURLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.util.internal.BrooklynSystemProperties;
import brooklyn.util.net.Networking;

import com.google.common.base.Throwables;
import com.maxmind.geoip.LookupService;

public class MaxMindHostGeoLookup implements HostGeoLookup {

    public static final Logger log = LoggerFactory.getLogger(MaxMindHostGeoLookup.class);
    
    static final String MAXMIND_DB_PATH = System.getProperty("user.home")+"/"+".brooklyn/"+"MaxMind-GeoLiteCity.dat";
    
    static boolean lookupFailed = false;
    static LookupService lookup = null;
    
    public static synchronized LookupService getLookup() {
        if (lookup!=null) return lookup;
        try {
            lookup = new LookupService(MAXMIND_DB_PATH);
        } catch (IOException e) {
            lookupFailed = true;
            log.debug("MaxMind geo lookup unavailable; either download and unpack the latest "+
                    "http://www.maxmind.com/app/geolitecity binary into "+MAXMIND_DB_PATH+", "+
                    "or specify a different HostGeoLookup implementation with the key "+
                    BrooklynSystemProperties.HOST_GEO_LOOKUP_IMPL.getPropertyName()+" (error trying to read: "+e+")");
        }
        return lookup;
    }
    
    public HostGeoInfo getHostGeoInfo(InetAddress address) throws MalformedURLException, IOException {
        if (lookupFailed) return null;
        LookupService ll = getLookup();
        if (ll==null) return null;
        
        InetAddress extAddress = address;
        if (Networking.isPrivateSubnet(extAddress)) extAddress = InetAddress.getByName(UtraceHostGeoLookup.getLocalhostExternalIp());
        
        com.maxmind.geoip.Location l = ll.getLocation(extAddress);
        if (l==null) {
            if (log.isDebugEnabled()) log.debug("Geo info failed to find location for address {}, using {}", extAddress, ll);
            return null;
        }
        
        try {
            StringBuilder name = new StringBuilder();
            
            if (l.city!=null) name.append(l.city);
            
            if (l.region!=null && !l.region.equals("U1")) {
                if (name.length()>0) name.append(", ");
                name.append(l.region);
            }
            
            if (name.length()==0) name.append(l.countryName);

            name.append(" ("); name.append(l.countryCode); name.append(")");
            
            HostGeoInfo geo = new HostGeoInfo(address.getHostName(), name.toString(), l.latitude, l.longitude);
            log.debug("Geo info lookup (MaxMind DB) for "+address+" returned: "+geo);
            return geo;
        } catch (Exception e) {
            if (log.isDebugEnabled())
                log.debug("Geo info lookup failed: "+e);
            throw Throwables.propagate(e);
        }
    }
}
