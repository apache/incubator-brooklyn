package brooklyn.location.basic

/**
 * A provisioner of @{link SshMachineLocation}s
 */
public class SshMachineProvisioner {

    private Object lock = new Object();
    private List<SshMachineLocation> available;
    private List<SshMachineLocation> inUse;

    public SshMachineProvisioner(Collection<SshMachineLocation> machines) {
        available = new ArrayList(machines);
        inUse = new ArrayList();
    }

    public static fromStringList(Collection<String> machines, String userName) {
        return new SshMachineProvisioner(machines.collect { new SshMachineLocation(InetAddress.getByName(), userName) })
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

public class LocalhostSshMachineProvisioner extends SshMachineProvisioner {
    public LocalhostSshMachineProvisioner() {
        super([ new SshMachineLocation(InetAddress.getByAddress((byte[])[127,0,0,1])) ])
    }
}