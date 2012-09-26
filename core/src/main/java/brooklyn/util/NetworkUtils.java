package brooklyn.util;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.util.text.Identifiers;

public class NetworkUtils {

    private static final Logger log = LoggerFactory.getLogger(NetworkUtils.class);
    
    public static final int MIN_PORT_NUMBER = 1;
    public static final int MAX_PORT_NUMBER = 65535;
    
    public static boolean isPortAvailable(int port) {
        if (port < MIN_PORT_NUMBER || port > MAX_PORT_NUMBER) {
            throw new IllegalArgumentException("Invalid start port: " + port);
        }
        try {
            Socket s = new Socket(InetAddress.getByName("localhost"), port);
            try {
                s.close();
            } catch (Exception e) {}
            return false;
        } catch (Exception e) {
            //expected - shouldn't be able to connect
        }
        //despite http://stackoverflow.com/questions/434718/sockets-discover-port-availability-using-java
        //(recommending the following) it isn't 100% reliable (e.g. nginx will happily coexist with ss+ds)
        //so we also do the above check
        ServerSocket ss = null;
        DatagramSocket ds = null;
        try {
            ss = new ServerSocket(port);
            ss.setReuseAddress(true);
            ds = new DatagramSocket(port);
            ds.setReuseAddress(true);
            return true;
        } catch (IOException e) {
            return false;
        } finally {
            if (ds != null) {
                ds.close();
            }

            if (ss != null) {
                try {
                    ss.close();
                } catch (IOException e) {
                    /* should not be thrown */
                }
            }
        }
    }

    public static boolean isPortValid(Integer port) {
        return (port!=null && port>=NetworkUtils.MIN_PORT_NUMBER && port<=NetworkUtils.MAX_PORT_NUMBER);
    }
    public static int checkPortValid(Integer port, String errorMessage) {
        if (!isPortValid(port)) {
            throw new IllegalArgumentException("Invalid port value "+port+": "+errorMessage);
        }
        return port;
    }

    public static void checkPortsValid(Map ports) {
        for (Object ppo : ports.entrySet()) {
            Map.Entry<?,?> pp = (Map.Entry<?,?>)ppo;
            Object val = pp.getValue();
            if(val == null){
                throw new IllegalArgumentException("port for "+pp.getKey()+" is null");
            }else if (!(val instanceof Integer)) {
                throw new IllegalArgumentException("port "+val+" for "+pp.getKey()+" is not an integer ("+val.getClass()+")");
            }
            checkPortValid((Integer)val, ""+pp.getKey());
        }
    }

    /** return true if the IP (v4 only currently) address indicates a private subnet address, 
     * not exposed on the public internet */
    public static boolean isPrivateSubnet(InetAddress address) {
//      127.0.0.1/0
//      10.0.0.0/8
//      172.16.0.0/12
//      192.168.0.0/16
        byte[] bytes = address.getAddress();
        if (bytes[0]==10) return true;
        if (((bytes[0] & 0xFF) == 172) && (bytes[1] & 240)==16) return true;
        if (((bytes[0] & 0xFF) == 192) && ((bytes[1] & 0xFF) == 168)) return true;
        
        if ((bytes[0] & 0xFF) == 169) return true;
        if (bytes[0]==127 && bytes[1]==0 && bytes[2]==0 && bytes[3]==1) return true;
        
        return false;
    }

    private static boolean triedUnresolvableHostname = false;
    private static String cachedAddressOfUnresolvableHostname = null;
    
    /** returns null in a sane DNS environment, but if DNS provides a bogus address for made-up hostnames, this returns that address */
    public synchronized static String getAddressOfUnresolvableHostname() {
        if (triedUnresolvableHostname) return cachedAddressOfUnresolvableHostname;
        String h = "noexistent-machine-"+Identifiers.makeRandomBase64Id(8);
        try {
            cachedAddressOfUnresolvableHostname = InetAddress.getByName(h).getHostAddress();
            log.info("NetworkUtils detected "+cachedAddressOfUnresolvableHostname+" being returned by DNS for bogus hostnames ("+h+")");
        } catch (Exception e) {
            log.debug("NetworkUtils detected failure on DNS resolution of unknown hostname ("+h+" throws "+e+")");
            cachedAddressOfUnresolvableHostname = null;
        }
        triedUnresolvableHostname = true;
        return cachedAddressOfUnresolvableHostname;
    }
    
    /** resolves the given hostname to an IP address, returning null if unresolvable or 
     * if the resolution is bogus (eg 169.* subnet or a "catch-all" IP resolution supplied by some miscreant DNS services) */
    public static InetAddress resolve(String hostname) {
        try {
            InetAddress a = InetAddress.getByName(hostname);
            if (a==null) return null;
            String ha = a.getHostAddress();
            if (log.isDebugEnabled()) log.debug("NetworkUtils resolved "+hostname+" as "+a);
            if (ha.equals(getAddressOfUnresolvableHostname())) return null;
            if (ha.startsWith("169.")) return null;
            return a;
        } catch (Exception e) {
            if (log.isDebugEnabled()) log.debug("NetworkUtils failed to resolve "+hostname+", threw "+e);
            return null;
        }
    }
    
}
