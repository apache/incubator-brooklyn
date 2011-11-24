package brooklyn.location.basic

import com.google.common.base.Preconditions

/**
 * An implementation of {@link brooklyn.location.MachineProvisioningLocation} that can provision a {@link SshMachineLocation} for the
 * local host.
 *
 * By default you can only obtain a single SshMachineLocation for the localhost. Optionally, you can "overload"
 * and choose to allow localhost to be provisioned multiple times, which may be useful in some testing scenarios.
 */
public class LocalhostMachineProvisioningLocation extends FixedListMachineProvisioningLocation<SshMachineLocation> {
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
        super(augmentProperties(properties))
    }

    public LocalhostMachineProvisioningLocation(String name, int count) {
        this([name: name, count: count]);
    }

    private static Map augmentProperties(Map props) {
        String address = props.address
        int numberOfMachines = 1
        
        if (props.count) {
            Preconditions.checkArgument props.count instanceof Integer, "count value must be an integer"
            numberOfMachines = props.count
        }

        Collection<SshMachineLocation> machines = []
        numberOfMachines.times { machines += new SshMachineLocation(address:(address ?: InetAddress.localHost)) }
        props.machines = machines

        return props
    }
}