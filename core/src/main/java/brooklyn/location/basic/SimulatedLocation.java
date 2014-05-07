package brooklyn.location.basic;

import java.net.InetAddress;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

import brooklyn.location.HardwareDetails;
import brooklyn.location.LocationSpec;
import brooklyn.location.MachineDetails;
import brooklyn.location.MachineLocation;
import brooklyn.location.MachineProvisioningLocation;
import brooklyn.location.OsDetails;
import brooklyn.location.PortRange;
import brooklyn.location.PortSupplier;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.net.Networking;

import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;


/** Location for use in dev/test, defining custom start/stop support, and/or tweaking the ports which are permitted to be available
 * (using setPermittedPorts(Iterable))
 * 
 * @deprecated since 0.7.0; will be moved to src/test/java
 */
public class SimulatedLocation extends AbstractLocation implements MachineProvisioningLocation<MachineLocation>, MachineLocation, PortSupplier {

    private static final long serialVersionUID = 1L;
    
    private static final InetAddress address;
    static {
        address = Networking.getLocalHost();
    }

    Iterable<Integer> permittedPorts = PortRanges.fromString("1+");
    Set<Integer> usedPorts = Sets.newLinkedHashSet();

    public SimulatedLocation() {
        this(MutableMap.<String,Object>of());
    }
    public SimulatedLocation(Map<String,? extends Object> flags) {
        super(flags);
    }
    
    @Override
    public SimulatedLocation newSubLocation(Map<?,?> newFlags) {
        // TODO shouldn't have to copy config bag as it should be inherited (but currently it is not used inherited everywhere; just most places)
        return getManagementContext().getLocationManager().createLocation(LocationSpec.create(getClass())
                .parent(this)
                .configure(getLocalConfigBag().getAllConfig())  // FIXME Should this just be inherited?
                .configure(newFlags));
    }

    public MachineLocation obtain(Map<?,?> flags) {
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
        return getMachineDetails().getOsDetails();
    }

    @Override
    public MachineDetails getMachineDetails() {
        HardwareDetails hardwareDetails = new BasicHardwareDetails(null, null);
        OsDetails osDetails = BasicOsDetails.Factory.ANONYMOUS_LINUX;
        return new BasicMachineDetails(hardwareDetails, osDetails);
    }
}
