package brooklyn.location;

import java.net.InetAddress;

/** A location that has an IP address.
 * <p>
 * This IP address may be a machine (usually the MachineLocation sub-interface), 
 * or often an entry point for a service.
 */
public interface AddressableLocation {

    /**
     * Return the single most appropriate address for this location.
     * (An implementation or sub-interface definition may supply more information
     * on the precise semantics of the address.)  
     */
    InetAddress getAddress();

}
