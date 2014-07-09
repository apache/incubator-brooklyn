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

import java.io.IOException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.util.net.Networking;

public class GeoBytesHostGeoLookup implements HostGeoLookup {

    public static final Logger log = LoggerFactory.getLogger(GeoBytesHostGeoLookup.class);
    
    /*
    curl "http://www.geobytes.com/IpLocator.htm?GetLocation&template=valuepairs.txt&IpAddress=geobytes.com"
    known=1
    countryid=254
    country=United States
    fips104=US
    iso2=US
    iso3=USA
    ison=840
    internet=US
    comment=
    regionid=142
    region=Maryland
    code=MD
    adm1code=    
    cityid=8909
    city=Baltimore
    latitude=39.2894
    longitude=-76.6384
    timezone=-05:00
    dmaid=512
    dma=512
    market=Baltimore
    certainty=78
    isproxy=false
    mapbytesremaining=Free
    */
    
    public String getPropertiesLookupUrlForPublicIp(String ip) {
        return "http://www.geobytes.com/IpLocator.htm?GetLocation&template=valuepairs.txt&IpAddress="+ip.trim();
    }

    public String getPropertiesLookupUrlForLocalhost() {
        return "http://www.geobytes.com/IpLocator.htm?GetLocation&template=valuepairs.txt";
    }

    /** returns URL to get properties for the given address (assuming localhost if address is on a subnet) */
    public String getPropertiesLookupUrlFor(InetAddress address) {
        if (Networking.isPrivateSubnet(address)) return getPropertiesLookupUrlForLocalhost();
        return getPropertiesLookupUrlForPublicIp(address.getHostAddress());
    }
    
    private static boolean LOGGED_GEO_LOOKUP_UNAVAILABLE = false;
    
    public HostGeoInfo getHostGeoInfo(InetAddress address) throws MalformedURLException, IOException {
        String url = getPropertiesLookupUrlFor(address);
        if (log.isDebugEnabled())
            log.debug("Geo info lookup for "+address+" at "+url);
        Properties props = new Properties();
        try {
            props.load( new URL(url).openStream() );
        } catch (Exception e) {
            if (log.isDebugEnabled())
                log.debug("Geo info lookup for "+address+" failed: "+e);
            if (!LOGGED_GEO_LOOKUP_UNAVAILABLE) {
                LOGGED_GEO_LOOKUP_UNAVAILABLE = true;
                log.info("Geo info lookup unavailable (for "+address+"; cause "+e+")");
            }
            return null;
        }
        HostGeoInfo geo = new HostGeoInfo(address.getHostName(), props.getProperty("city")+" ("+props.getProperty("iso2")+")", 
                Double.parseDouble(props.getProperty("latitude")), Double.parseDouble(props.getProperty("longitude")));
        log.info("Geo info lookup for "+address+" returned: "+geo);
        return geo;
    }
    
}
