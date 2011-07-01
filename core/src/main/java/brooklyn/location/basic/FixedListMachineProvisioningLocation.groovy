package brooklyn.location.basic

import brooklyn.location.MachineProvisioningLocation

/**
 * A provisioner of @{link SshMachineLocation}s
 */
public class FixedListMachineProvisioningLocation extends GeneralPurposeLocation implements MachineProvisioningLocation<SshMachineLocation> {

    private Object lock = new Object();
    private List<SshMachineLocation> available;
    private List<SshMachineLocation> inUse;

    public FixedListMachineProvisioningLocation(Map attributes = [:], Collection<SshMachineLocation> machines) {
        super(attributes)
        available = new ArrayList(machines);
        inUse = new ArrayList();
    }

    public static fromStringList(Map attributes = [:], Collection<String> machines, String userName) {
        return new FixedListMachineProvisioningLocation(attributes, machines.collect { new SshMachineLocation(InetAddress.getByName(), userName) })
    }

    public SshMachineLocation obtain() {
        SshMachineLocation machine;
        synchronized (lock) {
            if (available.empty)
                return null;
            machine = available.pop();
            inUse.add(machine);
        }
        return machine;
    }

    public void release(SshMachineLocation machine) {
        synchronized (lock) {
            inUse.remove(machine);
            available.add(machine);
        }
    }
}

public class LocalhostMachineProvisioningLocation extends FixedListMachineProvisioningLocation {
    public LocalhostMachineProvisioningLocation() {
        super([ new SshMachineLocation(InetAddress.getByAddress((byte[])[127,0,0,1])) ])
    }
}