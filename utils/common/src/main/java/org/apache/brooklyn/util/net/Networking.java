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
package org.apache.brooklyn.util.net;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.text.Identifiers;
import org.apache.brooklyn.util.time.Time;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import com.google.common.base.Throwables;
import com.google.common.net.HostAndPort;
import com.google.common.primitives.UnsignedBytes;

import static com.google.common.base.Preconditions.checkArgument;

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
        
    public static boolean isPortAvailable(int port) {
        return isPortAvailable(ANY_NIC, port);
    }
    public static boolean isPortAvailable(InetAddress localAddress, int port) {
        if (port < MIN_PORT_NUMBER || port > MAX_PORT_NUMBER) {
            throw new IllegalArgumentException("Invalid start port: " + port);
        }

        Stopwatch watch = Stopwatch.createStarted();
        try {
            //despite http://stackoverflow.com/questions/434718/sockets-discover-port-availability-using-java
            //(recommending the following) it isn't 100% reliable (e.g. nginx will happily coexist with ss+ds)
            //
            //Svet - SO_REUSEADDR (enabled below) will allow one socket to listen on 0.0.0.0:X and another on
            //192.168.0.1:X which explains the above comment (nginx sets SO_REUSEADDR as well). Moreover there
            //is no TIME_WAIT for listening sockets without any connections so why enable it at all.
            ServerSocket ss = null;
            DatagramSocket ds = null;
            try {
                // Check TCP port
                ss = new ServerSocket();
                ss.setSoTimeout(250);
                ss.setReuseAddress(true);
                ss.bind(new InetSocketAddress(localAddress, port));

                // Check UDP port
                ds = new DatagramSocket(null);
                ds.setSoTimeout(250);
                ds.setReuseAddress(true);
                ds.bind(new InetSocketAddress(localAddress, port));
            } catch (IOException e) {
                if (log.isTraceEnabled()) log.trace("Failed binding to " + localAddress + " : " + port, e);
                return false;
            } finally {
                closeQuietly(ds);
                closeQuietly(ss);
            }

            if (localAddress==null || ANY_NIC.equals(localAddress)) {
                // sometimes 0.0.0.0 can be bound to even if 127.0.0.1 has the port as in use;
                // check all interfaces if 0.0.0.0 was requested
                Enumeration<NetworkInterface> nis = null;
                try {
                    nis = NetworkInterface.getNetworkInterfaces();
                } catch (SocketException e) {
                    throw Exceptions.propagate(e);
                }
                // When using a specific interface saw failures not caused by port already bound:
                //   * java.net.SocketException: No such device
                //   * java.net.BindException: Cannot assign requested address
                //   * probably many more
                // Check if the address is still valid before marking the port as not available.
                boolean foundAvailableInterface = false;
                while (nis.hasMoreElements()) {
                    NetworkInterface ni = nis.nextElement();
                    Enumeration<InetAddress> as = ni.getInetAddresses();
                    while (as.hasMoreElements()) {
                        InetAddress a = as.nextElement();
                        if (!isPortAvailable(a, port)) {
                            if (isAddressValid(a)) {
                                if (log.isTraceEnabled()) log.trace("Port {} : {} @ {} is taken and the address is valid", new Object[] {a, port, nis});
                                return false;
                            }
                        } else {
                            foundAvailableInterface = true;
                        }
                    }
                }
                if (!foundAvailableInterface) {
                    //Aborting with an error, even nextAvailablePort won't be able to find a free port.
                    throw new RuntimeException("Unable to bind on any network interface, even when letting the OS pick a port. Possible causes include file handle exhaustion, port exhaustion. Failed on request for " + localAddress + ":" + port + ".");
                }
            }

            return true;
        } finally {
            // Until timeout was added, was taking 1min5secs for /fe80:0:0:0:1cc5:1ff:fe81:a61d%8 : 8081
            // Svet - Probably caused by the now gone new Socket().connect() call, SO_TIMEOUT doesn't
            // influence bind(). Doesn't hurt having it though.
            long elapsed = watch.elapsed(TimeUnit.SECONDS);
            boolean isDelayed = (elapsed >= 1);
            boolean isDelayedByMuch = (elapsed >= 30);
            if (isDelayed || log.isTraceEnabled()) {
                String msg = "Took {} to determine if port was available for {} : {}";
                Object[] args = new Object[] {Time.makeTimeString(watch.elapsed(TimeUnit.MILLISECONDS), true), localAddress, port};
                if (isDelayedByMuch) {
                    log.warn(msg, args);
                } else if (isDelayed) {
                    log.debug(msg, args);
                } else {
                    log.trace(msg, args);
                }
            }
        }
    }

    /**
     * Bind to the specified IP, but let the OS pick a port.
     * If the operation fails we know it's not because of
     * non-available port, the interface could be down.
     * 
     * If there's port exhaustion on a single interface we won't catch it
     * and declare the port is free. Doesn't matter really because the
     * subsequent bind of the caller will fail anyway and nextAvailablePort
     * wouldn't be able to find a free one either.
     */
    private static boolean isAddressValid(InetAddress addr) {
        ServerSocket ss;
        try {
            ss = new ServerSocket();
            ss.setSoTimeout(250);
        } catch (IOException e) {
            throw Exceptions.propagate(e);
        }
        try {
            ss.bind(new InetSocketAddress(addr, 0));
            return true;
        } catch (IOException e) {
            if (log.isTraceEnabled()) log.trace("Binding on {} failed, interface could be down, being reconfigured, file handle exhaustion, port exhaustion, etc.", addr);
            return false;
        } finally {
            closeQuietly(ss);
        }
    }

    /** returns the first port available on the local machine >= the port supplied */
    public static int nextAvailablePort(int port) {
        checkArgument(port >= MIN_PORT_NUMBER && port <= MAX_PORT_NUMBER, "requested port %s is outside the valid range of %s to %s", port, MIN_PORT_NUMBER, MAX_PORT_NUMBER);
        int originalPort = port;
        while (!isPortAvailable(port) && port < MAX_PORT_NUMBER) port++;
        if (port >= MAX_PORT_NUMBER)
            throw new RuntimeException("unable to find a free port at or above " + originalPort);
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

    public static void checkPortsValid(Map<?, ?> ports) {
        for (Map.Entry<?,?> entry : ports.entrySet()) {
            Object val = entry.getValue();
            if (val == null){
                throw new IllegalArgumentException("port for "+entry.getKey()+" is null");
            } else if (!(val instanceof Integer)) {
                throw new IllegalArgumentException("port "+val+" for "+entry.getKey()+" is not an integer ("+val.getClass()+")");
            }
            checkPortValid((Integer)val, ""+entry.getKey());
        }
    }

    /**
     * Check if this is a private address, not exposed on the public Internet.
     *
     * For IPV4 addresses this is an RFC1918 subnet (site local) address ({@code 10.0.0.0/8},
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
     * although certain well-known hostnames are recognised as local-only
     * <p>
     * note however {@link InetAddress#getByName(String)} can ignore settings in /etc/hosts, on OS X at least, 
     * and give different values than the system */
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

    /** As {@link #isPrivateSubnet(InetAddress)} but taking a string; sepcifically local-only address ARE treated as private. 
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

    /** returns true if the supplied string matches any known IP (v4 or v6) for this machine,
     * or if it can be resolved to any such address */
    public static boolean isLocalhost(String remoteAddress) {
        Map<String, InetAddress> addresses = getLocalAddresses();
        if (addresses.containsKey(remoteAddress)) return true;
        
        if ("127.0.0.1".equals(remoteAddress)) return true;
        
        String modifiedIpV6Address = remoteAddress;
        // IPv6 localhost "ip" strings may vary;
        // comes back as 0:0:0:0:0:0:0:1%1 for me.
        // following deals with the cases which seem likely.
        // (svet suggests using InetAddress parsing but I -- Alex -- am not sure if that's going to have it's own bugs)
        if (modifiedIpV6Address.contains("%")) {
            // trim any description %dex
            modifiedIpV6Address = modifiedIpV6Address.substring(0, modifiedIpV6Address.indexOf("%"));
        }
        if ("0:0:0:0:0:0:0:1".equals(modifiedIpV6Address)) return true;
        if ("::1".equals(modifiedIpV6Address)) return true;
        if (addresses.containsKey(remoteAddress) || addresses.containsKey(modifiedIpV6Address))
            return true;
        
        try {
            InetAddress remote = InetAddress.getByName(remoteAddress);
            if (addresses.values().contains(remote))
                return true;
        } catch (Exception e) {
            Exceptions.propagateIfFatal(e);
            log.debug("Error resolving address "+remoteAddress+" when checking if it is local (assuming not: "+e, e);
        }
        
        return false;
    }
    
    public static boolean isReachable(HostAndPort endpoint) {
        // TODO Should we create an unconnected socket, and then use the calls below (see jclouds' InetSocketAddressConnect):
        //      socket.setReuseAddress(false);
        //      socket.setSoLinger(false, 1);
        //      socket.setSoTimeout(timeout);
        //      socket.connect(socketAddress, timeout);
        
        try {
            Socket s = new Socket(endpoint.getHostText(), endpoint.getPort());
            closeQuietly(s);
            return true;
        } catch (Exception e) {
            if (log.isTraceEnabled()) log.trace("Error reaching "+endpoint+" during reachability check (return false)", e);
            return false;
        }
    }

    public static void closeQuietly(Socket s) {
        if (s != null) {
            try {
                s.close();
            } catch (IOException e) {
                /* should not be thrown */
            }
        }
    }

    public static void closeQuietly(ServerSocket s) {
        if (s != null) {
            try {
                s.close();
            } catch (IOException e) {
                /* should not be thrown */
            }
        }
    }

    public static void closeQuietly(DatagramSocket s) {
        if (s != null) {
            s.close();
        }
    }


    // TODO go through nic's, looking for public, private, etc, on localhost

    /**
     * force use of TLSv1, fixing:
     * http://stackoverflow.com/questions/9828414/receiving-sslhandshakeexception-handshake-failure-despite-my-client-ignoring-al
     */
    public static void installTlsOnlyForHttpsForcing() {
        System.setProperty("https.protocols", "TLSv1");
    }
    public static void installTlsForHttpsIfAppropriate() {
        if (System.getProperty("https.protocols")==null && System.getProperty("brooklyn.https.protocols.leave_untouched")==null) {
            installTlsOnlyForHttpsForcing();
        }
    }
    static {
        installTlsForHttpsIfAppropriate();
    }
    
    /** does nothing, but forces the class to be loaded and do static initialization */
    public static void init() {}
    
}
