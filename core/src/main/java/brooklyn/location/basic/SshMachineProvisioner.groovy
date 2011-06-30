package brooklyn.location.basic

/**
 * A provisioner of @{link SshMachine}s
 */
public class SshMachineProvisioner {

    private Object lock = new Object();
    private List<SshMachine> available;
    private List<SshMachine> inUse;

    public SshMachineProvisioner(Collection<SshMachine> machines) {
        available = new ArrayList(machines);
        inUse = new ArrayList();
    }

    public static fromStringList(Collection<String> machines, String userName) {
        return new SshMachineProvisioner(machines.collect { new SshMachine(InetAddress.getByName(), userName) })
    }

    public SshMachine obtain() {
        SshMachine machine;
        synchronized (lock) {
            if (available.empty)
                return null;
            machine = available.pop();
            inUse.add(machine);
        }
        return machine;
    }

    public void release(SshMachine machine) {
        synchronized (lock) {
            inUse.remove(machine);
            available.add(machine);
        }
    }
}

public class LocalhostSshMachineProvisioner extends SshMachineProvisioner {
    public LocalhostSshMachineProvisioner() {
        super([ new SshMachine(InetAddress.getByAddress((byte[])[127,0,0,1])) ])
    }
}