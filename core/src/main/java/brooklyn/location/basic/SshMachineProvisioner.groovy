package brooklyn.location.basic

/**
 * A provisioner of @{link SshMachine}s
 */
public class SshMachineProvisioner {

    private Object lock = new Object();
    private List<InetAddress> available;
    private List<InetAddress> inUse;

    public SshMachineProvisioner(Collection<InetAddress> machines) {
        available = new ArrayList(machines);
        inUse = new ArrayList();
    }

    public static fromStringList(Collection<String> machines) {
        return new SshMachineProvisioner(machines.collect { InetAddress.getByName() })
    }

    public SshMachine obtain() {
        SshMachine machine;
        synchronized (lock) {
            if (available.empty)
                return null;
            InetAddress host = available.pop();
            machine = new SshMachine(host);
            inUse.add(machine);
        }
        return machine;
    }

    public void release(SshMachine machine) {
        synchronized (lock) {
            inUse.remove(machine.host);
            available.add(machine.host);
        }
    }
}

public class LocalhostSshMachineProvisioner extends SshMachineProvisioner {
    public LocalhostSshMachineProvisioner() {
        super([ InetAddress.getByAddress((byte[])[127,0,0,1]) ])
    }
}