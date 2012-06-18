package brooklyn.location.basic;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

import brooklyn.location.MachineLocation;
import brooklyn.location.MachineProvisioningLocation;
import brooklyn.location.OsDetails;
import brooklyn.location.PortRange;
import brooklyn.location.PortSupplier;
import brooklyn.util.MutableMap;

import com.google.common.base.Throwables;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;


/** Location for use in dev/test, defining custom start/stop support, and/or tweaking the ports which are permitted to be available
 * (using setPermittedPorts(Iterable))
 */
public class SimulatedLocation extends AbstractLocation implements MachineProvisioningLocation<MachineLocation>, MachineLocation, PortSupplier {

    private static final long serialVersionUID = 1L;
    
    private static final InetAddress address;
    static {
        try {
            address = InetAddress.getLocalHost();
        } catch (UnknownHostException e) {
            throw Throwables.propagate(e);
        }
    }

    Iterable permittedPorts = PortRanges.fromString("1+");
    Set usedPorts = Sets.newLinkedHashSet();

    public SimulatedLocation() {
        this(MutableMap.<String,Object>of());
    }
    public SimulatedLocation(Map<String,? extends Object> flags) {
        super(flags);
    }
    
    public MachineLocation obtain(Map<String,? extends Object> flags) {
        return this;
    }

    public void release(MachineLocation machine) {
    }

    public Map<String,Object> getProvisioningFlags(Collection<String> tags) {
        return MutableMap.<String,Object>of();
    }
    
    public InetAddress getAddress() {
        return address;
    }

    public synchronized boolean obtainSpecificPort(int portNumber) {
        if (!Iterables.contains(permittedPorts, portNumber)) return false;
        if (usedPorts.contains(portNumber)) return false;
        usedPorts.add(portNumber);
        return true;
    }

    public synchronized int obtainPort(PortRange range) {
        for (int p: range)
            if (obtainSpecificPort(p)) return p;
        return -1;
    }

    public synchronized void releasePort(int portNumber) {
        usedPorts.remove(portNumber);
    }
    
    public synchronized void setPermittedPorts(Iterable<Integer> ports) {
        permittedPorts  = ports;
    }

    @Override
    public OsDetails getOsDetails() {
        return BasicOsDetails.Factory.ANONYMOUS_LINUX;
    }

}
