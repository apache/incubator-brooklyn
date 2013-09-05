package brooklyn.util.net;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.util.text.Identifiers;

import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.primitives.UnsignedBytes;

public class Networking {

    private static final Logger log = LoggerFactory.getLogger(Networking.class);
    
    public static final int MIN_PORT_NUMBER = 1;
    public static final int MAX_PORT_NUMBER = 65535;

    // based on http://stackoverflow.com/questions/106179/regular-expression-to-match-hostname-or-ip-address
    // but updated to allow leading zeroes
    public static final String VALID_IP_ADDRESS_REGEX = "^((0*[0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])\\.){3}(0*[0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])$";
    
    public static final Pattern VALID_IP_ADDRESS_PATTERN;
    static {
        VALID_IP_ADDRESS_PATTERN = Pattern.compile(VALID_IP_ADDRESS_REGEX);
    }

    public static final List<Cidr> PRIVATE_NETWORKS = Cidr.PRIVATE_NETWORKS_RFC_1918;
    
    public static InetAddress ANY_NIC = getInetAddressWithFixedName(0, 0, 0, 0);
    public static InetAddress LOOPBACK = getInetAddressWithFixedName(127, 0, 0, 1);
        
    private static boolean loggedLocalhostNotAvailable = false;
    public static boolean isPortAvailable(int port) {
        try {
            return isPortAvailable(InetAddress.getByName("localhost"), port);
        } catch (UnknownHostException e) {
            if (!loggedLocalhostNotAvailable) {
                loggedLocalhostNotAvailable = true;
                log.warn("localhost unavailable during port availability check for "+port+": "+e+"; ignoring, but this may be a sign of network misconfiguration");
            }
            return isPortAvailable(null, port);
        }
    }
    public static boolean isPortAvailable(InetAddress localAddress, int port) {
        if (port < MIN_PORT_NUMBER || port > MAX_PORT_NUMBER) {
            throw new IllegalArgumentException("Invalid start port: " + port);
        }
        try {
            Socket s = new Socket(localAddress, port);
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
    /** returns the first port available on the local machine >= the port supplied */
    public static int nextAvailablePort(int port) {
        while (!isPortAvailable(port)) port++;
        return port;
    }

    public static boolean isPortValid(Integer port) {
        return (port!=null && port>=Networking.MIN_PORT_NUMBER && port<=Networking.MAX_PORT_NUMBER);
    }
    public static int checkPortValid(Integer port, String errorMessage) {
        if (!isPortValid(port)) {
            throw new IllegalArgumentException("Invalid port value "+port+": "+errorMessage);
        }
        return port;
    }

    public static void checkPortsValid(@SuppressWarnings("rawtypes") Map ports) {
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

    /**
     * Check if this is a private address, not exposed on the public Internet.
     *
     * For IPV4 addresses this is an RFC1918 subnet (site local) address ({code 10.0.0.0/8},
     * {@code 172.16.0.0/12} and {@code 192.168.0.0/16}), a link-local address
     * ({@code 169.254.0.0/16}) or a loopback address ({@code 127.0.0.1/0}).
     * <p>
     * For IPV6 addresses this is the RFC3514 link local block ({@code fe80::/10})
     * and site local block ({@code feco::/10}) or the loopback block
     * ({@code ::1/128}).
     *
     * @return true if the address is private
     */
    public static boolean isPrivateSubnet(InetAddress address) {
        return address.isSiteLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress();
    }

    /** Check whether this address is definitely not going to be usable on any other machine;
     * i.e. if it is a loopback address or a link-local (169.254)
     */
    public static boolean isLocalOnly(InetAddress address) {
        return address.isLoopbackAddress() || address.isLinkLocalAddress();
    }
    
    /** As {@link #isLocalOnly(InetAddress)} but taking a string; 
     * does not require the string to be resolvable, and generally treats non-resolvable hostnames as NOT local-only
     * (although they are treated as private by {@link #isPrivateSubnet(String)}),
     * although certain well-known hostnames are recognised as local-only */
    public static boolean isLocalOnly(String hostnameOrIp) {
        Preconditions.checkNotNull(hostnameOrIp, "hostnameOrIp");
        if ("127.0.0.1".equals(hostnameOrIp)) return true;
        if ("localhost".equals(hostnameOrIp)) return true;
        if ("localhost.localdomain".equals(hostnameOrIp)) return true;
        try {
            InetAddress ia = getInetAddressWithFixedName(hostnameOrIp);
            return isLocalOnly(ia);
        } catch (Exception e) {
            log.debug("Networking cannot resolve "+hostnameOrIp+": assuming it is not a local-only address, but it is a private address");
            return false;
        }
    }

    /** As {@link #isPrivateSubnet(InetAddress) but taking a string; sepcifically local-only address ARE treated as private. 
     * does not require the string to be resolvable, and things which aren't resolvable are treated as private 
     * unless they are known to be local-only */
    public static boolean isPrivateSubnet(String hostnameOrIp) {
        Preconditions.checkNotNull(hostnameOrIp, "hostnameOrIp");
        try {
            InetAddress ia = getInetAddressWithFixedName(hostnameOrIp);
            return isPrivateSubnet(ia);
        } catch (Exception e) {
            log.debug("Networking cannot resolve "+hostnameOrIp+": assuming it IS a private address");
            return true;
        }
    }

    private static boolean triedUnresolvableHostname = false;
    private static String cachedAddressOfUnresolvableHostname = null;
    
    /** returns null in a sane DNS environment, but if DNS provides a bogus address for made-up hostnames, this returns that address */
    public synchronized static String getAddressOfUnresolvableHostname() {
        if (triedUnresolvableHostname) return cachedAddressOfUnresolvableHostname;
        String h = "noexistent-machine-"+Identifiers.makeRandomBase64Id(8);
        try {
            cachedAddressOfUnresolvableHostname = InetAddress.getByName(h).getHostAddress();
            log.info("Networking detected "+cachedAddressOfUnresolvableHostname+" being returned by DNS for bogus hostnames ("+h+")");
        } catch (Exception e) {
            log.debug("Networking detected failure on DNS resolution of unknown hostname ("+h+" throws "+e+")");
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
            if (log.isDebugEnabled()) log.debug("Networking resolved "+hostname+" as "+a);
            if (ha.equals(getAddressOfUnresolvableHostname())) return null;
            if (ha.startsWith("169.")) return null;
            return a;
        } catch (Exception e) {
            if (log.isDebugEnabled()) log.debug("Networking failed to resolve "+hostname+", threw "+e);
            return null;
        }
    }
    
    /**
     * Gets an InetAddress using the given IP, and using that IP as the hostname (i.e. avoids any hostname resolution).
     * <p>
     * This is very useful if using the InetAddress for updating config files on remote machines, because then it will
     * not be pickup a hostname from the local /etc/hosts file, which might not be known on the remote machine.
     */
    public static InetAddress getInetAddressWithFixedName(byte[] ip) {
        try {
            StringBuilder name = new StringBuilder();
            for (byte part : ip) {
                if (name.length() > 0) name.append(".");
                name.append(part);
            }
            return InetAddress.getByAddress(name.toString(), ip);
        } catch (UnknownHostException e) {
            throw Throwables.propagate(e);
        }
    }
    
    public static InetAddress getInetAddressWithFixedName(int ip1, int ip2, int ip3, int ip4) {
        return getInetAddressWithFixedName(asByteArray(ip1, ip2, ip3, ip4));
    }

    public static InetAddress getInetAddressWithFixedName(int ip1, int ip2, int ip3, int ip4, int ip5, int ip6) {
        return getInetAddressWithFixedName(asByteArray(ip1, ip2, ip3, ip4, ip5, ip6));
    }
    
    /** creates a byte array given a var-arg number of (or bytes or longs);
     * checks that all values are valid as _unsigned_ bytes (i.e. in [0,255] ) */
    public static byte[] asByteArray(long ...bytes) {
        byte[] result = new byte[bytes.length];
        for (int i=0; i<bytes.length; i++)
            result[i] = UnsignedBytes.checkedCast(bytes[i]);
        return result;
    }
    
    /** checks whether given string matches a valid numeric IP (v4) address, e.g. 127.0.0.1, 
     * but not localhost or 1.2.3.256 */
    public static boolean isValidIp4(String input) {
        return VALID_IP_ADDRESS_PATTERN.matcher(input).matches();
    }
    
    /**
     * Gets an InetAddress using the given hostname or IP. If it is an IPv4 address, then this is equivalent
     * to {@link getInetAddressWithFixedName(byte[])}. If it is a hostname, then this hostname will be used
     * in the returned InetAddress.
     */
    public static InetAddress getInetAddressWithFixedName(String hostnameOrIp) {
        try {
            if (isValidIp4(hostnameOrIp)) {
                byte[] ip = new byte[4];
                String[] parts = hostnameOrIp.split("\\.");
                assert parts.length == 4 : "val="+hostnameOrIp+"; split="+Arrays.toString(parts)+"; length="+parts.length;
                for (int i = 0; i < parts.length; i++) {
                    ip[i] = (byte)Integer.parseInt(parts[i]);
                }
                return InetAddress.getByAddress(hostnameOrIp, ip);
            } else {
                return InetAddress.getByName(hostnameOrIp);
            }
        } catch (UnknownHostException e) {
            throw Throwables.propagate(e);
        }
    }

    /** returns local IP address, or 127.0.0.1 if it cannot be parsed */
    public static InetAddress getLocalHost() {
        try {
            return InetAddress.getLocalHost();
        } catch (UnknownHostException e) {
            InetAddress result = null;
            result = getInetAddressWithFixedName("127.0.0.1");
            log.warn("Localhost is not resolvable; using "+result);
            return result;
        }
    }
    
    /** returns all local addresses */
    public static Map<String,InetAddress> getLocalAddresses() {
        Map<String, InetAddress> result = new LinkedHashMap<String, InetAddress>();
        Enumeration<NetworkInterface> ne;
        try {
            ne = NetworkInterface.getNetworkInterfaces();
        } catch (SocketException e) {
            log.warn("Local network interfaces are not resolvable: "+e);
            ne = null;
        }
        while (ne != null && ne.hasMoreElements()) {
            NetworkInterface nic = ne.nextElement();
            Enumeration<InetAddress> inets = nic.getInetAddresses();
            while (inets.hasMoreElements()) {
                InetAddress inet = inets.nextElement();
                result.put(inet.getHostAddress(), inet);
            }
        }
        if (result.isEmpty()) {
            log.warn("No local network addresses found; assuming 127.0.0.1");
            InetAddress loop = Cidr.LOOPBACK.addressAtOffset(0);
            result.put(loop.getHostAddress(), loop);
        }
        return result;
    }

    /** returns a CIDR object for the given string, e.g. "10.0.0.0/8" */
    public static Cidr cidr(String cidr) {
        return new Cidr(cidr);
    }

    /** returns any well-known private network (e.g. 10.0.0.0/8 or 192.168.0.0/16) 
     * which the given IP is in, or the /32 of local address if none */
    public static Cidr getPrivateNetwork(String ip) {
        Cidr me = new Cidr(ip+"/32");
        for (Cidr c: PRIVATE_NETWORKS)
            if (c.contains(me)) 
                return c;
        return me;
    }

    public static Cidr getPrivateNetwork(InetAddress address) {
        return getPrivateNetwork(address.getHostAddress());
    }
    
    /** returns whether the IP is _not_ in any private subnet */
    public static boolean isPublicIp(String ipAddress) {
        Cidr me = new Cidr(ipAddress+"/32");
        for (Cidr c: Cidr.NON_PUBLIC_CIDRS)
            if (c.contains(me)) return false;
        return true;
    }
    
    // TODO go through nic's, looking for public, private, etc, on localhost

}
