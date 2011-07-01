package brooklyn.location.basic

import brooklyn.location.MachineLocation
import brooklyn.location.PortRange
import brooklyn.util.internal.SshJschTool

/**
 * Operations on a machine that is accessible via ssh.
 */
public class SshMachineLocation extends GeneralPurposeLocation implements MachineLocation {
    private String user = null
    private InetAddress address

    public SshMachineLocation(Map attributes = [:], InetAddress address) {
        super(attributes)
        this.address = address
    }

    public SshMachineLocation(Map attributes = [:], InetAddress address, String userName) {
        super(attributes)
        this.user = userName
        this.address = address
    }

    public InetAddress getAddress() {
        return this.address;
    }

    /** convenience for running a script, returning the result code */
    public int run(Map props=[:], String command) {
        assert address : "host must be specified for $this"

        if (!user) user=System.getProperty "user.name"
        def t = new SshJschTool(user:user, host:address.hostName);
        t.connect()
        int result = t.execShell props, command
        t.disconnect()
        result

//        ExecUtils.execBlocking "ssh", (user?user+"@":"")+host, command
    }

    public int copyTo(File src, String destination) {
        def conn = new SshJschTool(user:user, host:address.hostName)
        conn.connect()
        int result = conn.copyToServer [:], src, destination
        conn.disconnect()
        result
    }

    @Override
    public String toString() {
        return address;
    }

    boolean obtainSpecificPort(int portNumber) {
        throw new Exception("Not implemented")
    }

    int obtainPort(PortRange range) {
        throw new Exception("Not implemented")
    }

    void releasePort(int portNumber) {
        throw new Exception("Not implemented")
    }
}
