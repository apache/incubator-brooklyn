package brooklyn.location;

import java.net.InetAddress;

/**
 * A location that is a machine.
 *
 * This interface marks a {@link Location} being a network node with an IP address, and supports appropriate operations on the node.
 */
public interface MachineLocation extends Location {
    /**
     * Get the network address of the machine.
     *
     * @return the machine's network address.
     */
    InetAddress getAddress();
    
    /**
     * Reserve a specific port for an application. If your application requires a specific port - for example, port 80 for a web
     * server - you should reserve this port before starting your application. Using this method, you will be able to detect if
     * another application has already claimed this port number.
     *
     * @param portNumber the required port number.
     * @return <code>true</code> if the port was successfully reserved; <code>false</code> if it has been previously reserved.
     */
    boolean obtainSpecificPort(int portNumber);

    /**
     * Reserve a port for your application, with a port number in a specific range. If your application requires a port, but it does
     * not mind exactly which port number - for example, a port for internal JMX monitoring - call this method.
     *
     * @param range the range of acceptable port numbers.
     * @return the port number that has been reserved, or -1 if there was no available port in the acceptable range.
     */
    int obtainPort(PortRange range);

    /**
     * Release a previously reserved port.
     *
     * @param portNumber the port number from a call to {@link #obtainPort(PortRange)} or {@link #obtainSpecificPort(int)}
     */
    void releasePort(int portNumber);

    
    /**
     * Extends the basic interface to provide lat-long coordinates where available.
     */
    public interface WithCoordinates extends MachineLocation {
        Double getLatitude();
        Double getLongitude();
    }
        
}
