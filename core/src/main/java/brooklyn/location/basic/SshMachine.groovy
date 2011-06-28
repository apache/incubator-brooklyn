package brooklyn.location.basic

import brooklyn.util.internal.SshJschTool

/**
 * Operations on a machine that is accessible via ssh.
 */
public class SshMachine {
    String user = null
    InetAddress host
    Map attributes=[:]

    public SshMachine(Map attributes = [:], InetAddress host) {
        this.host = host
    }

    public SshMachine(Map attributes = [:], InetAddress host, String userName) {
        this.user = userName
        this.host = host
    }

    /** convenience for running a script, returning the result code */
    public int run(Map props=[:], String command) {
        assert host : "host must be specified for $this"

        if (!user) user=System.getProperty "user.name"
        def t = new SshJschTool(user:user, host:host.hostName);
        t.connect()
        int result = t.execShell props, command
        t.disconnect()
        result

//		ExecUtils.execBlocking "ssh", (user?user+"@":"")+host, command
    }

    public int copyTo(File src, String destination) {
        def conn = new SshJschTool(user:user, host:host.hostName)
        conn.connect()
        int result = conn.copyToServer [:], src, destination
        conn.disconnect()
        result
    }

    @Override
    public String toString() {
        return host;
    }

    /**
     * These attributes are separate to the entity hierarchy attributes,
     * used by certain types of entities as documented in their setup
     * (e.g. JMX port)
     */
    public Map getAttributes() { attributes }
}
