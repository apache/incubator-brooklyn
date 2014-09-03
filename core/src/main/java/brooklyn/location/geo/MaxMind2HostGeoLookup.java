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

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.MalformedURLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.util.internal.BrooklynSystemProperties;
import brooklyn.util.net.Networking;
import brooklyn.util.text.Strings;

import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.model.CityResponse;
import com.maxmind.geoip2.record.Subdivision;

public class MaxMind2HostGeoLookup implements HostGeoLookup {

    public static final Logger log = LoggerFactory.getLogger(MaxMind2HostGeoLookup.class);
    
    static final String MAXMIND_DB_URL = "http://dev.maxmind.com/geoip/geoip2/geolite2/#Downloads";
    // TODO this should be configurable from system property or brooklyn.properties
    // TODO and should use properties BrooklynServerConfig.MGMT_BASE_DIR (but hard to get mgmt properties here!)
    static final String MAXMIND_DB_PATH = System.getProperty("user.home")+"/"+".brooklyn/"+"GeoLite2-City.mmdb";
    
    static boolean lookupFailed = false;
    static DatabaseReader databaseReader = null;
    
    public static synchronized DatabaseReader getDatabaseReader() {
        if (databaseReader!=null) return databaseReader;
        try {
            File f = new File(MAXMIND_DB_PATH);
            databaseReader = new DatabaseReader.Builder(f).build();
        } catch (IOException e) {
            lookupFailed = true;
            log.debug("MaxMind geo lookup unavailable; either download and unpack the latest "+
                    "binary from "+MAXMIND_DB_URL+" into "+MAXMIND_DB_PATH+", "+
                    "or specify a different HostGeoLookup implementation with the key "+
                    BrooklynSystemProperties.HOST_GEO_LOOKUP_IMPL.getPropertyName()+" (error trying to read: "+e+")");
        }
        return databaseReader;
    }
    
    public HostGeoInfo getHostGeoInfo(InetAddress address) throws MalformedURLException, IOException {
        if (lookupFailed) return null;
        
        DatabaseReader ll = getDatabaseReader();
        if (ll==null) return null;
        
        InetAddress extAddress = address;
        if (Networking.isPrivateSubnet(extAddress)) extAddress = InetAddress.getByName(LocalhostExternalIpLoader.getLocalhostIpQuicklyOrDefault());
        
        try {
            CityResponse l = ll.city(extAddress);
            if (l==null) {
                if (log.isDebugEnabled()) log.debug("Geo info failed to find location for address {}, using {}", extAddress, ll);
                return null;
            }
            
            StringBuilder name = new StringBuilder();
            
            if (l.getCity()!=null && l.getCity().getName()!=null) name.append(l.getCity().getName());
            
            if (l.getSubdivisions()!=null) {
                for (Subdivision subd: Lists.reverse(l.getSubdivisions())) {
                    if (name.length()>0) name.append(", ");
                    // prefer e.g. USA state codes over state names
                    if (!Strings.isBlank(subd.getIsoCode())) 
                        name.append(subd.getIsoCode());
                    else
                        name.append(subd.getName());
                }
            }
            
            if (l.getCountry()!=null) {
                if (name.length()==0) {
                    name.append(l.getCountry().getName());
                } else {
                    name.append(" ("); name.append(l.getCountry().getIsoCode()); name.append(")");
                }
            }

            
            HostGeoInfo geo = new HostGeoInfo(address.getHostName(), name.toString(), l.getLocation().getLatitude(), l.getLocation().getLongitude());
            log.debug("Geo info lookup (MaxMind DB) for "+address+" returned: "+geo);
            return geo;
        } catch (Exception e) {
            if (log.isDebugEnabled())
                log.debug("Geo info lookup failed: "+e);
            throw Throwables.propagate(e);
        }
    }
}
