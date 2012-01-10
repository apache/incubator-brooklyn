package brooklyn.location.basic

import brooklyn.util.flags.SetFromFlag

import com.google.common.base.Preconditions

/**
 * An implementation of {@link brooklyn.location.MachineProvisioningLocation} that can provision a {@link SshMachineLocation} for the
 * local host.
 *
 * By default you can only obtain a single SshMachineLocation for the localhost. Optionally, you can "overload"
 * and choose to allow localhost to be provisioned multiple times, which may be useful in some testing scenarios.
 */
public class LocalhostMachineProvisioningLocation extends FixedListMachineProvisioningLocation<SshMachineLocation> {
    
    @SetFromFlag('count')
    int initialCount;

    @SetFromFlag
    Boolean canProvisionMore;
    
    @SetFromFlag
    InetAddress address;

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
        if (!address) address = Inet4Address.localHost;
        
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
            SshMachineLocation child = new SshMachineLocation(address:(address ?: InetAddress.localHost)) 
            addChildLocation(child)
            child.setParentLocation(this)
       }
    }
        
    
}