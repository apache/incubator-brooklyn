package brooklyn.location.geo;

import groovy.util.Node;
import groovy.util.NodeList;
import groovy.util.XmlParser;

import java.io.IOException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.ResourceUtils;
import brooklyn.util.exceptions.RuntimeInterruptedException;
import brooklyn.util.net.Networking;

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

    private static final AtomicBoolean retrievingLocalExternalIp = new AtomicBoolean(false);
    private static final CountDownLatch triedLocalExternalIp = new CountDownLatch(1);
    static volatile String localExternalIp;

    /** returns public IP of localhost */
    public static String getLocalhostExternalIp() {
        if (localExternalIp!=null) return localExternalIp;

        // do in private thread, otherwise blocks for 30s+ on dodgy network!
        // (we can skip it if someone else is doing it, we have synch lock so we'll get notified)
        if (retrievingLocalExternalIp.compareAndSet(false, true)) {
            new Thread(new Runnable() {
                public void run() {
                    try {
                        log.debug("Looking up external IP of this host in private thread "+Thread.currentThread());
                        localExternalIp = new ResourceUtils(HostGeoLookup.class).getResourceAsString("http://api.externalip.net/ip/").trim();
                        log.debug("Finished looking up external IP of this host in private thread, result "+localExternalIp);
                    } catch (Throwable t) {
                        log.debug("Not able to look up external IP of this host in private thread, probably offline ("+t+")");
                    } finally {
                        retrievingLocalExternalIp.set(false);
                        triedLocalExternalIp.countDown();
                    }
                }
            }).start();
        }

        try {
            // only wait 2s, so startup is fast
            triedLocalExternalIp.await(2000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            throw Exceptions.propagate(e);
        }
        if (localExternalIp==null)  
            throw Throwables.propagate(new IOException("Unable to discover external IP of local machine; response to server timed out (ongoing="+retrievingLocalExternalIp+")"));

        log.debug("Looked up external IP of this host, result is: "+localExternalIp);
        return localExternalIp;
    }
    
    public String getLookupUrlForLocalhost() {
        return getLookupUrlForPublicIp(getLocalhostExternalIp());
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
