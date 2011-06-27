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

    public SshMachineLocation() {
    }

    public SshMachineLocation(Map attributes) {
        name = attributes.name
        attributes.remove 'name'
        this.attributes = attributes
    }

	/**
	 * These attributes are separate to the entity hierarchy attributes,
	 * used by certain types of entities as documented in their setup
	 * (e.g. JMX port) 
	 */
	public Map getAttributes() { attributes }

}
