package brooklyn.location.basic

/**
 * A provisioner of @{link SshMachine}s
 */
public class SshMachineProvisioner {

    private Object lock = new Object();
    private List<InetAddress> available;
    private List<InetAddress> inUse;

    public SshMachineProvisioner(List<InetAddress> machines) {
        available = new ArrayList(machines);
        inUse = new ArrayList();
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
