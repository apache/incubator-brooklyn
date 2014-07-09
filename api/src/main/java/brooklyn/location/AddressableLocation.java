package brooklyn.location;

import java.net.InetAddress;

/** A location that has an IP address.
 * <p>
 * This IP address may be a machine (usually the MachineLocation sub-interface), 
 * or often an entry point for a service.
 */
public interface AddressableLocation extends Location {

    /**
     * Return the single most appropriate address for this location.
     * (An implementation or sub-interface definition may supply more information
     * on the precise semantics of the address.)
     * 
     * Should not return null, but in some "special cases" (e.g. CloudFoundryLocation it
     * may return null if the location is not configured correctly). Users should expect
     * a non-null result and treat null as a programming error or misconfiguration. 
     * Implementors of this interface should strive to not return null (and then we'll
     * remove this caveat from the javadoc!).
     */
    InetAddress getAddress();

}
