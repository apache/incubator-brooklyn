package brooklyn.location.basic

/**
 * An implementation of @{link MachineProvisioningLocation} that can provision a single @{link SshMachineLocation> for the
 * local host.
 */
public class LocalhostMachineProvisioningLocation extends FixedListMachineProvisioningLocation<SshMachineLocation> {
    public LocalhostMachineProvisioningLocation(String name = null) {
        super([ new SshMachineLocation(InetAddress.getByAddress((byte[])[127,0,0,1])) ], name)
    }
}