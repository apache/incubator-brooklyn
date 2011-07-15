package brooklyn.location.basic

import com.google.common.base.Preconditions

import java.io.IOException
import java.util.List

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
    private Map config = [:]

    private final Set<Integer> ports = [] as HashSet

    public SshMachineLocation(Map properties = [:]) {
        super(properties)

        Preconditions.checkArgument properties.containsKey('address'), "properties must contain an entry with key 'address'"
        Preconditions.checkArgument properties.address instanceof InetAddress, "'address' value must be an InetAddress"
        this.address = properties.address

        if (properties.userName) {
            Preconditions.checkArgument properties.userName instanceof String, "'userName' value must be a string"
            this.user = properties.userName
        }

        if (properties.config) {
            Preconditions.checkArgument properties.config instanceof Map, "'config' value must be a Map"
            this.config = properties.config
        }
    }

    public InetAddress getAddress() { return address }
 
    public int run(Map props=[:], String command, Map env=[:]) {
        run(props, [ command ], env)
    }

    /** convenience for running a script, returning the result code */
    public int run(Map props=[:], List<String> commands, Map env=[:]) {
        Preconditions.checkNotNull address, "host address must be specified for ssh"

        if (!user) user = System.getProperty "user.name"
        Map args = [ user:user, host:address.hostName ]
        args << config
        SshJschTool ssh = new SshJschTool(args);
        ssh.connect()
        int result = ssh.execShell props, commands, env
        ssh.disconnect()
        result
    }

    public int copyTo(Map props=[:], File src, String destination) {
        Preconditions.checkNotNull address, "host address must be specified for scp"
        Preconditions.checkArgument src.exists(), "File {} must exist for scp", src.name

        if (!user) user=System.getProperty "user.name"
        Map args = [user:user, host:address.hostName]
        args << config
        SshJschTool ssh = new SshJschTool(args)
        ssh.connect()
        int result = ssh.copyToServer props, src, destination
        ssh.disconnect()
        result
    }

    @Override
    public String toString() {
        return address;
    }

    // TODO Does not support zero to mean any; but can't when returning boolean
    // TODO Does not yet check if the port really is free on this machine
    public boolean obtainSpecificPort(int portNumber) {
        if (ports.contains(portNumber)) {
            return false
        } else {
            ports.add(portNumber)
            return true
        }
    }

    public int obtainPort(PortRange range) {
        return (range.min..range.max).find { int p -> obtainSpecificPort(p) } ?: -1
    }

    public void releasePort(int portNumber) {
        ports.remove((Object) portNumber);
    }
    
    public boolean isSshable() {
        String cmd = "date; exit";
        try {
            int result = run(cmd)
            if (result == 0) {
                return true;
            } else {
                LOG.info("Not reachable: $this, executing `$cmd`, exit code $result");
                return false;
            }
        } catch (IllegalStateException e) {
            LOG.info("Exception checking if $this is reachable; assuming not", e);
            return false;
        } catch (IOException e) {
            LOG.info("Exception checking if $this is reachable; assuming not", e);
            return false;
        }
    }
}
