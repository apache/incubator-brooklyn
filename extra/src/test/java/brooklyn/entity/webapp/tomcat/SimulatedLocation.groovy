package brooklyn.entity.webapp.tomcat

import brooklyn.location.MachineProvisioningLocation
import brooklyn.location.MachineLocation
import brooklyn.location.PortRange
import brooklyn.location.basic.AbstractLocation

/**
 * Created by IntelliJ IDEA.
 * User: richard
 * Date: 17/06/2011
 * Time: 10:22
 * To change this template use File | Settings | File Templates.
 */
class SimulatedLocation extends AbstractLocation implements MachineProvisioningLocation, MachineLocation {

    private static final address = InetAddress.getLocalHost()

    // brooklyn.location.MachineProvisioningLocation interace

    MachineLocation obtain() {
        return this
    }

    void release(MachineLocation machine) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    // brooklyn.location.MachineLocation interface

    InetAddress getAddress() {
        return address;
    }

    boolean obtainSpecificPort(int portNumber) {
        return false;
    }

    int obtainPort(PortRange range) {
        return -1;
    }

    void releasePort(int portNumber) {
        
    }

    private static final long serialVersionUID = 1L;
}
