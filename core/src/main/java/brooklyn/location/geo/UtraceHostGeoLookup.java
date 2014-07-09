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

import groovy.util.Node;
import groovy.util.NodeList;
import groovy.util.XmlParser;

import java.io.IOException;
import java.net.InetAddress;
import java.net.MalformedURLException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.util.net.Networking;
import brooklyn.util.time.Duration;

import com.google.common.base.Throwables;

public class UtraceHostGeoLookup implements HostGeoLookup {

    /*
     * 
http://xml.utrace.de/?query=88.198.156.18 
(IP address or hostname)

The XML result is as follows:

<?xml version="1.0" encoding="iso-8869-1"?>
<results>
<result>
<ip>88.198.156.18</ip>
<host>utrace.de</host>
<isp>Hetzner Online AG</isp>
<org>Pagedesign GmbH</org>
<region>Hamburg</region>
<countrycode>DE</countrycode>
<latitude>53.5499992371</latitude>
<longitude>10</longitude>
<queries>10</queries>
</result>
</results>

Note the queries count field -- you are permitted 100 per day.
Beyond this you get blacklisted and requests may time out, or return none.
(This may last for several days once blacklisting, not sure how long.)
     */
    
    public static final Logger log = LoggerFactory.getLogger(UtraceHostGeoLookup.class);
    
    public String getLookupUrlForPublicIp(String ip) {
        return "http://xml.utrace.de/?query="+ip.trim();
    }

    /**
     * @deprecated since 0.7.0. Use {@link brooklyn.location.geo.LocalhostExternalIpLoader} instead.
     */
    @Deprecated
    public static String getLocalhostExternalIp() {
        return LocalhostExternalIpLoader.getLocalhostIpWithin(Duration.seconds(2));
    }
    
    /**
     * @deprecated since 0.7.0. Use {@link brooklyn.location.geo.LocalhostExternalIpLoader} instead.
     */
    @Deprecated
    public static String getLocalhostExternalIpImpl() {
        return LocalhostExternalIpLoader.getLocalhostIpWithin(Duration.seconds(2));
    }
    
    public String getLookupUrlForLocalhost() {
        return getLookupUrlForPublicIp(LocalhostExternalIpLoader.getLocalhostIpQuicklyOrDefault());
    }

    /** returns URL to get properties for the given address (assuming localhost if address is on a subnet) */
    public String getLookupUrlFor(InetAddress address) {
        if (Networking.isPrivateSubnet(address)) return getLookupUrlForLocalhost();
        return getLookupUrlForPublicIp(address.getHostAddress());
    }
    
    private static boolean LOGGED_GEO_LOOKUP_UNAVAILABLE = false;
    
    public HostGeoInfo getHostGeoInfo(InetAddress address) throws MalformedURLException, IOException {
        String url = getLookupUrlFor(address);
        if (log.isDebugEnabled())
            log.debug("Geo info lookup for "+address+" at "+url);
        Node xml;
        try {
            xml = new XmlParser().parse(getLookupUrlFor(address));
        } catch (Exception e) {
            if (log.isDebugEnabled())
                log.debug("Geo info lookup for "+address+" failed: "+e);
            if (!LOGGED_GEO_LOOKUP_UNAVAILABLE) {
                LOGGED_GEO_LOOKUP_UNAVAILABLE = true;
                log.info("Geo info lookup unavailable (for "+address+"; cause "+e+")");
            }
            return null;
        }
        try {
            String org = getXmlResultsField(xml, "org").trim();
            if (org.isEmpty()) org = getXmlResultsField(xml, "isp").trim();
            String region = getXmlResultsField(xml, "region").trim();
            if (!org.isEmpty()) {
                if (!region.isEmpty()) region = org+", "+region;
                else region = org;
            }
            if (region.isEmpty()) region = getXmlResultsField(xml, "isp").trim();
            if (region.isEmpty()) region = address.toString();
            HostGeoInfo geo = new HostGeoInfo(address.getHostName(), 
                    region+
                    " ("+getXmlResultsField(xml, "countrycode")+")", 
                    Double.parseDouble(""+getXmlResultsField(xml, "latitude")), 
                    Double.parseDouble(""+getXmlResultsField(xml, "longitude")));
            log.info("Geo info lookup for "+address+" returned: "+geo);
            return geo;
        } catch (Exception e) {
            if (log.isDebugEnabled())
                log.debug("Geo info lookup failed, for "+address+" at "+url+", due to "+e+"; response is "+xml);
            throw Throwables.propagate(e);
        }
    }
    
    private static String getXmlResultsField(Node xml, String field) {
        Node r1 = ((Node)((NodeList)xml.get("result")).get(0));
        Node f1 = ((Node)((NodeList)r1.get(field)).get(0));
        return f1.text();
    }
}
