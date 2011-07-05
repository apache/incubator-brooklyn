package brooklyn.location.basic

/**
 * An implementation of @{link MachineProvisioningLocation} that can provision a single @{link SshMachineLocation> for the
 * local host.
 */
public class LocalhostMachineProvisioningLocation extends FixedListMachineProvisioningLocation<SshMachineLocation> {
    public LocalhostMachineProvisioningLocation(Map properties = [:]) {
        super(augmentProperties(properties))
    }

    private static Map augmentProperties(Map properties) {
        properties.machines = [ new SshMachineLocation(address: InetAddress.getByAddress((byte[])[127,0,0,1])) ]
        return properties
    }
}