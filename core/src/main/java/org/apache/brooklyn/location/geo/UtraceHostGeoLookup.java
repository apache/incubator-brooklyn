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
package org.apache.brooklyn.location.geo;

import groovy.util.Node;
import groovy.util.NodeList;
import groovy.util.XmlParser;

import java.io.IOException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.javalang.JavaClassNames;
import brooklyn.util.net.Networking;
import brooklyn.util.time.Duration;
import brooklyn.util.time.Durations;

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
    
    /** after failures, subsequent retries within this time interval are blocked */
    private static final Duration RETRY_INTERVAL = Duration.FIVE_MINUTES;
    /** requests taking longer than this period are deemed to have timed out and failed;
     * set reasonably low so that if we are blacklisted for making too many requests,
     * the call to get geo info does not take very long */
    private static final Duration REQUEST_TIMEOUT = Duration.seconds(3);
    
    public static final Logger log = LoggerFactory.getLogger(UtraceHostGeoLookup.class);
    
    public String getLookupUrlForPublicIp(String ip) {
        return "http://xml.utrace.de/?query="+ip.trim();
    }

    /**
     * @deprecated since 0.7.0. Use {@link LocalhostExternalIpLoader} instead.
     */
    @Deprecated
    public static String getLocalhostExternalIp() {
        return LocalhostExternalIpLoader.getLocalhostIpWithin(Duration.seconds(2));
    }
    
    /**
     * @deprecated since 0.7.0. Use {@link LocalhostExternalIpLoader} instead.
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
    private static long LAST_FAILURE_UTC = -1;
    
    /** does the {@link #retrieveHostGeoInfo(InetAddress)}, but in the background with a default timeout */
    public HostGeoInfo getHostGeoInfo(InetAddress address) throws MalformedURLException, IOException {
        if (Duration.sinceUtc(LAST_FAILURE_UTC).compareTo(RETRY_INTERVAL) < 0) {
            // wait at least 60s since a failure
            return null;
        }
        return getHostGeoInfo(address, REQUEST_TIMEOUT);
    }
    
    /** does a {@link #retrieveHostGeoInfo(InetAddress)} with a timeout (returning null, interrupting, and setting failure time) */
    public HostGeoInfo getHostGeoInfo(final InetAddress address, Duration timeout) throws MalformedURLException, IOException {
        final AtomicReference<HostGeoInfo> result = new AtomicReference<HostGeoInfo>();
        Thread lt = new Thread() {
            public void run() {
                try {
                    result.set(retrieveHostGeoInfo(address));
                } catch (Exception e) {
                    log.warn("Error computing geo info for "+address+"; internet issues or too many requests to (free) servers for "+JavaClassNames.simpleClassName(UtraceHostGeoLookup.this)+": "+e);
                    log.debug("Detail of host geo error: "+e, e);
                }
            }
        };
        lt.start();

        try {
            Durations.join(lt, timeout);
        } catch (InterruptedException e) {
            throw Exceptions.propagate(e);
        }
        
        if (lt.isAlive()) {
            // interrupt and set the failure time so that subsequent attempts do not face this timeout
            lt.interrupt();
            LAST_FAILURE_UTC = System.currentTimeMillis();
            log.debug("Geo info lookup for "+address+" timed out after "+timeout);
        }
        
        return result.get();
    }
    
    public HostGeoInfo retrieveHostGeoInfo(InetAddress address) throws MalformedURLException, IOException {
        String url = getLookupUrlFor(address);
        if (log.isDebugEnabled())
            log.debug("Geo info lookup for "+address+" at "+url);
        Node xml;
        try {
            xml = new XmlParser().parse(getLookupUrlFor(address));
        } catch (Exception e) {
            LAST_FAILURE_UTC = System.currentTimeMillis();
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
    
    @Nullable
    private static Node getFirstChild(Node xml, String field) {
        if (xml==null) return null;
        NodeList nl = (NodeList)xml.get(field);
        if (nl==null || nl.isEmpty()) return null;
        return (Node)nl.get(0);
    }
    @Nonnull
    private static String getXmlResultsField(Node xml, String field) {
        Node f1 = getFirstChild(getFirstChild(xml, "result"), field);
        if (f1==null) return "";
        return f1.text();
    }
}
