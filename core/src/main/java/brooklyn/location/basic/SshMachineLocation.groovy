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

	Map attributes=[:]

    SshMachine machine;

    public SshMachineLocation(InetAddress host) {
        machine = new SshMachine(host)
    }

    public SshMachineLocation(Map attributes, InetAddress host) {
        name = attributes.name
        attributes.remove 'name'
        String user = attributes.user
        attributes.remove 'user'
        this.attributes = attributes

        machine = new SshMachine(host, user)
    }

    public int run(Map props=[:], String command) {
        return machine.run(props, command)
    }

    public int copyTo(File src, String destination) {
        return machine.copyTo(src, destination)
    }

    @Override
    public String toString() {
        return machine.toString()
    }

	/**
	 * These attributes are separate to the entity hierarchy attributes,
	 * used by certain types of entities as documented in their setup
	 * (e.g. JMX port) 
	 */
	public Map getAttributes() { attributes }

}
