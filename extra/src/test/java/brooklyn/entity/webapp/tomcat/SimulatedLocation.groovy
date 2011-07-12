package brooklyn.entity.webapp.tomcat

import java.util.Collection
import java.util.Map

import brooklyn.location.MachineLocation
import brooklyn.location.MachineProvisioningLocation
import brooklyn.location.PortRange
import brooklyn.location.basic.AbstractLocation

/**
 */
class SimulatedLocation extends AbstractLocation implements MachineProvisioningLocation<MachineLocation>, MachineLocation {

    private static final long serialVersionUID = 1L;
    
    private static final address = InetAddress.getLocalHost()

    public MachineLocation obtain(Map<String,? extends Object> flags) {
        return this
    }

    public void release(MachineLocation machine) {
    }

    public Map<String,Object> getProvisioningFlags(Collection<String> tags) {
        return [:]
    }
    
    public InetAddress getAddress() {
        return address;
    }

    public boolean obtainSpecificPort(int portNumber) {
        return false;
    }

    public int obtainPort(PortRange range) {
        return -1;
    }

    public void releasePort(int portNumber) {
        
    }
}
