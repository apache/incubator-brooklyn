package brooklyn.location.basic

import java.io.File
import java.util.Map

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import brooklyn.location.Location
import brooklyn.util.internal.SshJschTool

public class SshMachineLocation implements Location {
	private static final long serialVersionUID = -6233729266488652570L;
    static final Logger log = LoggerFactory.getLogger(SshMachineLocation.class)
 
	String name
	String user = null
	InetAddress host
 
	Map attributes=[:]

    public SshMachineLocation(InetAddress host) {
        this.host = host
    }

    public SshMachineLocation(Map attributes, InetAddress host) {
        name = attributes.name
        attributes.remove 'name'
        user = attributes.user
        attributes.remove 'user'
        this.attributes = attributes
        this.host = host
    }

	/**
	 * These attributes are separate to the entity hierarchy attributes,
	 * used by certain types of entities as documented in their setup
	 * (e.g. JMX port) 
	 */
	public Map getAttributes() { attributes }

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
        def conn = new SshJschTool(user:user, host:host)
        conn.connect()
        int result = conn.copyToServer [:], src, destination
        conn.disconnect()
        result
    }

    @Override
    public String toString() {
        return name?name+"["+host+"]":host;
    }
}
