/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package brooklyn.location.geo;

import java.io.Serializable;
import java.net.InetAddress;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.entity.Entity;
import brooklyn.location.AddressableLocation;
import brooklyn.location.Location;
import brooklyn.location.basic.AbstractLocation;
import brooklyn.location.basic.LocationConfigKeys;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.flags.TypeCoercions;
import brooklyn.util.guava.Maybe;
import brooklyn.util.internal.BrooklynSystemProperties;

import com.google.common.base.Objects;

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
    
    private static Maybe<HostGeoLookup> cachedLookup = null;

    public static HostGeoInfo create(String address, String displayName, double latitude, double longitude) {
        return new HostGeoInfo(address, displayName, latitude, longitude);
    }
    
    public static HostGeoInfo fromIpAddress(InetAddress address) {
        try {
            HostGeoLookup lookup = getDefaultLookup();
            if (lookup!=null)
                return lookup.getHostGeoInfo(address);
        } catch (Exception e) {
            if (log.isDebugEnabled())
                log.debug("unable to look up geo DNS info for "+address, e);
        }
        return null;
    }

    @Nullable
    public static HostGeoLookup getDefaultLookup() throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        if (cachedLookup==null) {
            cachedLookup = Maybe.of(findHostGeoLookupImpl());
        }                
        return cachedLookup.get();
    }
    
    /** returns null if cannot be set */
    public static HostGeoInfo fromLocation(Location l) {
        if (l==null) return null;
        
        Location la = l;
        HostGeoInfo resultFromLocation = null;
        while (la!=null) {
            if (la instanceof HasHostGeoInfo) {
                resultFromLocation = ((HasHostGeoInfo)l).getHostGeoInfo();
                if (resultFromLocation!=null) break;
            }
            la = la.getParent();
        }
        if (resultFromLocation!=null && l==la) {
            // from the location
            return resultFromLocation;
        }
        // resultFromLocation may be inherited, in which case we will copy it later
        
        InetAddress address = findIpAddress(l);
        Object latitude = l.getConfig(LocationConfigKeys.LATITUDE);
        Object longitude = l.getConfig(LocationConfigKeys.LONGITUDE);

        if (resultFromLocation!=null && (latitude == null || longitude == null)) {
            latitude = resultFromLocation.latitude;
            longitude = resultFromLocation.longitude;            
        }
        if (address!=null && (latitude == null || longitude == null)) {
            HostGeoInfo geo = fromIpAddress(address);
            if (geo==null) return null;
            latitude = geo.latitude;
            longitude = geo.longitude;
        }
        
        if (latitude==null || longitude==null)
            return null;
        
        Exception error=null;
        try {
            latitude = TypeCoercions.castPrimitive(latitude, Double.class);
            longitude = TypeCoercions.castPrimitive(longitude, Double.class);
        } catch (Exception e) {
            Exceptions.propagateIfFatal(e);
            error = e;
        }
        if (error!=null || !(latitude instanceof Double) || !(longitude instanceof Double))
            throw new IllegalArgumentException("Location "+l+" specifies invalid type of lat/long: " +
                    "lat="+latitude+" (type "+(latitude==null ? null : latitude.getClass())+"); " +
                    "lon="+longitude+" (type "+(longitude==null ? null : longitude.getClass())+")", error);
        
        HostGeoInfo result = new HostGeoInfo(address!=null ? address.getHostAddress() : null, l.getDisplayName(), (Double) latitude, (Double) longitude);
        if (l instanceof AbstractLocation) {
            ((AbstractLocation)l).setHostGeoInfo(result);
        }
        return result;
    }
    
    private static HostGeoLookup findHostGeoLookupImpl() throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        String type = BrooklynSystemProperties.HOST_GEO_LOOKUP_IMPL.getValue();
        /* utrace seems more accurate than geobytes, and it gives a report of how many tokens are left;
         * but maxmind if it's installed locally is even better (does not require remote lookup),
         * so use it if available */
        if (type==null) {
            if (MaxMind2HostGeoLookup.getDatabaseReader()!=null)
                return new MaxMind2HostGeoLookup();
            log.debug("Using Utrace remote for geo lookup because MaxMind2 is not available");
            return new UtraceHostGeoLookup();
        }
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
        this.address = address;
        this.displayName = displayName==null ? "" : displayName;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public String getAddress() {
        return address;
    }
    
    @Override
    public String toString() {
        return "HostGeoInfo["+displayName+": "+(address!=null ? address : "(no-address)")+" at ("+latitude+","+longitude+")]";
    }
    
    @Override
    public boolean equals(Object o) {
        // Slight cheat: only includes the address + displayName field (displayName to allow overloading localhost etc)
        return (o instanceof HostGeoInfo) && Objects.equal(address, ((HostGeoInfo) o).address)
                && Objects.equal(displayName, ((HostGeoInfo) o).displayName);
    }
    
    @Override
    public int hashCode() {
        // Slight cheat: only includes the address + displayName field (displayName to allow overloading localhost etc)
        return Objects.hashCode(address, displayName);
    }
    
}
