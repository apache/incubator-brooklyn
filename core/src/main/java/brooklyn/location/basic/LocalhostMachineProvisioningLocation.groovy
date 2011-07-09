package brooklyn.location.basic

import com.google.common.base.Preconditions

/**
 * An implementation of {@link MachineProvisioningLocation} that can provision a {@link SshMachineLocation} for the
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

    private static Map augmentProperties(Map properties) {
        int numberOfMachines = 1

        if (properties.count) {
            Preconditions.checkArgument properties.get('count') instanceof Integer, "count value must be an integer"
            numberOfMachines = properties.count
        }

        Collection<SshMachineLocation> machines = []
        numberOfMachines.times { machines += new SshMachineLocation(address:InetAddress.localHost) }
        properties.machines = machines

        return properties
    }
}