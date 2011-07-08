package brooklyn.location.basic

import brooklyn.location.MachineLocation
import brooklyn.location.PortRange
import brooklyn.util.internal.SshJschTool
import com.google.common.base.Preconditions

/**
 * Operations on a machine that is accessible via ssh.
 */
public class SshMachineLocation extends AbstractLocation implements MachineLocation {
    private String user = null
    private InetAddress address
    private Map sshConfig = [:]

    private final Set<Integer> portsInUse = [] as HashSet
    
    public SshMachineLocation(Map properties = [:]) {
        super(properties)
        Map mutableProperties = properties as LinkedHashMap
        Preconditions.checkArgument mutableProperties.containsKey('address'), "properties must contain an entry with key 'address'"
        Preconditions.checkArgument mutableProperties.address instanceof InetAddress, "'address' value must be an InetAddress"
        this.address = mutableProperties.remove('address')

        if (mutableProperties.userName) {
            Preconditions.checkArgument mutableProperties.userName instanceof String, "'userName' value must be a string"
            this.user = mutableProperties.remove('userName')
        }

        if (properties.sshConfig) {
            Preconditions.checkArgument properties.sshConfig instanceof Map, "'sshConfig' value must be a Map"
            this.sshConfig = properties.remove('sshConfig')
        }
    }

    public InetAddress getAddress() { return address }
 
    public int run(Map props=[:], String command, Map env=[:]) {
        run(props, [ command ], env)
    }

    /** convenience for running a script, returning the result code */
    public int run(Map props=[:], List<String> commands, Map env=[:]) {
        assert address : "host must be specified for $this"

        if (!user) user=System.getProperty "user.name"
        def sshArgs = [user:user, host:address.hostName]
        sshArgs << sshConfig
        def t = new SshJschTool(sshArgs);
        t.connect()
        int result = t.execShell props, commands, env
        t.disconnect()
        result
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

    // TODO Does not support zero to mean any; but can't when returning boolean
    // TODO Does not yet check if the port really is free on this machine
    boolean obtainSpecificPort(int portNumber) {
        if (portsInUse.contains(portNumber)) {
            return false
        } else {
            portsInUse.add(portNumber)
            return true
        }
    }

    int obtainPort(PortRange range) {
        for (int i = range.getMin(); i <= range.getMax(); i++) {
            if (obtainSpecificPort(i)) return i;
        }
        return -1;
    }

    void releasePort(int portNumber) {
        portsInUse.remove((Object)portNumber);
    }
}
