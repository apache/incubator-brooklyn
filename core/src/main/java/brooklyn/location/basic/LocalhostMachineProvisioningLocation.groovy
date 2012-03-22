package brooklyn.location.basic

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.config.BrooklynServiceAttributes;
import brooklyn.location.OsDetails;
import brooklyn.location.PortRange
import brooklyn.util.NetworkUtils;
import brooklyn.util.flags.SetFromFlag
import brooklyn.util.flags.TypeCoercions;

/**
 * An implementation of {@link brooklyn.location.MachineProvisioningLocation} that can provision a {@link SshMachineLocation} for the
 * local host.
 *
 * By default you can only obtain a single SshMachineLocation for the localhost. Optionally, you can "overload"
 * and choose to allow localhost to be provisioned multiple times, which may be useful in some testing scenarios.
 */
public class LocalhostMachineProvisioningLocation extends FixedListMachineProvisioningLocation<SshMachineLocation> {

    public static final Logger LOG = LoggerFactory.getLogger(LocalhostMachineProvisioningLocation.class);
                
    @SetFromFlag('count')
    int initialCount;

    @SetFromFlag
    Boolean canProvisionMore;
    
    @SetFromFlag
    InetAddress address;

    private static Set<Integer> portsInUse = []
    
    /**
     * Construct a new instance.
     *
     * The constructor recognises the following properties:
     * <ul>
     * <li>count - number of localhost machines to make available
     * </ul>
     *
     * @param properties the properties of the new instance.
     */
    public LocalhostMachineProvisioningLocation(Map properties = [:]) {
        super(properties)
    }
        
    public LocalhostMachineProvisioningLocation(String name, int count=0) {
        this([name: name, count: count]);
    }

    protected void configure(Map flags) {
        super.configure(flags)
        
        if (!name) { name="localhost" }
        if (!address) address = TypeCoercions.coerce(BrooklynServiceAttributes.LOCALHOST_IP_ADDRESS.getValue() ?: Inet4Address.localHost, InetAddress)
        // TODO should try to confirm this machine is accessible on the given address ... but there's no 
        // immediate convenience in java so early-trapping of that particular error is deferred
        
        if (canProvisionMore==null) {
            if (initialCount>0) canProvisionMore = false;
            else canProvisionMore = true;
        }
        if (initialCount > machines.size()) {
            provisionMore(initialCount - machines.size());
        }
    }
    
    public boolean canProvisionMore() { return canProvisionMore; }
    public void provisionMore(int size) {
        for (int i=0; i<size; i++) { 
            SshMachineLocation child = new LocalhostMachine(address:(address ?: InetAddress.localHost)) 
            addChildLocation(child)
            child.setParentLocation(this)
       }
    }

    public static synchronized boolean obtainSpecificPort(InetAddress localAddress, int portNumber) {
        if (portsInUse.contains(portNumber)) {
            return false
        } else {
            //see if it is available?
            if (!checkPortAvailable(localAddress, portNumber)) {
                return false;
            }
            portsInUse.add(portNumber)
            return true
        }
    }
    /** checks the actual availability of the port on localhost, ie by binding to it */
    public static boolean checkPortAvailable(InetAddress localAddress, int portNumber) {
        if (portNumber<1024) {
            if (LOG.isDebugEnabled()) LOG.debug("Skipping system availability check for privileged localhost port "+portNumber);
            return true;
        }
        return NetworkUtils.isPortAvailable(portNumber);
    }
    public static int obtainPort(InetAddress localAddress, PortRange range) {
        for (int p: range)
            if (obtainSpecificPort(localAddress, p)) return p;
        if (LOG.isDebugEnabled()) LOG.debug("unable to find port in {} on {}; returning -1", range, this)
        return -1;
    }

    public static synchronized void releasePort(InetAddress localAddress, int portNumber) {
        portsInUse.remove((Object) portNumber);
    }

    public void release(SshMachineLocation machine) {
        Set portsObtained = []
        synchronized (machine.portsObtained) {
            portsObtained.addAll(machine.portsObtained)
        }
        
        super.release(machine);
        
        for (int p: portsObtained)
            releasePort(null, p)
    }
    
    private static class LocalhostMachine extends SshMachineLocation {
        Set portsObtained = []
        
        private LocalhostMachine(Map properties) {
            super(properties)
        }
        public boolean obtainSpecificPort(int portNumber) {
            return LocalhostMachineProvisioningLocation.obtainSpecificPort(address, portNumber)
        }
        public int obtainPort(PortRange range) {
            int r = LocalhostMachineProvisioningLocation.obtainPort(address, range)
            synchronized (portsObtained) {
                if (r>0) portsObtained += r;
            }
            return r;
        }
        public void releasePort(int portNumber) {
            synchronized (portsObtained) {
                portsObtained -= portNumber;
            }
            LocalhostMachineProvisioningLocation.releasePort(address, portNumber)
        }
        
        @Override
        public OsDetails getOsDetails() {
            return new BasicOsDetails.Factory().newLocalhostInstance();
        }
    }
}